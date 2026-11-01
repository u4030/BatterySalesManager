import {
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

export const onVariantWritten = onDocumentWritten(
  "product_variants/{variantId}",
  async (event) => {
    if (!event.data) return;
    const beforeSnap = event.data.before;
    const afterSnap = event.data.after;

    // Strict Idempotency Guardrail: If updating and no core domain fields changed, exit immediately!
    if (beforeSnap.exists && afterSnap.exists) {
      const b = beforeSnap.data() || {};
      const a = afterSnap.data() || {};

      const stockUnchanged = JSON.stringify(b.currentStock || {}) === JSON.stringify(a.currentStock || {});
      const minQtyUnchanged = JSON.stringify(b.minQuantities || {}) === JSON.stringify(a.minQuantities || {}) && b.minQuantity === a.minQuantity;
      const statusUnchanged = b.archived === a.archived && b.isDiscontinued === a.isDiscontinued;
      const priceUnchanged = b.sellingPrice === a.sellingPrice && b.weightedAverageCost === a.weightedAverageCost;

      if (stockUnchanged && minQtyUnchanged && statusUnchanged && priceUnchanged) {
        console.log(`[IdempotencyGuard] Variant ${event.params.variantId} update had no domain changes. Skipping execution.`);
        return;
      }
    }

    console.time("onVariantWritten_totalTime");
    const db = admin.firestore();
    const variantId = event.params.variantId;

    const batch = db.batch();
    const now = FieldValue.serverTimestamp();

    if (!afterSnap.exists) {
      // Document deleted - increment inventory version and sync
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

      const currentStockMap = (variant.currentStock || {}) as Record<string, any>;
      const minQuantitiesMap = (variant.minQuantities || {}) as Record<string, any>;
      const defaultMinQty = Number(variant.minQuantity || 0);
      const capacity = Number(variant.capacity || 0);

        const warehousesDetailsMap: Record<string, { currentStock: number; threshold: number; warehouseName: string }> = {};
        const lowStockDetailsList: string[] = [];

      warehouseDocs.forEach((whDoc) => {
        const whId = whDoc.id;
        const whName = whDoc.data()?.name || "مخزن";
        const rawStock = currentStockMap[whId];
        const currentQty = typeof rawStock === "number" ? rawStock : Number(rawStock || 0);
        const rawMin = minQuantitiesMap[whId];
        const threshold = typeof rawMin === "number" ? rawMin : (rawMin ? Number(rawMin) : defaultMinQty);

        if (threshold > 0 && currentQty <= threshold) {
            warehousesDetailsMap[whId] = {
              currentStock: currentQty,
              threshold: threshold,
              warehouseName: whName,
            };
            lowStockDetailsList.push(`${whName}: الحالي ${currentQty} من ${threshold}`);
          }
        });

        const unifiedAlertRef = db.collection("system_alerts").doc(`low_stock_${variantId}`);

        if (lowStockDetailsList.length > 0) {
          const specSuffix = variant.specification ? ` | ${variant.specification}` : "";
          batch.set(
            unifiedAlertRef,
            {
              id: unifiedAlertRef.id,
              type: "low_stock",
              title: `مخزون منخفض: ${pName}`,
              message: `${capacity}A${specSuffix} - ${lowStockDetailsList.join(" | ")}`,
              relatedId: variantId,
              resolved: false,
              timestamp: now,
              data: {
                capacity: capacity,
                specification: variant.specification || "",
                warehousesDetails: warehousesDetailsMap,
            },
            },
            { merge: true }
          );

          // Clean up old individual warehouse alert documents
          warehouseDocs.forEach((whDoc) => {
            batch.delete(db.collection("system_alerts").doc(`low_stock_${variantId}_${whDoc.id}`));
          });
        } else {
          // All warehouses safe - delete unified alert and individual alerts
          batch.delete(unifiedAlertRef);
          warehouseDocs.forEach((whDoc) => {
            batch.delete(db.collection("system_alerts").doc(`low_stock_${variantId}_${whDoc.id}`));
          });
        }

      // Update Sync Registry & Dashboard Hub
      console.time("consolidatedWriteBatch_commit");
      batch.set(
        db.doc("summaries/sync_registry"),
        {
          alertsVersion: FieldValue.increment(1),
          last_alert_update: now,
          lastModified: now,
        },
        { merge: true }
      );

      batch.set(
        db.doc("summaries/dashboard_hub"),
        {
          "syncRegistry.alertsVersion": FieldValue.increment(1),
          "syncRegistry.last_alert_update": now,
          "syncRegistry.lastModified": now,
          lastUpdated: now,
        },
        { merge: true }
      );

      await batch.commit();
      console.timeEnd("consolidatedWriteBatch_commit");
    } catch (error) {
      console.error("Error in onVariantWritten consolidated batch commit:", error);
    }

    console.timeEnd("onVariantWritten_totalTime");
  }
);
