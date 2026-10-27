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
    const variantId = event.params.variantId;
    const batch = db.batch();
    const now = firestore_2.FieldValue.serverTimestamp();
    if (!afterSnap.exists) {
        // Document deleted
        batch.set(db.doc("summaries/sync_registry"), {
            inventoryVersion: firestore_2.FieldValue.increment(1),
            lastModified: now,
        }, { merge: true });
        batch.set(db.doc("summaries/dashboard_hub"), {
            "syncRegistry.inventoryVersion": firestore_2.FieldValue.increment(1),
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
            inventoryVersion: firestore_2.FieldValue.increment(1),
            lastModified: now,
        }, { merge: true });
        batch.set(db.doc("summaries/dashboard_hub"), {
            "syncRegistry.inventoryVersion": firestore_2.FieldValue.increment(1),
            "syncRegistry.lastModified": now,
            lastUpdated: now,
        }, { merge: true });
        await batch.commit();
        console.timeEnd("onVariantWritten_totalTime");
        return;
    }
    let createdAlertsCount = 0;
    try {
        console.time("fetchWarehousesAndSummaries_parallel");
        const [warehousesSnap, productSnap, globalSummarySnap] = await Promise.all([
            db.collection("warehouses").get(),
            variant.productId ? db.collection("products").doc(variant.productId).get() : Promise.resolve(null),
            db.doc("summaries/inventory_global").get(),
        ]);
        const warehouseDocs = warehousesSnap.docs;
        const whSummarySnaps = await Promise.all(warehouseDocs.map((whDoc) => db.doc(`summaries/inventory_wh_${whDoc.id}`).get()));
        console.timeEnd("fetchWarehousesAndSummaries_parallel");
        let pName = variant.productName || "";
        if (!pName && productSnap && productSnap.exists) {
            pName = productSnap.data()?.name || "منتج";
        }
        if (!pName)
            pName = "منتج";
        const currentStockMap = (variant.currentStock || {});
        const minQuantitiesMap = (variant.minQuantities || {});
        const defaultMinQty = Number(variant.minQuantity || 0);
        const cost = Number(variant.weightedAverageCost || 0);
        const sellingPrice = Number(variant.sellingPrice || 0);
        const capacity = Number(variant.capacity || 0);
        // --- 1. Cook Warehouse Summaries (summaries/inventory_wh_[id]) ---
        warehouseDocs.forEach((whDoc) => {
            const whId = whDoc.id;
            const whName = whDoc.data()?.name || "مخزن";
            const rawStock = currentStockMap[whId];
            const currentQty = typeof rawStock === "number" ? rawStock : Number(rawStock || 0);
            const rawMin = minQuantitiesMap[whId];
            const threshold = typeof rawMin === "number" ? rawMin : (rawMin ? Number(rawMin) : defaultMinQty);
            const whSummarySnap = whSummarySnaps.find((s) => s.id === `inventory_wh_${whId}`);
            const items = whSummarySnap && whSummarySnap.exists ? (whSummarySnap.data()?.items || {}) : {};
            items[variantId] = {
                variantId: variantId,
                productId: variant.productId || "",
                productName: pName,
                capacity: capacity,
                specification: variant.specification || "",
                barcode: variant.barcode || "",
                currentStock: currentQty,
                minQuantity: threshold,
                weightedAverageCost: cost,
                sellingPrice: sellingPrice,
                updatedAt: now,
                isDiscontinued: Boolean(variant.isDiscontinued),
            };
            const totalValue = Object.values(items).reduce((acc, item) => acc + (Number(item?.currentStock || 0) * Number(item?.weightedAverageCost || 0)), 0);
            const totalItemsCount = Object.values(items).reduce((acc, item) => acc + Number(item?.currentStock || 0), 0);
            batch.set(db.doc(`summaries/inventory_wh_${whId}`), {
                id: `inventory_wh_${whId}`,
                warehouseId: whId,
                items: items,
                totalValue: Math.max(0, totalValue),
                totalItemsCount: Math.max(0, totalItemsCount),
                lastUpdated: now,
                version: firestore_2.FieldValue.increment(1),
            }, { merge: true });
            // Low stock alert check
            if (threshold > 0 && currentQty <= threshold) {
                const alertRef = db.collection("system_alerts").doc(`low_stock_${variantId}_${whId}`);
                const specSuffix = variant.specification ? ` | ${variant.specification}` : "";
                batch.set(alertRef, {
                    id: alertRef.id,
                    type: "low_stock",
                    title: `مخزون منخفض: ${pName}`,
                    message: `${capacity}A${specSuffix} في ${whName} | الكمية: ${currentQty} (الحد: ${threshold})`,
                    relatedId: variantId,
                    warehouseId: whId,
                    warehouseName: whName,
                    resolved: false,
                    timestamp: now,
                    data: {
                        capacity: capacity,
                        specification: variant.specification || "",
                        currentStock: currentQty,
                        threshold: threshold,
                    },
                }, { merge: true });
                createdAlertsCount += 1;
            }
        });
        // --- 2. Cook Global Inventory Summary (summaries/inventory_global) ---
        const globalItems = globalSummarySnap.exists ? (globalSummarySnap.data()?.items || {}) : {};
        const totalGlobalStock = Object.values(currentStockMap).reduce((acc, qty) => {
            const num = typeof qty === "number" ? qty : Number(qty || 0);
            return acc + (isNaN(num) ? 0 : num);
        }, 0);
        globalItems[variantId] = {
            variantId: variantId,
            productId: variant.productId || "",
            productName: pName,
            capacity: capacity,
            specification: variant.specification || "",
            barcode: variant.barcode || "",
            currentStock: totalGlobalStock,
            minQuantity: defaultMinQty,
            weightedAverageCost: cost,
            sellingPrice: sellingPrice,
            updatedAt: now,
            isDiscontinued: Boolean(variant.isDiscontinued),
        };
        const globalTotalValue = Object.values(globalItems).reduce((acc, item) => acc + (Number(item?.currentStock || 0) * Number(item?.weightedAverageCost || 0)), 0);
        const globalTotalItemsCount = Object.values(globalItems).reduce((acc, item) => acc + Number(item?.currentStock || 0), 0);
        batch.set(db.doc("summaries/inventory_global"), {
            id: "inventory_global",
            warehouseId: null,
            items: globalItems,
            totalValue: Math.max(0, globalTotalValue),
            totalItemsCount: Math.max(0, globalTotalItemsCount),
            lastUpdated: now,
            version: firestore_2.FieldValue.increment(1),
        }, { merge: true });
        // --- 3. Update Sync Registry & Dashboard Hub ---
        console.time("consolidatedWriteBatch_commit");
        batch.set(db.doc("summaries/sync_registry"), {
            inventoryVersion: firestore_2.FieldValue.increment(1),
            alertsVersion: createdAlertsCount > 0 ? firestore_2.FieldValue.increment(1) : firestore_2.FieldValue.increment(0),
            last_alert_update: createdAlertsCount > 0 ? now : firestore_2.FieldValue.delete(),
            lastModified: now,
        }, { merge: true });
        batch.set(db.doc("summaries/dashboard_hub"), {
            "inventorySummary.totalValue": Math.max(0, globalTotalValue),
            "inventorySummary.totalItemsCount": Math.max(0, globalTotalItemsCount),
            "syncRegistry.inventoryVersion": firestore_2.FieldValue.increment(1),
            "syncRegistry.alertsVersion": createdAlertsCount > 0 ? firestore_2.FieldValue.increment(1) : firestore_2.FieldValue.increment(0),
            "syncRegistry.last_alert_update": createdAlertsCount > 0 ? now : firestore_2.FieldValue.delete(),
            "syncRegistry.lastModified": now,
            lastUpdated: now,
        }, { merge: true });
        await batch.commit();
        console.timeEnd("consolidatedWriteBatch_commit");
    }
    catch (error) {
        console.error("Error in onVariantWritten consolidated batch commit:", error);
    }
    console.timeEnd("onVariantWritten_totalTime");
});
//# sourceMappingURL=variants.js.map