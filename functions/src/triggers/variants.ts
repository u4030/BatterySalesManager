import {
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

/**
 * onVariantWritten Trigger:
 * Centralized low-stock alert injector and sync reconciler.
 * Evaluates variant stock levels against minQuantities and writes a clean unified alert
 * with an explicit boolean resolved: false.
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
    const unifiedAlertRef = db.collection("system_alerts").doc(`low_stock_${variantId}`);
    const batch = db.batch();
    const now = FieldValue.serverTimestamp();

    if (!afterSnap.exists) {
      // Document deleted - cleanup unified alert
      batch.delete(unifiedAlertRef);
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
      batch.delete(unifiedAlertRef);
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
      const currentStockMap = (variant.currentStock || {}) as Record<string, any>;
      const minQuantitiesMap = (variant.minQuantities || {}) as Record<string, any>;
      const defaultMinQty = Number(variant.minQuantity || 0);

      // Check if stock in any warehouse or total stock is at or below threshold
      let isLow = false;
      const stockEntries = Object.entries(currentStockMap);
      if (stockEntries.length > 0) {
        for (const [whId, rawQty] of stockEntries) {
          const qty = typeof rawQty === "number" ? rawQty : Number(rawQty || 0);
          const rawMin = minQuantitiesMap[whId];
          const threshold = typeof rawMin === "number" ? rawMin : (rawMin ? Number(rawMin) : defaultMinQty);
          if (threshold > 0 && qty <= threshold) {
            isLow = true;
            break;
          }
        }
      }

      const totalStock = Object.values(currentStockMap).reduce((acc: number, v: any) => acc + Number(v || 0), 0);
      if (defaultMinQty > 0 && totalStock <= defaultMinQty) {
        isLow = true;
      }

      if (isLow) {
        const alertPayload = {
          id: `low_stock_${variantId}`,
          type: "low_stock",
          title: `مخزون منخفض حرج`,
          message: `المخزون الحالي هبط تحت حد الأمان المعتمد`,
          variantId: variantId,
          resolved: false, // حقل الفلترة المركزي نقي وصريح
          timestamp: now,
        };
        batch.set(unifiedAlertRef, alertPayload, { merge: true });
      } else {
        batch.delete(unifiedAlertRef);
      }

      batch.set(
        db.doc("summaries/sync_registry"),
        {
          inventoryVersion: FieldValue.increment(1),
          alertsVersion: FieldValue.increment(1),
          last_alert_update: now,
          lastModified: now,
        },
        { merge: true }
      );

      batch.set(
        db.doc("summaries/dashboard_hub"),
        {
          "syncRegistry.inventoryVersion": FieldValue.increment(1),
          "syncRegistry.alertsVersion": FieldValue.increment(1),
          "syncRegistry.last_alert_update": now,
          "syncRegistry.lastModified": now,
          lastUpdated: now,
        },
        { merge: true }
      );

      await batch.commit();
    } catch (error) {
      console.error("Error in onVariantWritten consolidated batch commit:", error);
    }

    console.timeEnd("onVariantWritten_totalTime");
  }
);
