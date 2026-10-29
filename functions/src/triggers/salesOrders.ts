import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

async function handleSaleDoc(snapshot: admin.firestore.DocumentSnapshot, orderId: String) {
  if (!snapshot.exists) return;

  const db = admin.firestore();
  const orderData = snapshot.data();
  if (!orderData || orderData.status === "COMPLETED" || orderData.processed === true) return;

  const txRef = db.collection("processed_transactions").doc(orderId.toString());

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

      // 1. خصم المخزن ذرّياً (Zero Pre-Fetch)
      for (const item of items) {
        const prodId = item.productId || item.productVariantId || "";
        if (prodId) {
          const stockRef = db.collection("stocks").doc(`${prodId}_${warehouseId}`);
          transaction.set(stockRef, { quantity: FieldValue.increment(-Number(item.quantity)) }, { merge: true });
        }
      }

      // 2. معالجة مستودع السكراب والخردة تلقائياً
      if (scrapQty > 0) {
        const scrapRef = db.collection("scrap_warehouses").doc(`scrap_wh_${warehouseId}`);
        transaction.set(scrapRef, {
          totalQuantity: FieldValue.increment(scrapQty),
          totalAmperes: FieldValue.increment(scrapAmps),
          totalValue: FieldValue.increment(scrapVal),
          lastUpdated: FieldValue.serverTimestamp()
        }, { merge: true });
      }

      // 3. تحديث ملخص الذمم والوضع المالي المركزي في الـ Dashboard Hub
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
