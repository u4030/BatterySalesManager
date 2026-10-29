import { onDocumentWritten } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const onStockEntryWritten = onDocumentWritten("stock_entries/{entryId}", async (event) => {
  const change = event.data;
  if (!change || !change.after.exists) return;

  const db = admin.firestore();
  const entryData = change.after.data();
  if (!entryData || entryData.status !== "approved" || entryData.processedByServer === true) return;

  const entryId = event.params.entryId;
  const variantId = entryData.productVariantId || "";
  const warehouseId = entryData.warehouseId || "";
  const qty = Number(entryData.quantity || 0) - Number(entryData.returnedQuantity || 0);

  if (!variantId || !warehouseId || qty === 0) return;

  try {
    await db.runTransaction(async (transaction) => {
      // 1. Update product_variants currentStock map
      const variantRef = db.collection("product_variants").doc(variantId);
      transaction.set(variantRef, {
        [`currentStock.${warehouseId}`]: FieldValue.increment(qty),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });

      // 2. Update Read-Model summaries/inventory_wh_${warehouseId}
      const whSummaryRef = db.collection("summaries").doc(`inventory_wh_${warehouseId}`);
      transaction.set(whSummaryRef, {
        [`items.${variantId}.currentStock`]: FieldValue.increment(qty),
        version: FieldValue.increment(1),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      // 3. Update Read-Model summaries/inventory_global
      const globalSummaryRef = db.collection("summaries").doc("inventory_global");
      transaction.set(globalSummaryRef, {
        [`items.${variantId}.currentStock`]: FieldValue.increment(qty),
        version: FieldValue.increment(1),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      // 4. Low-Stock Threshold check
      const variantSnap = await transaction.get(variantRef);
      if (variantSnap.exists) {
        const vData = variantSnap.data() || {};
        const stockMap = vData.currentStock || {};
        const currentStockInWh = (stockMap[warehouseId] || 0) + qty;
        const minQty = Number(vData.minQuantities?.[warehouseId] || vData.minQuantity || 0);

        const alertRef = db.collection("system_alerts").doc(`low_stock_${variantId}`);

        if (minQty > 0 && currentStockInWh <= minQty) {
          const productName = vData.productName || entryData.productName || "منتج";
          const capacity = Number(vData.capacity || entryData.capacity || 0);
          const specification = vData.specification || entryData.specification || "";

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
        } else {
          // If stock is now safe, delete/resolve the alert
          const alertSnap = await transaction.get(alertRef);
          if (alertSnap.exists) {
            transaction.delete(alertRef);
          }
        }
      }

      transaction.update(change.after.ref, { processedByServer: true });
    });
  } catch (error: any) {
    console.error(`Stock entry processing failed for ${entryId}:`, error);
  }
});
