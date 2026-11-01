import {
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

/**
 * onVariantWritten Trigger:
 * Centralized Alert & Idempotency Management:
 * Verifies stock changes against minQuantities across warehouses.
 * Writes clean plain JavaScript objects for system_alerts/low_stock_${variantId}_${warehouseId}.
 * Prevents flapping by skipping redundant writes when alert already exists,
 * and only deleting when stock has risen above the security threshold.
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
      // Document deleted - cleanup alerts for all warehouses
      const existingAlerts = await db.collection("system_alerts")
        .where("relatedId", "==", variantId)
        .get();

      existingAlerts.docs.forEach((doc) => batch.delete(doc.ref));
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
      // If archived or discontinued, remove alerts
      const existingAlerts = await db.collection("system_alerts")
        .where("relatedId", "==", variantId)
        .get();

      existingAlerts.docs.forEach((doc) => batch.delete(doc.ref));
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
      const [warehousesSnap, productSnap, existingAlertsSnap] = await Promise.all([
        db.collection("warehouses").get(),
        variant.productId ? db.collection("products").doc(String(variant.productId)).get() : Promise.resolve(null),
        db.collection("system_alerts").where("relatedId", "==", variantId).get(),
      ]);

      const warehouseDocs = warehousesSnap.docs;
      const existingAlertsMap = new Map<string, admin.firestore.QueryDocumentSnapshot>();
      existingAlertsSnap.docs.forEach((d) => existingAlertsMap.set(d.id, d));

      let pName = String(variant.productName || "");
      if (!pName && productSnap && productSnap.exists) {
        pName = String(productSnap.data()?.name || "منتج");
      }
      if (!pName) pName = "منتج";

      const currentStockMap = (variant.currentStock || {}) as Record<string, any>;
      const minQuantitiesMap = (variant.minQuantities || {}) as Record<string, any>;
      const defaultMinQty = Number(variant.minQuantity || 0);

      let netAlertDelta = 0;

      warehouseDocs.forEach((whDoc) => {
        const whId = String(whDoc.id);
        const whName = String(whDoc.data()?.name || "مخزن");
        const rawStock = currentStockMap[whId];
        const currentQty = typeof rawStock === "number" ? rawStock : Number(rawStock || 0);
        const rawMin = minQuantitiesMap[whId];
        const threshold = typeof rawMin === "number" ? rawMin : (rawMin ? Number(rawMin) : defaultMinQty);

        const alertDocId = `low_stock_${variantId}_${whId}`;
        const existingAlertDoc = existingAlertsMap.get(alertDocId);

        if (threshold > 0 && currentQty <= threshold) {
          // Low stock condition: anti-flapping guard
          // If already existing and unresolved, DO NOTHING to save delete/re-create churn
          if (!existingAlertDoc || existingAlertDoc.data()?.resolved === true) {
            const alertPayload = {
              id: alertDocId,
              type: "low_stock",
              title: "مستوى المخزون منخفض",
              message: `الصنف ${pName} شارف على النفاد في المستودع`,
              warehouseId: whId,
              warehouseName: whName,
              relatedId: variantId,
              resolved: false,
              timestamp: now,
            };
            batch.set(db.collection("system_alerts").doc(alertDocId), alertPayload);
            netAlertDelta += 1;
          }
        } else if (existingAlertDoc) {
          // Stock has recovered safely above threshold -> delete alert
          batch.delete(existingAlertDoc.ref);
          if (existingAlertDoc.data()?.resolved !== true) {
            netAlertDelta -= 1;
          }
        }
      });

      // Cleanup legacy unified doc if present
      batch.delete(db.collection("system_alerts").doc(`low_stock_${variantId}`));

      batch.set(
        db.doc("summaries/sync_registry"),
        {
          inventoryVersion: FieldValue.increment(1),
          alertsVersion: netAlertDelta !== 0 ? FieldValue.increment(1) : FieldValue.increment(0),
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

      if (netAlertDelta !== 0) {
        hubUpdates["scrapAndAlertsSummary.activeLowStockAlertsCount"] = FieldValue.increment(netAlertDelta);
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
