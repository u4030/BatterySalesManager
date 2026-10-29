import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const processPaymentCommand = onDocumentCreated("payment_commands/{commandId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;

  const db = admin.firestore();
  const cmd = snapshot.data();
  if (!cmd || cmd.status !== "PENDING") return;

  try {
    await db.runTransaction(async (transaction) => {
      const invoiceRef = db.collection("invoices").doc(cmd.invoiceId);
      const invoiceSnap = await transaction.get(invoiceRef);
      if (!invoiceSnap.exists) throw new Error("Invoice not found");

      const invoiceData = invoiceSnap.data() || {};
      const amount = Number(cmd.amount || 0);
      const isBank = cmd.paymentMethod === "bank";
      const warehouseId = cmd.warehouseId || invoiceData.warehouseId;

      const hubRef = db.doc("summaries/dashboard_hub");
      const paymentRef = db.collection("payments").doc(cmd.paymentId || cmd.id || event.params.commandId);

      if (cmd.type === "CREATE") {
        // 1. إنشاء السداد الفعلي وعكس الأثر المالي
        transaction.set(paymentRef, {
          id: paymentRef.id,
          invoiceId: cmd.invoiceId,
          warehouseId,
          amount,
          paymentMethod: cmd.paymentMethod,
          notes: cmd.notes || "",
          timestamp: cmd.paymentDate || FieldValue.serverTimestamp()
        });
        transaction.update(invoiceRef, {
          paidAmount: FieldValue.increment(amount),
          remainingAmount: FieldValue.increment(-amount),
          updatedAt: FieldValue.serverTimestamp()
        });
        transaction.set(hubRef, {
          "financialStatus.totalCashBalance": FieldValue.increment(!isBank ? amount : 0),
          "financialStatus.globalBankBalance": FieldValue.increment(isBank ? amount : 0),
          [`financialStatus.subWarehouseBalances.${warehouseId}`]: FieldValue.increment(!isBank ? amount : 0),
          "supplierSummary.totalCustomerDebt": FieldValue.increment(-amount),
          lastUpdated: FieldValue.serverTimestamp()
        }, { merge: true });

      } else if (cmd.type === "UPDATE") {
        // 2. تعديل دفعة مالية سابقة
        const paymentSnap = await transaction.get(paymentRef);
        if (!paymentSnap.exists) throw new Error("Payment document not found for update");
        const oldPaymentData = paymentSnap.data() || {};
        const oldAmount = Number(oldPaymentData.amount || 0);
        const oldIsBank = oldPaymentData.paymentMethod === "bank";
        const diff = amount - oldAmount;

        transaction.update(paymentRef, {
          amount,
          paymentMethod: cmd.paymentMethod,
          notes: cmd.notes || oldPaymentData.notes || "",
          timestamp: cmd.paymentDate || oldPaymentData.timestamp || FieldValue.serverTimestamp()
        });

        transaction.update(invoiceRef, {
          paidAmount: FieldValue.increment(diff),
          remainingAmount: FieldValue.increment(-diff),
          updatedAt: FieldValue.serverTimestamp()
        });

        // Rebalance hub stats
        transaction.set(hubRef, {
          "financialStatus.totalCashBalance": FieldValue.increment((!isBank ? amount : 0) - (!oldIsBank ? oldAmount : 0)),
          "financialStatus.globalBankBalance": FieldValue.increment((isBank ? amount : 0) - (oldIsBank ? oldAmount : 0)),
          [`financialStatus.subWarehouseBalances.${warehouseId}`]: FieldValue.increment((!isBank ? amount : 0) - (!oldIsBank ? oldAmount : 0)),
          "supplierSummary.totalCustomerDebt": FieldValue.increment(-diff),
          lastUpdated: FieldValue.serverTimestamp()
        }, { merge: true });

      } else if (cmd.type === "DELETE") {
        // 3. إلغاء/حذف دفعة مالية
        const paymentSnap = await transaction.get(paymentRef);
        if (paymentSnap.exists) {
          const oldPaymentData = paymentSnap.data() || {};
          const oldAmount = Number(oldPaymentData.amount || 0);
          const oldIsBank = oldPaymentData.paymentMethod === "bank";

          transaction.delete(paymentRef);

          transaction.update(invoiceRef, {
            paidAmount: FieldValue.increment(-oldAmount),
            remainingAmount: FieldValue.increment(oldAmount),
            updatedAt: FieldValue.serverTimestamp()
          });

          transaction.set(hubRef, {
            "financialStatus.totalCashBalance": FieldValue.increment(!oldIsBank ? -oldAmount : 0),
            "financialStatus.globalBankBalance": FieldValue.increment(oldIsBank ? -oldAmount : 0),
            [`financialStatus.subWarehouseBalances.${warehouseId}`]: FieldValue.increment(!oldIsBank ? -oldAmount : 0),
            "supplierSummary.totalCustomerDebt": FieldValue.increment(oldAmount),
            lastUpdated: FieldValue.serverTimestamp()
          }, { merge: true });
        }
      }

      transaction.update(snapshot.ref, { status: "SUCCESS", processedAt: FieldValue.serverTimestamp() });
    });
  } catch (error: any) {
    console.error("Payment command processing failed:", error);
    await snapshot.ref.update({ status: "FAILED", error: error.message });
  }
});
