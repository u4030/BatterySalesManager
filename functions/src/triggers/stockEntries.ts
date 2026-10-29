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
      const variantRef = db.collection("product_variants").doc(variantId);
      const whSummaryRef = db.collection("summaries").doc(`inventory_wh_${warehouseId}`);
      const globalSummaryRef = db.collection("summaries").doc("inventory_global");

      // === 1. READ PHASE FIRST (امتثالاً لقانون المعاملات الصارم لمنع الـ Crash) ===
      const variantSnap = await transaction.get(variantRef);

      // === 2. WRITE PHASE SECOND (تنفيذ المحاسبة وتوزيع الحسابات) ===
      // تحديث رصيد الصنف الموحد في المخازن
      transaction.set(variantRef, {
        [`currentStock.${warehouseId}`]: FieldValue.increment(qty),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });

      // تحديث ملخص المستودع الفرعي الذي ينصت له الهاتف كـ Flow حركي
      transaction.set(whSummaryRef, {
        [`items.${variantId}.currentStock`]: FieldValue.increment(qty),
        version: FieldValue.increment(1),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      // تحديث ملخص المخزن العالمي
      transaction.set(globalSummaryRef, {
        [`items.${variantId}.currentStock`]: FieldValue.increment(qty),
        version: FieldValue.increment(1),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      // محرك حساب الحد الأدنى للمخزون وإطلاق التنبيهات بناءً على القراءة المستقرة
      if (variantSnap.exists) {
        const vData = variantSnap.data() || {};
        const stockMap = vData.currentStock || {};
        const currentStockInWh = (stockMap[warehouseId] || 0) + qty;
        const minQty = Number(vData.minQuantities?.[warehouseId] || vData.minQuantity || 0);
        const alertRef = db.collection("system_alerts").doc(`low_stock_${variantId}`);

        if (minQty > 0 && currentStockInWh <= minQty) {
          const productName = vData.productName || entryData.productName || "Battery";
          const capacity = Number(vData.capacity || entryData.capacity || 0);
          const specification = vData.specification || entryData.specification || "";

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
          // إذا زاد المخزن عن الحد الأدنى، احذف مستند التنبيه فوراً لتنظيف لوحة التحكم
          transaction.delete(alertRef);
        }
      }

      // تعميد نجاح المعاملة لمنع التكرار ارتدادياً
      transaction.update(change.after.ref, { processedByServer: true });
    });
    console.log(`[ServerEngine] Fixed transaction ordering. StockEntry ${entryId} completed successfully.`);
  } catch (error: any) {
    console.error(`Stock entry processing failed for ${entryId}:`, error);
  }
});
