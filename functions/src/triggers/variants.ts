import {
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

/**
 * onVariantWritten Trigger:
 * Strict Idempotency & Cascade Prevention:
 * Low-stock checking and alert mutations are consolidated centrally in onStockEntryCreated.
 * This trigger only syncs inventoryVersion when variants are archived, discontinued, or deleted.
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

      // If only currentStock or weightedAverageCost changed (e.g. from stock entry writes),
      // low-stock alerts are handled atomically in onStockEntryCreated to prevent trigger cascades!
      if (statusUnchanged && minQtyUnchanged && priceUnchanged) {
        console.log(`[CascadeGuard] Variant ${event.params.variantId} stock update handled centrally. Skipping duplicate alert generation.`);
        return;
      }
    }

    console.time("onVariantWritten_totalTime");
    const db = admin.firestore();
    const batch = db.batch();
    const now = FieldValue.serverTimestamp();

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
  }
);
