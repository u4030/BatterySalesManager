import {
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const onVariantWritten = onDocumentWritten(
  "product_variants/{variantId}",
  async (event) => {
    if (!event.data) return;
    console.time("onVariantWritten_totalTime");
    const db = admin.firestore();
    const variantId = event.params.variantId;
    const afterSnap = event.data.after;

    const batch = db.batch();
    const now = FieldValue.serverTimestamp();

    if (!afterSnap.exists) {
      // Document deleted - update inventory version in sync_registry and dashboard_hub in 1 write batch
      batch.set(db.doc("summaries/sync_registry"), {
        inventoryVersion: FieldValue.increment(1),
        lastModified: now,
      }, { merge: true });

      batch.set(db.doc("summaries/dashboard_hub"), {
        "syncRegistry.inventoryVersion": FieldValue.increment(1),
        "syncRegistry.lastModified": now,
        lastUpdated: now,
      }, { merge: true });

      await batch.commit();
      console.timeEnd("onVariantWritten_totalTime");
      return;
    }

    const variant = afterSnap.data();

    if (!variant || variant.archived || variant.isDiscontinued) {
      batch.set(db.doc("summaries/sync_registry"), {
        inventoryVersion: FieldValue.increment(1),
        lastModified: now,
      }, { merge: true });

      batch.set(db.doc("summaries/dashboard_hub"), {
        "syncRegistry.inventoryVersion": FieldValue.increment(1),
        "syncRegistry.lastModified": now,
        lastUpdated: now,
      }, { merge: true });

      await batch.commit();
      console.timeEnd("onVariantWritten_totalTime");
      return;
    }

    let createdAlertsCount = 0;
    try {
      console.time("fetchWarehousesAndProduct_parallel");
      const [warehousesSnap, productSnap] = await Promise.all([
        db.collection("warehouses").get(),
        variant.productId ? db.collection("products").doc(variant.productId).get() : Promise.resolve(null),
      ]);
      console.timeEnd("fetchWarehousesAndProduct_parallel");

      const warehouseDocs = warehousesSnap.docs;

      let pName = variant.productName || "";
      if (!pName && productSnap && productSnap.exists) {
        pName = productSnap.data()?.name || "منتج";
      }
      if (!pName) pName = "منتج";

      const currentStockMap = (variant.currentStock || {}) as Record<string, number>;
      const minQuantitiesMap = (variant.minQuantities || {}) as Record<string, number>;
      const defaultMinQty = Number(variant.minQuantity || 0);

      warehouseDocs.forEach((whDoc) => {
        const whId = whDoc.id;
        const whName = whDoc.data()?.name || "مخزن";
        const currentQty = currentStockMap[whId] ?? 0;
        const threshold = minQuantitiesMap[whId] ?? defaultMinQty;

        if (threshold > 0 && currentQty <= threshold) {
          const alertRef = db.collection("system_alerts").doc(`low_stock_${variantId}_${whId}`);
          const specSuffix = variant.specification ? ` | ${variant.specification}` : "";

          batch.set(alertRef, {
            id: alertRef.id,
            type: "low_stock",
            title: `مخزون منخفض: ${pName}`,
            message: `${variant.capacity || 0}A${specSuffix} في ${whName} | الكمية: ${currentQty} (الحد: ${threshold})`,
            relatedId: variantId,
            warehouseId: whId,
            warehouseName: whName,
            resolved: false,
            timestamp: now,
            data: {
              capacity: variant.capacity || 0,
              specification: variant.specification || "",
              currentStock: currentQty,
              threshold: threshold,
            },
          }, { merge: true });
          createdAlertsCount += 1;
        }
      });

      // Consolidated Write Batch: Commit low stock alerts, sync_registry, and dashboard_hub atomically
      console.time("consolidatedWriteBatch_commit");
      batch.set(db.doc("summaries/sync_registry"), {
        inventoryVersion: FieldValue.increment(1),
        alertsVersion: createdAlertsCount > 0 ? FieldValue.increment(1) : FieldValue.increment(0),
        last_alert_update: createdAlertsCount > 0 ? now : FieldValue.delete(),
        lastModified: now,
      }, { merge: true });

      batch.set(db.doc("summaries/dashboard_hub"), {
        "syncRegistry.inventoryVersion": FieldValue.increment(1),
        "syncRegistry.alertsVersion": createdAlertsCount > 0 ? FieldValue.increment(1) : FieldValue.increment(0),
        "syncRegistry.last_alert_update": createdAlertsCount > 0 ? now : FieldValue.delete(),
        "syncRegistry.lastModified": now,
        lastUpdated: now,
      }, { merge: true });

      await batch.commit();
      console.timeEnd("consolidatedWriteBatch_commit");
    } catch (error) {
      console.error("Error in onVariantWritten consolidated batch commit:", error);
    }

    console.timeEnd("onVariantWritten_totalTime");
  }
);
