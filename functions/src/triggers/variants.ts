import {
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

/**
 * onVariantWritten Trigger:
 * Centralized Alert & Idempotency Management:
 * Verifies stock changes against minQuantities across warehouses.
 * Writes system_alerts/low_stock_${variantId} if any warehouse stock is at or below its minQuantity.
 * Deletes the alert document only when stock levels across all warehouses are safe.
 */
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
    const variantId = String(event.params.variantId);
    const batch = db.batch();
    const now = FieldValue.serverTimestamp();

    if (!afterSnap.exists) {
      // Document deleted - cleanup alerts and update sync
      batch.delete(db.doc(`system_alerts/low_stock_${variantId}`));
      batch.set(db.doc("summaries/sync_registry"), {
        inventoryVersion: FieldValue.increment(1),
        alertsVersion: FieldValue.increment(1),
        lastModified: now,
      }, { merge: true });

      batch.set(db.doc("summaries/dashboard_hub"), {
        "syncRegistry.inventoryVersion": FieldValue.increment(1),
        "syncRegistry.alertsVersion": FieldValue.increment(1),
        "syncRegistry.lastModified": now,
        lastUpdated: now,
      }, { merge: true });

      await batch.commit();
      console.timeEnd("onVariantWritten_totalTime");
      return;
    }

    const variant = afterSnap.data();

    if (!variant || variant.archived || variant.isDiscontinued) {
      // If archived or discontinued, remove alert
      batch.delete(db.doc(`system_alerts/low_stock_${variantId}`));
      batch.set(db.doc("summaries/sync_registry"), {
        inventoryVersion: FieldValue.increment(1),
        alertsVersion: FieldValue.increment(1),
        lastModified: now,
      }, { merge: true });

      batch.set(db.doc("summaries/dashboard_hub"), {
        "syncRegistry.inventoryVersion": FieldValue.increment(1),
        "syncRegistry.alertsVersion": FieldValue.increment(1),
        "syncRegistry.lastModified": now,
        lastUpdated: now,
      }, { merge: true });

      await batch.commit();
      console.timeEnd("onVariantWritten_totalTime");
      return;
    }

    try {
      const [warehousesSnap, productSnap, existingAlertSnap] = await Promise.all([
        db.collection("warehouses").get(),
        variant.productId ? db.collection("products").doc(String(variant.productId)).get() : Promise.resolve(null),
        db.doc(`system_alerts/low_stock_${variantId}`).get(),
      ]);

      const warehouseDocs = warehousesSnap.docs;

      let pName = String(variant.productName || "");
      if (!pName && productSnap && productSnap.exists) {
        pName = String(productSnap.data()?.name || "منتج");
      }
      if (!pName) pName = "منتج";

      const currentStockMap = (variant.currentStock || {}) as Record<string, any>;
      const minQuantitiesMap = (variant.minQuantities || {}) as Record<string, any>;
      const defaultMinQty = Number(variant.minQuantity || 0);
      const capacity = Number(variant.capacity || 0);
      const spec = String(variant.specification || "");

      const warehousesDetailsMap: Record<string, { currentStock: number; threshold: number; warehouseName: string }> = {};
      const lowStockDetailsList: string[] = [];
      let primaryWarehouseId = "";
      let primaryWarehouseName = "";

      warehouseDocs.forEach((whDoc) => {
        const whId = String(whDoc.id);
        const whName = String(whDoc.data()?.name || "مخزن");
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
          if (!primaryWarehouseId) {
            primaryWarehouseId = whId;
            primaryWarehouseName = whName;
          }
        }
      });

      const unifiedAlertRef = db.collection("system_alerts").doc(`low_stock_${variantId}`);
      let alertDelta = 0;

      if (lowStockDetailsList.length > 0) {
        const alertPayload = {
          id: `low_stock_${variantId}`,
          type: "low_stock",
          title: `مخزون منخفض: ${pName}`,
          message: `المخزون منخفض للصنف ${pName}`,
          relatedId: variantId,
          warehouseId: primaryWarehouseId,
          warehouseName: primaryWarehouseName,
          resolved: false,
          timestamp: now,
          data: {
            capacity: capacity,
            specification: spec,
            warehouseId: primaryWarehouseId,
            warehouseName: primaryWarehouseName,
            currentStock: Object.values(warehousesDetailsMap)[0]?.currentStock || 0,
            threshold: Object.values(warehousesDetailsMap)[0]?.threshold || defaultMinQty,
            warehousesDetails: warehousesDetailsMap,
          },
        };
        batch.set(unifiedAlertRef, alertPayload, { merge: true });

        if (!existingAlertSnap.exists || existingAlertSnap.data()?.resolved === true) {
          alertDelta = 1;
        }

        // Clean up legacy individual warehouse alert documents
        warehouseDocs.forEach((whDoc) => {
          batch.delete(db.collection("system_alerts").doc(`low_stock_${variantId}_${whDoc.id}`));
        });
      } else if (existingAlertSnap.exists) {
        // Stock safely exceeds threshold across all warehouses -> delete alert
        batch.delete(unifiedAlertRef);
        if (existingAlertSnap.data()?.resolved !== true) {
          alertDelta = -1;
        }
        warehouseDocs.forEach((whDoc) => {
          batch.delete(db.collection("system_alerts").doc(`low_stock_${variantId}_${whDoc.id}`));
        });
      }

      batch.set(
        db.doc("summaries/sync_registry"),
        {
          inventoryVersion: FieldValue.increment(1),
          alertsVersion: alertDelta !== 0 ? FieldValue.increment(1) : FieldValue.increment(0),
          last_alert_update: now,
          lastModified: now,
        },
        { merge: true }
      );

      const hubUpdates: Record<string, any> = {
        "syncRegistry.inventoryVersion": FieldValue.increment(1),
        "syncRegistry.last_alert_update": now,
        "syncRegistry.lastModified": now,
        lastUpdated: now,
      };

      if (alertDelta !== 0) {
        hubUpdates["scrapAndAlertsSummary.activeLowStockAlertsCount"] = FieldValue.increment(alertDelta);
        hubUpdates["syncRegistry.alertsVersion"] = FieldValue.increment(1);
      }

      batch.set(db.doc("summaries/dashboard_hub"), hubUpdates, { merge: true });

      await batch.commit();
    } catch (error) {
      console.error("Error in onVariantWritten consolidated batch commit:", error);
    }

    console.timeEnd("onVariantWritten_totalTime");
  }
);
