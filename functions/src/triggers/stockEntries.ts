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
    console.time("onStockEntryCreated_executionTime");

    const db = admin.firestore();
    const entry = event.data.data();
    if (!entry) return;

    const variantId = entry.productVariantId;
    const warehouseId = entry.warehouseId;
    if (!variantId || !warehouseId) return;

    const qty = Number(entry.quantity || 0);
    const returnedQty = Number(entry.returnedQuantity || 0);
    const netQty = qty - returnedQty;
    const now = FieldValue.serverTimestamp();

    const batch = db.batch();

    // 1. Update Variant currentStock map
    batch.set(
      db.doc(`product_variants/${variantId}`),
      {
        currentStock: {
          [warehouseId]: FieldValue.increment(netQty),
        },
      },
      { merge: true }
    );

    try {
      const [whSummarySnap, globalSummarySnap, variantSnap] = await Promise.all([
        db.doc(`summaries/inventory_wh_${warehouseId}`).get(),
        db.doc("summaries/inventory_global").get(),
        db.doc(`product_variants/${variantId}`).get(),
      ]);

      const variant = variantSnap.exists ? (variantSnap.data() || {}) : {};

      // 2. Update Warehouse Inventory Summary (summaries/inventory_wh_[id])
      const whItems = whSummarySnap.exists ? (whSummarySnap.data()?.items || {}) : {};
      const oldWhItem = whItems[variantId] || {};
      const newWhStock = Math.max(0, Number(oldWhItem.currentStock || 0) + netQty);
      const itemCost = Number(entry.costPrice || variant.weightedAverageCost || oldWhItem.weightedAverageCost || 0);

      whItems[variantId] = {
        variantId: variantId,
        productId: variant.productId || oldWhItem.productId || "",
        productName: entry.productName || variant.productName || oldWhItem.productName || "منتج",
        capacity: Number(entry.capacity || variant.capacity || oldWhItem.capacity || 0),
        specification: entry.specification || variant.specification || oldWhItem.specification || "",
        barcode: variant.barcode || oldWhItem.barcode || "",
        currentStock: newWhStock,
        minQuantity: (variant.minQuantities || {})[warehouseId] ?? variant.minQuantity ?? oldWhItem.minQuantity ?? 0,
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

      batch.set(
        db.doc(`summaries/inventory_wh_${warehouseId}`),
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

      // 3. Update Global Inventory Summary (summaries/inventory_global)
      const globalItems = globalSummarySnap.exists ? (globalSummarySnap.data()?.items || {}) : {};
      const oldGlobalItem = globalItems[variantId] || {};
      const newGlobalStock = Math.max(0, Number(oldGlobalItem.currentStock || 0) + netQty);

      globalItems[variantId] = {
        variantId: variantId,
        productId: variant.productId || oldGlobalItem.productId || "",
        productName: entry.productName || variant.productName || oldGlobalItem.productName || "منتج",
        capacity: Number(entry.capacity || variant.capacity || oldGlobalItem.capacity || 0),
        specification: entry.specification || variant.specification || oldGlobalItem.specification || "",
        barcode: variant.barcode || oldGlobalItem.barcode || "",
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

      batch.set(
        db.doc("summaries/inventory_global"),
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

      // 4. Update Sync Registry & Dashboard Hub
      batch.set(
        db.doc("summaries/sync_registry"),
        {
          inventoryVersion: FieldValue.increment(1),
          suppliersVersion: FieldValue.increment(1),
          financialVersion: FieldValue.increment(1),
          lastModified: now,
        },
        { merge: true }
      );

      batch.set(
        db.doc("summaries/dashboard_hub"),
        {
          "inventorySummary.totalValue": Math.max(0, globalTotalValue),
          "inventorySummary.totalItemsCount": Math.max(0, globalTotalItemsCount),
          "syncRegistry.inventoryVersion": FieldValue.increment(1),
          "syncRegistry.suppliersVersion": FieldValue.increment(1),
          "syncRegistry.financialVersion": FieldValue.increment(1),
          "syncRegistry.lastModified": now,
          lastUpdated: now,
        },
        { merge: true }
      );

      await batch.commit();
    } catch (error) {
      console.error("Error in onStockEntryCreated batch execution:", error);
    }

    console.timeEnd("onStockEntryCreated_executionTime");
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
