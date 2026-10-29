import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

async function handleSaleDoc(snapshot: admin.firestore.DocumentSnapshot, orderId: string) {
  if (!snapshot.exists) return;

  const db = admin.firestore();
  const orderData = snapshot.data();
  if (!orderData || orderData.status === "COMPLETED" || orderData.processed === true) return;

  const txRef = db.collection("processed_transactions").doc(orderId);

  try {
    await db.runTransaction(async (transaction) => {
      const txSnap = await transaction.get(txRef);
      if (txSnap.exists) return; // Idempotency Guardrail

      transaction.set(txRef, { processedAt: FieldValue.serverTimestamp() });

      const warehouseId = orderData.warehouseId || "";
      const items = orderData.items || [];
      const paidAmount = Number(orderData.paidAmount || 0);
      const remainingAmount = Number(orderData.remainingAmount || 0);
      const paymentMethod = orderData.paymentMethod || "cash";
      const scrapQty = Number(orderData.oldBatteriesQuantity || 0);
      const scrapAmps = Number(orderData.oldBatteriesTotalAmperes || 0);
      const scrapVal = Number(orderData.oldBatteriesValue || 0);

      // 1. Atomically deduct stock from stocks, product_variants, and summaries
      for (const item of items) {
        const variantId = item.productId || item.productVariantId || "";
        const qty = Number(item.quantity || 0);

        if (variantId && qty > 0) {
          // Update stocks collection
          const stockRef = db.collection("stocks").doc(`${variantId}_${warehouseId}`);
          transaction.set(stockRef, { quantity: FieldValue.increment(-qty) }, { merge: true });

          // Update product_variants document currentStock map
          const variantRef = db.collection("product_variants").doc(variantId);
          transaction.set(variantRef, {
            [`currentStock.${warehouseId}`]: FieldValue.increment(-qty),
            updatedAt: FieldValue.serverTimestamp()
          }, { merge: true });

          // Update Read-Model summaries/inventory_wh_${warehouseId}
          const whSummaryRef = db.collection("summaries").doc(`inventory_wh_${warehouseId}`);
          transaction.set(whSummaryRef, {
            [`items.${variantId}.currentStock`]: FieldValue.increment(-qty),
            version: FieldValue.increment(1),
            lastUpdated: FieldValue.serverTimestamp()
          }, { merge: true });

          // Update Read-Model summaries/inventory_global
          const globalSummaryRef = db.collection("summaries").doc("inventory_global");
          transaction.set(globalSummaryRef, {
            [`items.${variantId}.currentStock`]: FieldValue.increment(-qty),
            version: FieldValue.increment(1),
            lastUpdated: FieldValue.serverTimestamp()
          }, { merge: true });

          // Low-Stock Threshold check
          const variantSnap = await transaction.get(variantRef);
          if (variantSnap.exists) {
            const vData = variantSnap.data() || {};
            const stockMap = vData.currentStock || {};
            const currentStockInWh = (stockMap[warehouseId] || 0) - qty;
            const minQty = Number(vData.minQuantities?.[warehouseId] || vData.minQuantity || 0);
            const productName = vData.productName || item.productName || "منتج";
            const capacity = Number(vData.capacity || 0);
            const specification = vData.specification || "";

            if (minQty > 0 && currentStockInWh <= minQty) {
              const alertRef = db.collection("system_alerts").doc(`low_stock_${variantId}`);
              transaction.set(alertRef, {
                id: `low_stock_${variantId}`,
                type: "low_stock",
                title: `مخزون منخفض: ${productName}`,
                message: `المخزون المتوفر (${currentStockInWh}) أقل من الحد الأدنى (${minQty})`,
                relatedId: variantId,
                warehouseId,
                timestamp: FieldValue.serverTimestamp(),
                resolved: false,
                data: {
                  capacity,
                  specification,
                  currentStock: currentStockInWh,
                  threshold: minQty
                }
              }, { merge: true });
            }
          }
        }
      }

      // 2. Adjust Scrap Warehouse
      if (scrapQty > 0) {
        const scrapRef = db.collection("scrap_warehouses").doc(`scrap_wh_${warehouseId}`);
        transaction.set(scrapRef, {
          totalQuantity: FieldValue.increment(scrapQty),
          totalAmperes: FieldValue.increment(scrapAmps),
          totalValue: FieldValue.increment(scrapVal),
          lastUpdated: FieldValue.serverTimestamp()
        }, { merge: true });
      }

      // 3. Update Financial Read-Model in summaries/dashboard_hub
      const isBank = paymentMethod === "bank";
      const hubRef = db.doc("summaries/dashboard_hub");
      transaction.set(hubRef, {
        "financialStatus.totalCashBalance": FieldValue.increment(!isBank ? paidAmount : 0),
        "financialStatus.globalBankBalance": FieldValue.increment(isBank ? paidAmount : 0),
        [`financialStatus.subWarehouseBalances.${warehouseId}`]: FieldValue.increment(!isBank ? paidAmount : 0),
        "supplierSummary.totalCustomerDebt": FieldValue.increment(remainingAmount),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      transaction.update(snapshot.ref, { status: "COMPLETED", processed: true });
    });
  } catch (error: any) {
    console.error(`Sale transaction failed for ${orderId}:`, error);
    await snapshot.ref.update({ status: "FAILED", error: error.message });
  }
}

export const processSalesOrder = onDocumentCreated("sales_orders/{orderId}", async (event) => {
  if (event.data) {
    await handleSaleDoc(event.data, event.params.orderId);
  }
});

export const processInvoiceCreated = onDocumentCreated("invoices/{invoiceId}", async (event) => {
  if (event.data) {
    await handleSaleDoc(event.data, event.params.invoiceId);
  }
});
