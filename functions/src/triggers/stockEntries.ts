import { onDocumentWritten } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const onStockEntryWritten = onDocumentWritten("stock_entries/{entryId}", async (event) => {
  const change = event.data;
  if (!change || !change.after.exists) return;

  const db = admin.firestore();
  const entryData = change.after.data();
  // المعالجة تنطلق فوراً لأي حركة مخزن جديدة ولم يتم معالجتها من قبل السيرفر مسبقاً
  if (!entryData || entryData.processedByServer === true) return;

  const entryId = event.params.entryId;
  const variantId = entryData.productVariantId || "";
  const warehouseId = entryData.warehouseId || "";

  // حساب الكمية الصافية المدخلة
  const qty = Number(entryData.quantity || 0) - Number(entryData.returnedQuantity || 0);
  if (!variantId || !warehouseId || qty === 0) return;

  try {
    await db.runTransaction(async (transaction) => {
      // 1. تحديث مستند الصنف الأصلي product_variants
      const variantRef = db.collection("product_variants").doc(variantId);
      transaction.set(variantRef, {
        [`currentStock.${warehouseId}`]: FieldValue.increment(qty),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });

      // 2. تحديث ملخص المستودع الفرعي inventory_wh_${warehouseId} الذي ينصت له الهاتف
      const whSummaryRef = db.collection("summaries").doc(`inventory_wh_${warehouseId}`);
      transaction.set(whSummaryRef, {
        [`items.${variantId}.currentStock`]: FieldValue.increment(qty),
        version: FieldValue.increment(1),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      // 3. تحديث ملخص المخزن العالمي inventory_global
      const globalSummaryRef = db.collection("summaries").doc("inventory_global");
      transaction.set(globalSummaryRef, {
        [`items.${variantId}.currentStock`]: FieldValue.increment(qty),
        version: FieldValue.increment(1),
        lastUpdated: FieldValue.serverTimestamp()
      }, { merge: true });

      // 4. محرك حساب الحد الأدنى وإطلاق التنبيهات الحية فوراً (Threshold Engine)
      const variantSnap = await transaction.get(variantRef);
      if (variantSnap.exists) {
        const vData = variantSnap.data() || {};
        const stockMap = vData.currentStock || {};
        // احتساب الكمية الجديدة بعد الزيادة الحالية
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
            data: {
              capacity,
              specification,
              currentStock: currentStockInWh,
              threshold: minQty
            }
          }, { merge: true });
        } else {
          // إذا أصبح المخزن آمناً وفوق الحد الأدنى، امسح التنبيه فوراً
          transaction.delete(alertRef);
        }
      }

      // تعميد المستند لكي لا يعاد معالجته تكراراً
      transaction.update(change.after.ref, { processedByServer: true });
    });
    console.log(`[ServerEngine] StockEntry ${entryId} processed successfully. Read-Models updated.`);
  } catch (error: any) {
    console.error(`Stock entry processing failed for ${entryId}:`, error);
  }
});
