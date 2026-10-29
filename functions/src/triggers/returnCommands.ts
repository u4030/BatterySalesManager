import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const processReturnCommand = onDocumentCreated("return_commands/{commandId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;

  const db = admin.firestore();
  const cmd = snapshot.data();
  if (!cmd || cmd.status !== "PENDING") return;

  try {
    await db.runTransaction(async (transaction) => {
      const warehouseId = cmd.warehouseId || "";
      const variantId = cmd.productVariantId || "";
      const returnedQty = Number(cmd.returnedQuantity || 0);
      const refundAmount = Number(cmd.totalRefundAmount || 0);
      const scrapQty = Number(cmd.oldBatteriesQuantity || 0);
      const scrapAmps = Number(cmd.oldBatteriesTotalAmperes || 0);
      const scrapVal = Number(cmd.oldBatteriesValue || 0);

      const hubRef = db.doc("summaries/dashboard_hub");

      // Update Stock Entry document if specified
      if (cmd.entryId) {
        const entryRef = db.collection("stock_entries").doc(cmd.entryId);
        const entrySnap = await transaction.get(entryRef);
        if (entrySnap.exists) {
          transaction.update(entryRef, {
            returnedQuantity: FieldValue.increment(returnedQty),
            returnDate: FieldValue.serverTimestamp()
          });
        }
      }

      if (cmd.type === "SALES_RETURN") {
        // 1. Rebalance stock: Sales return increases warehouse stock
        if (variantId) {
          const stockRef = db.collection("stocks").doc(`${variantId}_${warehouseId}`);
          transaction.set(stockRef, { quantity: FieldValue.increment(returnedQty) }, { merge: true });
        }

        // 2. Adjust scrap warehouse if old batteries returned
        if (scrapQty > 0) {
          const scrapRef = db.collection("scrap_warehouses").doc(`scrap_wh_${warehouseId}`);
          transaction.set(scrapRef, {
            totalQuantity: FieldValue.increment(-scrapQty),
            totalAmperes: FieldValue.increment(-scrapAmps),
            totalValue: FieldValue.increment(-scrapVal),
            lastUpdated: FieldValue.serverTimestamp()
          }, { merge: true });
        }

        // 3. Invoice & Treasury adjustment
        let remainingDebtReduction = refundAmount;
        let cashRefund = 0;

        if (cmd.invoiceId) {
          const invoiceRef = db.collection("invoices").doc(cmd.invoiceId);
          const invoiceSnap = await transaction.get(invoiceRef);
          if (invoiceSnap.exists) {
            const invoiceData = invoiceSnap.data() || {};
            const currentDebt = Number(invoiceData.remainingAmount || 0);

            if (currentDebt > 0) {
              remainingDebtReduction = Math.min(refundAmount, currentDebt);
              cashRefund = refundAmount - remainingDebtReduction;
            } else {
              remainingDebtReduction = 0;
              cashRefund = refundAmount;
            }

            transaction.update(invoiceRef, {
              remainingAmount: FieldValue.increment(-remainingDebtReduction),
              status: (currentDebt - remainingDebtReduction <= 0.001) ? "paid" : "pending",
              updatedAt: FieldValue.serverTimestamp()
            });
          } else {
            cashRefund = refundAmount;
          }
        } else {
          cashRefund = refundAmount;
        }

        // Update Central Dashboard Hub
        const hubUpdates: Record<string, any> = {
          "supplierSummary.totalCustomerDebt": FieldValue.increment(-remainingDebtReduction),
          lastUpdated: FieldValue.serverTimestamp()
        };
        if (cashRefund > 0) {
          hubUpdates["financialStatus.totalCashBalance"] = FieldValue.increment(-cashRefund);
          hubUpdates[`financialStatus.subWarehouseBalances.${warehouseId}`] = FieldValue.increment(-cashRefund);
        }
        transaction.set(hubRef, hubUpdates, { merge: true });

      } else if (cmd.type === "PURCHASE_RETURN") {
        // 1. Rebalance stock: Purchase return decreases warehouse stock
        if (variantId) {
          const stockRef = db.collection("stocks").doc(`${variantId}_${warehouseId}`);
          transaction.set(stockRef, { quantity: FieldValue.increment(-returnedQty) }, { merge: true });
        }

        // 2. Adjust supplier debt in Dashboard Hub
        transaction.set(hubRef, {
          "supplierSummary.totalSupplierDebt": FieldValue.increment(-refundAmount),
          lastUpdated: FieldValue.serverTimestamp()
        }, { merge: true });

        // 3. Adjust supplier summary in suppliers_overview
        const overviewRef = db.doc("summaries/suppliers_overview");
        transaction.set(overviewRef, {
          totalSupplierDebt: FieldValue.increment(-refundAmount),
          lastUpdated: FieldValue.serverTimestamp()
        }, { merge: true });
      }

      transaction.update(snapshot.ref, { status: "SUCCESS", processedAt: FieldValue.serverTimestamp() });
    });
  } catch (error: any) {
    console.error(`Return command processing failed for ${event.params.commandId}:`, error);
    await snapshot.ref.update({ status: "FAILED", error: error.message });
  }
});
