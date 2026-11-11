import {
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

/**
 * onVariantWritten Trigger:
 * Strictly silent: Zero side-effects for system alerts or warehouse stock.
 * Low stock checks and system alerts logic are 100% centralized inside onStockEntryCreated.
 * This trigger only manages domain IdempotencyGuard and increments inventoryVersion on
 * sync_registry / dashboard_hub when domain fields change, eliminating race conditions (flapping).
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

      // If only currentStock or weightedAverageCost changed (written from onStockEntryCreated),
      // exit immediately to prevent cascading updates and race conditions!
      if (statusUnchanged && minQtyUnchanged && priceUnchanged) {
        console.log(`[CascadeGuard] Variant ${event.params.variantId} stock update handled centrally. Skipping duplicate trigger execution.`);
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

    // Threshold check on variant update:
    // If minQuantity or minQuantities is updated to a value >= currentWhStock, create or update low_stock alerts
    if (afterSnap.exists) {
      const variantData = afterSnap.data() || {};
      const currentStockMap = (variantData.currentStock || {}) as Record<string, number>;
      const minQuantities = (variantData.minQuantities || {}) as Record<string, number>;
      const globalMin = Number(variantData.minQuantity || 0);
      const variantId = event.params.variantId;
      const capacity = Number(variantData.capacity || 0);
      const spec = String(variantData.specification || "");

      for (const [whId, stockVal] of Object.entries(currentStockMap)) {
        const whStock = Number(stockVal || 0);
        const whMin = Number(minQuantities[whId] ?? globalMin);

        if (whStock <= whMin && whMin > 0) {
          const alertDocId = `low_stock_${variantId}_${whId}`;
          const alertRef = db.doc(`system_alerts/${alertDocId}`);
          
          let whArabicName = "المستودع";
          try {
            const whSnap = await db.doc(`warehouses/${whId}`).get();
            if (whSnap.exists) whArabicName = whSnap.data()?.name || "المستودع";
          } catch (e) {
            console.error(`Error fetching warehouse ${whId} name:`, e);
          }

          const pName = String(variantData.productName || "بطارية");
          const pId = String(variantData.productId || "");

          batch.set(
            alertRef,
            {
              id: alertDocId,
              type: "low_stock",
              title: `مخزون منخفض: ${pName}`,
              message: `الكمية المتوفرة حرجة في ${whArabicName}`,
              productId: pId,
              productName: pName,
              variantId: variantId,
              warehouseId: whId,
              warehouseName: whArabicName,
              resolved: false,
              status: "active",
              timestamp: now,
              currentStock: whStock,
              threshold: whMin,
              capacity: capacity,
              specification: spec,
              data: {
                productId: pId,
                productName: pName,
                capacity: capacity,
                specification: spec,
                currentStock: whStock,
                threshold: whMin,
                warehouseName: whArabicName,
              },
            },
            { merge: true }
          );
        }
      }
    }

    await batch.commit();
    console.timeEnd("onVariantWritten_totalTime");
  }
);
