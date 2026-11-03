import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

const processedEvents = new Set<string>();

export const onInvoiceCreated = onDocumentCreated(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    const eventId = event.id;

    // Idempotency Guardrail
    if (processedEvents.has(eventId)) {
      console.log(`[IdempotencyGuard] Event ${eventId} for invoice ${event.params.invoiceId} already processed. Skipping.`);
      return;
    }
    processedEvents.add(eventId);
    if (processedEvents.size > 1000) {
      const firstKey = processedEvents.values().next().value;
      if (firstKey) processedEvents.delete(firstKey);
    }

    console.time("onInvoiceCreated_executionTime");
    const db = admin.firestore();
    const invoice = event.data.data();
    if (!invoice) return;

    const warehouseId = invoice.warehouseId || "";
    const items = (invoice.items || []) as Array<{ productId: string; quantity: number; total: number }>;
    const totalAmount = Number(invoice.totalAmount || invoice.finalAmount || 0);
    const paidAmount = Number(invoice.paidAmount || 0);
    const initialDebt = Math.max(0, totalAmount - paidAmount);
    const paymentMethod = invoice.paymentMethod || "cash";
    const scrapAmps = Number(invoice.oldBatteriesTotalAmperes || 0);
    const scrapVal = Number(invoice.oldBatteriesValue || 0);

    const isBank = paymentMethod === "bank";
    const now = FieldValue.serverTimestamp();

    const batch = db.batch();

    // 1. Decrement variant stock on product_variants & summaries using FieldValue.increment
    items.forEach((item) => {
      const variantId = item.productId;
      const qty = Number(item.quantity || 0);
      if (variantId && qty > 0) {
        // Decrement variant currentStock map
        if (warehouseId) {
          batch.set(
            db.doc(`product_variants/${variantId}`),
            {
              currentStock: {
                [warehouseId]: FieldValue.increment(-qty),
              },
            },
            { merge: true }
          );

          // Decrement warehouse summary item
          batch.set(
            db.doc(`summaries/inventory_wh_${warehouseId}`),
            {
              [`items.${variantId}.currentStock`]: FieldValue.increment(-qty),
              lastUpdated: now,
              version: FieldValue.increment(1),
            },
            { merge: true }
          );
        }

        // Decrement global summary item
        batch.set(
          db.doc("summaries/inventory_global"),
          {
            [`items.${variantId}.currentStock`]: FieldValue.increment(-qty),
            lastUpdated: now,
            version: FieldValue.increment(1),
          },
          { merge: true }
        );
      }
    });

    // 2. Commit batch
    await batch.commit();

    // 3. Update Dashboard Hub delta & per-warehouse daily collection stats with atomic debt receivables
    const subWarehouseDeltas: Record<string, number> = {};
    if (warehouseId) {
      subWarehouseDeltas[warehouseId] = !isBank ? paidAmount : 0;
    }

    // Calculate localized date string in Asia/Amman (UTC+3) to ensure temporal consistency
    const todayDateStr = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Amman",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date()); // Outputs YYYY-MM-DD in Asia/Amman

    const whBalanceUpdate: Record<string, any> = {
      cashBalance: FieldValue.increment(!isBank ? paidAmount : 0),
      bankBalance: FieldValue.increment(isBank ? paidAmount : 0),
      todayCollection: FieldValue.increment(paidAmount),
      todayCollectionCount: FieldValue.increment(paidAmount > 0 ? 1 : 0),
      lastInvoiceDate: todayDateStr,
    };

    if (initialDebt > 0) {
      whBalanceUpdate.pendingCollection = FieldValue.increment(initialDebt);
      whBalanceUpdate.totalReceivables = FieldValue.increment(initialDebt);
    }

    if (warehouseId) {
      await db.doc(`summaries/financial_status`).set({
        warehouseBalances: {
          [warehouseId]: whBalanceUpdate,
        },
        todayCollection: FieldValue.increment(paidAmount),
        todayCollectionCount: FieldValue.increment(paidAmount > 0 ? 1 : 0),
        totalCustomerDebt: FieldValue.increment(initialDebt),
        totalReceivables: FieldValue.increment(initialDebt),
        lastUpdated: now,
        version: FieldValue.increment(1),
      }, { merge: true });

      // Compound document for isolated daily sales aggregation: summaries/daily_${warehouseId}_${todayDateStr}
      await db.doc(`summaries/daily_${warehouseId}_${todayDateStr}`).set({
        warehouseId: warehouseId,
        date: todayDateStr,
        totalSales: FieldValue.increment(totalAmount),
        totalInvoices: FieldValue.increment(1),
        totalPaid: FieldValue.increment(paidAmount),
        totalDebt: FieldValue.increment(initialDebt),
        lastUpdated: now,
      }, { merge: true });
    }

    await applyDashboardHubDelta({
      cashDelta: !isBank ? paidAmount : 0,
      bankDelta: isBank ? paidAmount : 0,
      scrapAmperesDelta: scrapAmps,
      scrapValueDelta: scrapVal,
      subWarehouseDeltas: Object.keys(subWarehouseDeltas).length > 0 ? subWarehouseDeltas : undefined,
      updatedDomains: ["inventory", "financial"],
    });

    console.timeEnd("onInvoiceCreated_executionTime");
  }
);

export const onInvoiceUpdated = onDocumentUpdated(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    const b = event.data.before.data() || {};
    const a = event.data.after.data() || {};

    if (
      b.paidAmount === a.paidAmount &&
      b.remainingAmount === a.remainingAmount &&
      b.status === a.status
    ) {
      console.log(`[IdempotencyGuard] Invoice ${event.params.invoiceId} update had no domain changes. Skipping.`);
      return;
    }

    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "financial"],
    });
  }
);

export const onInvoiceDeleted = onDocumentDeleted(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    const db = admin.firestore();
    const invoice = event.data.data() || {};
    const warehouseId = invoice.warehouseId || "";
    const items = (invoice.items || []) as Array<{ productId: string; quantity: number }>;
    const now = FieldValue.serverTimestamp();

    if (items.length > 0 && warehouseId) {
      await db.runTransaction(async (transaction) => {
        // Read phase: inspect safety stock threshold and current stock for each variant
        const variantRefs = items.map(item => db.doc(`product_variants/${item.productId}`));
        const alertRefs = items.map(item => db.doc(`system_alerts/low_stock_${item.productId}_${warehouseId}`));

        const variantSnaps = await Promise.all(variantRefs.map(ref => transaction.get(ref)));
        const alertSnaps = await Promise.all(alertRefs.map(ref => transaction.get(ref)));

        let alertDeletionsCount = 0;

        items.forEach((item, index) => {
          const variantSnap = variantSnaps[index];
          const alertSnap = alertSnaps[index];
          const qty = Number(item.quantity || 0);

          if (variantSnap.exists && qty > 0) {
            const variantData = variantSnap.data() || {};
            const currentStockMap = (variantData.currentStock || {}) as Record<string, number>;
            const currentWhStock = Number(currentStockMap[warehouseId] || 0);
            const restoredStock = currentWhStock + qty;

            const minQty = Number((variantData.minQuantities || {})[warehouseId] ?? variantData.minQuantity ?? 0);

            // If restored stock is strictly greater than safety threshold, delete alert atomically
            if (restoredStock > minQty && alertSnap.exists) {
              transaction.delete(alertRefs[index]);
              alertDeletionsCount++;
            }

            // Restore variant currentStock
            transaction.set(
              variantRefs[index],
              {
                currentStock: {
                  [warehouseId]: FieldValue.increment(qty),
                },
                updatedAt: now,
              },
              { merge: true }
            );

            // Restore warehouse summary currentStock
            transaction.set(
              db.doc(`summaries/inventory_wh_${warehouseId}`),
              {
                [`items.${item.productId}.currentStock`]: FieldValue.increment(qty),
                lastUpdated: now,
                version: FieldValue.increment(1),
              },
              { merge: true }
            );

            // Restore global summary currentStock
            transaction.set(
              db.doc("summaries/inventory_global"),
              {
                [`items.${item.productId}.currentStock`]: FieldValue.increment(qty),
                lastUpdated: now,
                version: FieldValue.increment(1),
              },
              { merge: true }
            );
          }
        });

        if (alertDeletionsCount > 0) {
          transaction.set(
            db.doc("summaries/dashboard_hub"),
            {
              "scrapAndAlertsSummary.activeLowStockAlertsCount": FieldValue.increment(-alertDeletionsCount),
              "syncRegistry.alertsVersion": FieldValue.increment(1),
              lastUpdated: now,
            },
            { merge: true }
          );
        }
      });
    }

    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "financial", "alerts"],
    });
  }
);
