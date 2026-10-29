import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const processSalesOrder = onDocumentCreated("sales_orders/{orderId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;

  const db = admin.firestore();
  const orderId = event.params.orderId;
  const orderData = snapshot.data();
  if (!orderData) return;

  const txRef = db.collection("processed_transactions").doc(orderId);

  try {
    await db.runTransaction(async (transaction) => {
      const txSnap = await transaction.get(txRef);
      if (txSnap.exists) return; // Idempotency Guardrail check

      transaction.set(txRef, { processedAt: FieldValue.serverTimestamp() });

      // Zero Pre-Fetch Reads atomic stock deduction
      if (Array.isArray(orderData.items)) {
        for (const item of orderData.items) {
          if (item.variantId && item.warehouseId && item.quantity) {
            const stockRef = db.collection("stocks").doc(`${item.variantId}_${item.warehouseId}`);
            transaction.set(stockRef, {
              quantity: FieldValue.increment(-Number(item.quantity))
            }, { merge: true });
          }
        }
      }

      // Read-Model update for Customer Sales Summary
      if (orderData.customerId) {
        const customerSummaryRef = db.collection("customer_summaries").doc(orderData.customerId);
        transaction.set(customerSummaryRef, {
          lastOrderDate: orderData.timestamp || FieldValue.serverTimestamp(),
          totalSalesToDate: FieldValue.increment(Number(orderData.totalAmount || 0)),
          recentInvoicesSummary: FieldValue.arrayUnion({
            orderId: orderId,
            total: Number(orderData.totalAmount || 0),
            status: "COMPLETED"
          })
        }, { merge: true });
      }

      transaction.update(snapshot.ref, { status: "COMPLETED" });
    });
  } catch (error: any) {
    console.error(`Transaction failed for order ${orderId}:`, error);
    await snapshot.ref.update({ status: "FAILED", error: error.message });
  }
});
