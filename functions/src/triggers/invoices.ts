import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";
import { runWithIdempotencyGuard } from "../utils/idempotency";

export const onInvoiceCreated = onDocumentCreated(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    const invoiceId = event.params.invoiceId;
    const eventId = event.id;

    console.time(`onInvoiceCreated_${invoiceId}`);

    await runWithIdempotencyGuard(eventId, `invoice_created_${invoiceId}`, async (transaction) => {
      const db = admin.firestore();
      const invoice = event.data?.data();
      if (!invoice) return;

      const warehouseId = invoice.warehouseId || "";
      const items = (invoice.items || []) as Array<{ productId: string; quantity: number; total: number }>;
      const totalAmount = Number(invoice.totalAmount || invoice.finalAmount || 0);
      const paidAmount = Number(invoice.paidAmount || 0);
      const initialDebt = Math.max(0, totalAmount - paidAmount);
      const paymentMethod = invoice.paymentMethod || "cash";
      const isBank = paymentMethod === "bank";
      const now = FieldValue.serverTimestamp();

      // Read Phase: Fetch warehouse details synchronously to retrieve Arabic warehouse name
      let warehouseName = "المستودع";
      if (warehouseId) {
        const whSnap = await transaction.get(db.doc(`warehouses/${warehouseId}`));
        if (whSnap.exists) {
          warehouseName = whSnap.data()?.name || warehouseName;
        }
      }

      // Write Phase:
      // 1. Decrement variant stock on product_variants & summaries using atomic FieldValue.increment
      items.forEach((item) => {
        const variantId = item.productId;
        const qty = Number(item.quantity || 0);
        if (variantId && qty > 0) {
          if (warehouseId) {
            transaction.set(
              db.doc(`product_variants/${variantId}`),
              {
                currentStock: {
                  [warehouseId]: FieldValue.increment(-qty),
                },
              },
              { merge: true }
            );

            transaction.set(
              db.doc(`summaries/inventory_wh_${warehouseId}`),
              {
                [`items.${variantId}.currentStock`]: FieldValue.increment(-qty),
                lastUpdated: now,
                version: FieldValue.increment(1),
              },
              { merge: true }
            );
          }

          transaction.set(
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

      // Calculate localized date string in Asia/Amman (UTC+3) to ensure temporal consistency
      const todayDateStr = new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Amman",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(new Date());

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
        transaction.set(
          db.doc("summaries/financial_status"),
          {
            warehouseBalances: {
              [warehouseId]: whBalanceUpdate,
            },
            todayCollection: FieldValue.increment(paidAmount),
            todayCollectionCount: FieldValue.increment(paidAmount > 0 ? 1 : 0),
            totalCustomerDebt: FieldValue.increment(initialDebt),
            totalReceivables: FieldValue.increment(initialDebt),
            lastUpdated: now,
            version: FieldValue.increment(1),
          },
          { merge: true }
        );

        transaction.set(
          db.doc("summaries/dashboard_hub"),
          {
            version: FieldValue.increment(1),
            lastUpdated: now,
          },
          { merge: true }
        );

        // Compound document for isolated daily sales aggregation: summaries/daily_${warehouseId}_${todayDateStr}
        // Write both warehouseId, warehouse_id, warehouseName, and warehouse_name to satisfy all client deserializers
        transaction.set(
          db.doc(`summaries/daily_${warehouseId}_${todayDateStr}`),
          {
            warehouseId: warehouseId,
            warehouse_id: warehouseId,
            warehouseName: warehouseName,
            warehouse_name: warehouseName,
            date: todayDateStr,
            totalSales: FieldValue.increment(totalAmount),
            totalInvoices: FieldValue.increment(1),
            totalPaid: FieldValue.increment(paidAmount),
            totalDebt: FieldValue.increment(initialDebt),
            unsettledCashBalance: FieldValue.increment(!isBank ? paidAmount : 0),
            isSettled: false,
            lastUpdated: now,
          },
          { merge: true }
        );
      }
    });

    const invoice = event.data?.data();
    if (invoice) {
      const warehouseId = invoice.warehouseId || "";
      const paidAmount = Number(invoice.paidAmount || 0);
      const isBank = (invoice.paymentMethod || "cash") === "bank";
      const scrapAmps = Number(invoice.oldBatteriesTotalAmperes || 0);
      const scrapVal = Number(invoice.oldBatteriesValue || 0);

      const subWarehouseDeltas: Record<string, number> = {};
      if (warehouseId) {
        subWarehouseDeltas[warehouseId] = !isBank ? paidAmount : 0;
      }

      await applyDashboardHubDelta({
        cashDelta: !isBank ? paidAmount : 0,
        bankDelta: isBank ? paidAmount : 0,
        scrapAmperesDelta: scrapAmps,
        scrapValueDelta: scrapVal,
        subWarehouseDeltas: Object.keys(subWarehouseDeltas).length > 0 ? subWarehouseDeltas : undefined,
        updatedDomains: ["inventory", "financial"],
      });
    }

    console.timeEnd(`onInvoiceCreated_${invoiceId}`);
  }
);

export const onInvoiceUpdated = onDocumentUpdated(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    const invoiceId = event.params.invoiceId;
    const eventId = event.id;
    const b = event.data.before.data() || {};
    const a = event.data.after.data() || {};

    if (
      b.paidAmount === a.paidAmount &&
      b.remainingAmount === a.remainingAmount &&
      b.status === a.status
    ) {
      return;
    }

    await runWithIdempotencyGuard(eventId, `invoice_updated_${invoiceId}`, async () => {
      await applyDashboardHubDelta({
        updatedDomains: ["inventory", "financial"],
      });
    });
  }
);

export const onInvoiceDeleted = onDocumentDeleted(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    const invoiceId = event.params.invoiceId;
    const eventId = event.id;
    const db = admin.firestore();
    const invoice = event.data.data() || {};
    const warehouseId = invoice.warehouseId || "";
    const items = (invoice.items || []) as Array<{ productId: string; quantity: number }>;
    const now = FieldValue.serverTimestamp();

    await runWithIdempotencyGuard(eventId, `invoice_deleted_${invoiceId}`, async (transaction) => {
      if (items.length > 0 && warehouseId) {
        // Read Phase: inspect safety stock threshold and current stock for each variant
        const variantRefs = items.map((item) => db.doc(`product_variants/${item.productId}`));
        const alertRefs = items.map((item) => db.doc(`system_alerts/low_stock_${item.productId}_${warehouseId}`));

        const variantSnaps = await Promise.all(variantRefs.map((ref) => transaction.get(ref)));
        const alertSnaps = await Promise.all(alertRefs.map((ref) => transaction.get(ref)));

        let alertDeletionsCount = 0;

        // Write Phase:
        // Perform atomic evaluation of the threshold and delete alertRef strictly inside the same transaction block
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

            // Immediate threshold evaluation: if restored stock is strictly greater than safety threshold,
            // delete the low-stock alert atomically inside the same transaction block to eliminate timing gaps
            if (restoredStock > minQty && alertSnap.exists) {
              transaction.delete(alertRefs[index]);
              alertDeletionsCount++;
            }

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

            transaction.set(
              db.doc(`summaries/inventory_wh_${warehouseId}`),
              {
                [`items.${item.productId}.currentStock`]: FieldValue.increment(qty),
                lastUpdated: now,
                version: FieldValue.increment(1),
              },
              { merge: true }
            );

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
              version: FieldValue.increment(1),
              lastUpdated: now,
            },
            { merge: true }
          );
        }

        transaction.set(
          db.doc("summaries/financial_status"),
          {
            version: FieldValue.increment(1),
            lastUpdated: now,
          },
          { merge: true }
        );
      }
    });

    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "financial", "alerts"],
    });
  }
);
