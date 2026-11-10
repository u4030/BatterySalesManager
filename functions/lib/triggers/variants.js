"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.onVariantWritten = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = __importStar(require("firebase-admin"));
const firestore_2 = require("firebase-admin/firestore");
/**
 * onVariantWritten Trigger:
 * Strictly silent: Zero side-effects for system alerts or warehouse stock.
 * Low stock checks and system alerts logic are 100% centralized inside onStockEntryCreated.
 * This trigger only manages domain IdempotencyGuard and increments inventoryVersion on
 * sync_registry / dashboard_hub when domain fields change, eliminating race conditions (flapping).
 */
exports.onVariantWritten = (0, firestore_1.onDocumentWritten)("product_variants/{variantId}", async (event) => {
    if (!event.data)
        return;
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
    const now = firestore_2.FieldValue.serverTimestamp();
    batch.set(db.doc("summaries/sync_registry"), {
        inventoryVersion: firestore_2.FieldValue.increment(1),
        lastModified: now,
    }, { merge: true });
    batch.set(db.doc("summaries/dashboard_hub"), {
        "syncRegistry.inventoryVersion": firestore_2.FieldValue.increment(1),
        "syncRegistry.lastModified": now,
        lastUpdated: now,
    }, { merge: true });
    // Threshold check on variant update:
    // If minQuantity or minQuantities is updated to a value >= currentWhStock, create or update low_stock alerts
    if (afterSnap.exists) {
        const variantData = afterSnap.data() || {};
        const currentStockMap = (variantData.currentStock || {});
        const minQuantities = (variantData.minQuantities || {});
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
                    if (whSnap.exists)
                        whArabicName = whSnap.data()?.name || "المستودع";
                }
                catch (e) {
                    console.error(`Error fetching warehouse ${whId} name:`, e);
                }
                batch.set(alertRef, {
                    id: alertDocId,
                    type: "low_stock",
                    title: "مخزون منخفض حرج",
                    message: `الكمية المتوفرة حرجة في ${whArabicName}`,
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
                        capacity: capacity,
                        specification: spec,
                        currentStock: whStock,
                        threshold: whMin,
                        warehouseName: whArabicName,
                    },
                }, { merge: true });
            }
        }
    }
    await batch.commit();
    console.timeEnd("onVariantWritten_totalTime");
});
//# sourceMappingURL=variants.js.map