import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const onStockEntryCreated = onDocumentCreated(
  "stock_entries/{entryId}",
  async (event) => {
    if (!event.data) return;
    const entryId = event.params.entryId;
    console.time(`onStockEntryCreated_${entryId}`);

    const db = admin.firestore();
    const entry = event.data.data();
    if (!entry) return;

    const variantId = String(entry.productVariantId || entry.variantId || "");
    const warehouseId = String(entry.warehouseId || "");
    if (!variantId || !warehouseId) return;

    const qty = Number(entry.quantity || 0);
    const returnedQty = Number(entry.returnedQuantity || 0);
    const netQty = qty - returnedQty;
    const costPrice = Number(entry.costPrice || 0);
    const now = FieldValue.serverTimestamp();

    const processedRef = db.doc(`processed_transactions/${entryId}`);
    const variantRef = db.doc(`product_variants/${variantId}`);
    const whSummaryRef = db.doc(`summaries/inventory_wh_${warehouseId}`);
    const globalSummaryRef = db.doc("summaries/inventory_global");
    const warehouseRef = db.doc(`warehouses/${warehouseId}`);
    const alertId = `low_stock_${variantId}_${warehouseId}`;
    const alertRef = db.collection("system_alerts").doc(alertId);
    const syncRegistryRef = db.doc("summaries/sync_registry");
    const dashboardHubRef = db.doc("summaries/dashboard_hub");

    try {
      await db.runTransaction(async (transaction) => {
        // --- READ PHASE ---
        const processedSnap = await transaction.get(processedRef);
        if (processedSnap.exists) {
          console.log(`[IdempotencyGuard] StockEntry ${entryId} already processed. Skipping execution.`);
          return;
        }

        const [variantSnap, whSummarySnap, globalSummarySnap, warehouseSnap, alertSnap] = await Promise.all([
          transaction.get(variantRef),
          transaction.get(whSummaryRef),
          transaction.get(globalSummaryRef),
          transaction.get(warehouseRef),
          transaction.get(alertRef),
        ]);

        const variant = variantSnap.exists ? (variantSnap.data() || {}) : {};
        const oldWac = Number(variant.weightedAverageCost || 0);
        const currentStockMap = (variant.currentStock || {}) as Record<string, number>;
        const oldTotalStock = Object.values(currentStockMap).reduce((acc: number, val: any) => acc + Number(val || 0), 0);

        // Weighted Average Cost calculation:
        let newWac = oldWac;
        if (costPrice > 0 && netQty > 0) {
          const totalExistingValue = Math.max(0, oldTotalStock) * oldWac;
          const incomingValue = netQty * costPrice;
          const totalNewStock = Math.max(0, oldTotalStock) + netQty;
          newWac = totalNewStock > 0 ? (totalExistingValue + incomingValue) / totalNewStock : costPrice;
        } else if (newWac <= 0 && costPrice > 0) {
          newWac = costPrice;
        }

        // Warehouse Inventory Summary (summaries/inventory_wh_[id])
        const whItems = whSummarySnap.exists ? (whSummarySnap.data()?.items || {}) : {};
        const oldWhItem = whItems[variantId] || {};
        const newWhStock = Math.max(0, Number(oldWhItem.currentStock || 0) + netQty);
        const itemCost = newWac > 0 ? newWac : Number(entry.costPrice || oldWhItem.weightedAverageCost || 0);
        const minQty = Number((variant.minQuantities || {})[warehouseId] ?? variant.minQuantity ?? oldWhItem.minQuantity ?? 0);
        const pName = String(entry.productName || variant.productName || oldWhItem.productName || "منتج");
        const capacity = Number(entry.capacity || variant.capacity || oldWhItem.capacity || 0);
        const spec = String(entry.specification || variant.specification || oldWhItem.specification || "");
        const barcode = String(variant.barcode || oldWhItem.barcode || "");

        whItems[variantId] = {
          variantId: variantId,
          productId: String(variant.productId || oldWhItem.productId || ""),
          productName: pName,
          capacity: capacity,
          specification: spec,
          barcode: barcode,
          currentStock: newWhStock,
          minQuantity: minQty,
          weightedAverageCost: itemCost,
          sellingPrice: Number(variant.sellingPrice || oldWhItem.sellingPrice || 0),
          updatedAt: now,
          isDiscontinued: Boolean(variant.isDiscontinued ?? oldWhItem.isDiscontinued),
        };

        const whTotalValue = Object.values(whItems).reduce(
          (acc: number, item: any) => acc + (Number(item?.currentStock || 0) * Number(item?.weightedAverageCost || 0)),
          0
        );
        const whTotalItemsCount = Object.values(whItems).reduce(
          (acc: number, item: any) => acc + Number(item?.currentStock || 0),
          0
        );

        // Global Inventory Summary (summaries/inventory_global)
        const globalItems = globalSummarySnap.exists ? (globalSummarySnap.data()?.items || {}) : {};
        const oldGlobalItem = globalItems[variantId] || {};
        const newGlobalStock = Math.max(0, Number(oldGlobalItem.currentStock || 0) + netQty);

        globalItems[variantId] = {
          variantId: variantId,
          productId: String(variant.productId || oldGlobalItem.productId || ""),
          productName: pName,
          capacity: capacity,
          specification: spec,
          barcode: barcode,
          currentStock: newGlobalStock,
          minQuantity: Number(variant.minQuantity || oldGlobalItem.minQuantity || 0),
          weightedAverageCost: itemCost,
          sellingPrice: Number(variant.sellingPrice || oldGlobalItem.sellingPrice || 0),
          updatedAt: now,
          isDiscontinued: Boolean(variant.isDiscontinued ?? oldGlobalItem.isDiscontinued),
        };

        const globalTotalValue = Object.values(globalItems).reduce(
          (acc: number, item: any) => acc + (Number(item?.currentStock || 0) * Number(item?.weightedAverageCost || 0)),
          0
        );
        const globalTotalItemsCount = Object.values(globalItems).reduce(
          (acc: number, item: any) => acc + Number(item?.currentStock || 0),
          0
        );

        // Anti-Flapping Low Stock Alert Handling (Pure plain JS Object, NO Buffer data)
        const whName = String(warehouseSnap.exists ? (warehouseSnap.data()?.name || warehouseId) : warehouseId);
        const isLowStock = minQty > 0 && newWhStock <= minQty;
        let alertDelta = 0;

        if (isLowStock) {
          // If alert exists and already active, do nothing to prevent flapping
          if (!alertSnap.exists || alertSnap.data()?.resolved === true) {
            const alertPayload = {
              id: alertId,
              type: "low_stock",
              title: "مستوى المخزون منخفض",
              message: `الصنف ${pName} شارف على النفاد في المستودع`,
              warehouseId: warehouseId,
              warehouseName: whName,
              relatedId: variantId,
              resolved: false,
              timestamp: now,
            };
            transaction.set(alertRef, alertPayload);
            alertDelta = 1;
          }
        } else if (alertSnap.exists) {
          // Stock has recovered safely above threshold -> delete alert
          transaction.delete(alertRef);
          if (alertSnap.data()?.resolved !== true) {
            alertDelta = -1;
          }
        }

        // --- WRITE PHASE ---
        // 1. Mark as processed (Idempotency Guard)
        transaction.set(processedRef, {
          entryId: entryId,
          variantId: variantId,
          warehouseId: warehouseId,
          processedAt: now,
        });

        // 2. Update product_variants/${variantId}
        transaction.set(
          variantRef,
          {
            currentStock: {
              [warehouseId]: FieldValue.increment(netQty),
            },
            weightedAverageCost: newWac,
            updatedAt: now,
          },
          { merge: true }
        );

        // 3. Update summaries/inventory_wh_${warehouseId}
        transaction.set(
          whSummaryRef,
          {
            id: `inventory_wh_${warehouseId}`,
            warehouseId: warehouseId,
            items: whItems,
            totalValue: Math.max(0, whTotalValue),
            totalItemsCount: Math.max(0, whTotalItemsCount),
            lastUpdated: now,
            version: FieldValue.increment(1),
          },
          { merge: true }
        );

        // 4. Update warehouses/${warehouseId} stocks and updatedAt
        transaction.set(
          warehouseRef,
          {
            stocks: {
              [variantId]: FieldValue.increment(netQty),
            },
            updatedAt: now,
          },
          { merge: true }
        );

        // 5. Update summaries/inventory_global
        transaction.set(
          globalSummaryRef,
          {
            id: "inventory_global",
            warehouseId: null,
            items: globalItems,
            totalValue: Math.max(0, globalTotalValue),
            totalItemsCount: Math.max(0, globalTotalItemsCount),
            lastUpdated: now,
            version: FieldValue.increment(1),
          },
          { merge: true }
        );

        // 6. Update Sync Registry & Dashboard Hub
        transaction.set(
          syncRegistryRef,
          {
            inventoryVersion: FieldValue.increment(1),
            suppliersVersion: FieldValue.increment(1),
            financialVersion: FieldValue.increment(1),
            alertsVersion: alertDelta !== 0 ? FieldValue.increment(1) : FieldValue.increment(0),
            lastModified: now,
          },
          { merge: true }
        );

        const hubUpdates: Record<string, any> = {
          "inventorySummary.totalValue": Math.max(0, globalTotalValue),
          "inventorySummary.totalItemsCount": Math.max(0, globalTotalItemsCount),
          "syncRegistry.inventoryVersion": FieldValue.increment(1),
          "syncRegistry.suppliersVersion": FieldValue.increment(1),
          "syncRegistry.financialVersion": FieldValue.increment(1),
          "syncRegistry.lastModified": now,
          lastUpdated: now,
        };

        if (alertDelta !== 0) {
          hubUpdates["scrapAndAlertsSummary.activeLowStockAlertsCount"] = FieldValue.increment(alertDelta);
          hubUpdates["syncRegistry.alertsVersion"] = FieldValue.increment(1);
        }

        transaction.set(dashboardHubRef, hubUpdates, { merge: true });
      });
    } catch (error) {
      console.error(`Error in onStockEntryCreated transaction execution for ${entryId}:`, error);
    }

    console.timeEnd(`onStockEntryCreated_${entryId}`);
  }
);

export const onStockEntryUpdated = onDocumentUpdated(
  "stock_entries/{entryId}",
  async (event) => {
    if (!event.data) return;
    const b = event.data.before.data() || {};
    const a = event.data.after.data() || {};

    if (
      b.quantity === a.quantity &&
      b.returnedQuantity === a.returnedQuantity &&
      b.costPrice === a.costPrice &&
      b.totalCost === a.totalCost &&
      b.status === a.status &&
      b.remainingBalance === a.remainingBalance
    ) {
      console.log(`[IdempotencyGuard] StockEntry ${event.params.entryId} update had no domain changes. Skipping execution.`);
      return;
    }

    console.time("onStockEntryUpdated_executionTime");
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("onStockEntryUpdated_executionTime");
  }
);

export const onStockEntryDeleted = onDocumentDeleted(
  "stock_entries/{entryId}",
  async (event) => {
    if (!event.data) return;
    console.time("onStockEntryDeleted_executionTime");
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("onStockEntryDeleted_executionTime");
  }
);
