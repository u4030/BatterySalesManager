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
 * Centralized low-stock alert injector and sync reconciler.
 * Evaluates variant stock levels against minQuantities and writes a clean unified alert
 * with an explicit boolean resolved: false.
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
    }
    console.time("onVariantWritten_totalTime");
    const db = admin.firestore();
    const variantId = String(event.params.variantId);
    const unifiedAlertRef = db.collection("system_alerts").doc(`low_stock_${variantId}`);
    const batch = db.batch();
    const now = firestore_2.FieldValue.serverTimestamp();
    if (!afterSnap.exists) {
        // Document deleted - cleanup unified alert
        batch.delete(unifiedAlertRef);
        batch.set(db.doc("summaries/sync_registry"), {
            inventoryVersion: firestore_2.FieldValue.increment(1),
            alertsVersion: firestore_2.FieldValue.increment(1),
            lastModified: now,
        }, { merge: true });
        batch.set(db.doc("summaries/dashboard_hub"), {
            "syncRegistry.inventoryVersion": firestore_2.FieldValue.increment(1),
            "syncRegistry.alertsVersion": firestore_2.FieldValue.increment(1),
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
            inventoryVersion: firestore_2.FieldValue.increment(1),
            alertsVersion: firestore_2.FieldValue.increment(1),
            lastModified: now,
        }, { merge: true });
        batch.set(db.doc("summaries/dashboard_hub"), {
            "syncRegistry.inventoryVersion": firestore_2.FieldValue.increment(1),
            "syncRegistry.alertsVersion": firestore_2.FieldValue.increment(1),
            "syncRegistry.lastModified": now,
            lastUpdated: now,
        }, { merge: true });
        await batch.commit();
        console.timeEnd("onVariantWritten_totalTime");
        return;
    }
    try {
        const currentStockMap = (variant.currentStock || {});
        const minQuantitiesMap = (variant.minQuantities || {});
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
        const totalStock = Object.values(currentStockMap).reduce((acc, v) => acc + Number(v || 0), 0);
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
        }
        else {
            batch.delete(unifiedAlertRef);
        }
        batch.set(db.doc("summaries/sync_registry"), {
            inventoryVersion: firestore_2.FieldValue.increment(1),
            alertsVersion: firestore_2.FieldValue.increment(1),
            last_alert_update: now,
            lastModified: now,
        }, { merge: true });
        batch.set(db.doc("summaries/dashboard_hub"), {
            "syncRegistry.inventoryVersion": firestore_2.FieldValue.increment(1),
            "syncRegistry.alertsVersion": firestore_2.FieldValue.increment(1),
            "syncRegistry.last_alert_update": now,
            "syncRegistry.lastModified": now,
            lastUpdated: now,
        }, { merge: true });
        await batch.commit();
    }
    catch (error) {
        console.error("Error in onVariantWritten consolidated batch commit:", error);
    }
    console.timeEnd("onVariantWritten_totalTime");
});
//# sourceMappingURL=variants.js.map