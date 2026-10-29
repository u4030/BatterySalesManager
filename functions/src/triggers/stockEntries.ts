import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const onStockEntryCreated = onDocumentCreated("stock_entries/{entryId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot || !snapshot.exists) return;

  const db = admin.firestore();
  const entryData = snapshot.data();
  const entryId = event.params.entryId;

  const rawVariantId = entryData?.variantId || "";
  const rawProductVariantId = entryData?.productVariantId || "";
  const variantId = (rawVariantId.trim() !== "") ? rawVariantId : rawProductVariantId;

  const warehouseId = entryData?.warehouseId || "";
  const qty = Number(entryData?.quantity || 0) - Number(entryData?.returnedQuantity || 0);

  if (!variantId || !warehouseId || qty === 0) {
    console.warn(`[ServerEngine] Skipping processing for entry ${entryId}: Missing variantId (${variantId}) or warehouseId (${warehouseId})`);
    return;
  }

  const txRef = db.collection("processed_stock_transactions").doc(entryId);

  try {
    await db.runTransaction(async (transaction) => {
      const txSnap = await transaction.get(txRef);
      if (txSnap.exists) return;

      const variantRef = db.collection("product_variants").doc(variantId);
      const whSummaryRef = db.collection("summaries").doc(`inventory_wh_${warehouseId}`);
      const globalSummaryRef = db.collection("summaries").doc("inventory_global");

      // === 1. READ PHASE FIRST ===
      const variantSnap = await transaction.get(variantRef);

      // === 2. WRITE PHASE SECOND ===
      transaction.set(txRef, { processedAt: FieldValue.serverTimestamp() });

      if (!rawVariantId || !rawProductVariantId) {
        transaction.update(snapshot.ref, {
          variantId: variantId,
          productVariantId: variantId
        });
      }

      transaction.set(variantRef, {
        [`currentStock.${warehouseId}`]: FieldValue.increment(qty),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });

      transaction.set(whSummaryRef, {
        [`items.${variantId}.currentStock`]: FieldValue.increment(qty),
        version: FieldValue.increment(1),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      transaction.set(globalSummaryRef, {
        [`items.${variantId}.currentStock`]: FieldValue.increment(qty),
        version: FieldValue.increment(1),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      if (variantSnap.exists) {
        const vData = variantSnap.data() || {};
        const stockMap = vData.currentStock || {};
        const currentStockInWh = (stockMap[warehouseId] || 0) + qty;
        const minQty = Number(vData.minQuantities?.[warehouseId] || vData.minQuantity || 0);
        const alertRef = db.collection("system_alerts").doc(`low_stock_${variantId}`);

        if (minQty > 0 && currentStockInWh <= minQty) {
          const productName = vData.productName || entryData?.productName || "Battery";
          const capacity = Number(vData.capacity || entryData?.capacity || 0);
          const specification = vData.specification || entryData?.specification || "";

          transaction.set(alertRef, {
            id: `low_stock_${variantId}`,
            type: "low_stock",
            title: `تنبيه: ${productName}`,
            message: `المخزون شارف على النفاد (${currentStockInWh}) تحت الحد الأدنى (${minQty})`,
            relatedId: variantId,
            warehouseId,
            timestamp: FieldValue.serverTimestamp(),
            resolved: false,
            data: { capacity, specification, currentStock: currentStockInWh, threshold: minQty }
          }, { merge: true });
        } else {
          transaction.delete(alertRef);
        }
      }
    });
    console.log(`[ServerEngine] Pure CQRS Flow Completed. StockEntry ${entryId} reflected and normalized.`);
  } catch (error: any) {
    console.error(`Stock entry processing failed for ${entryId}:`, error);
  }
});
