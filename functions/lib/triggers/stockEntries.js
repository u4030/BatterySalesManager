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
exports.onStockEntryDeleted = exports.onStockEntryUpdated = exports.onStockEntryCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
const admin = __importStar(require("firebase-admin"));
const firestore_2 = require("firebase-admin/firestore");
exports.onStockEntryCreated = (0, firestore_1.onDocumentCreated)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    const entryId = event.params.entryId;
    console.time(`onStockEntryCreated_${entryId}`);
    const db = admin.firestore();
    const entry = event.data.data();
    if (!entry)
        return;
    const variantId = entry.productVariantId || entry.variantId;
    const warehouseId = entry.warehouseId;
    if (!variantId || !warehouseId)
        return;
    const qty = Number(entry.quantity || 0);
    const returnedQty = Number(entry.returnedQuantity || 0);
    const netQty = qty - returnedQty;
    const costPrice = Number(entry.costPrice || 0);
    const now = firestore_2.FieldValue.serverTimestamp();
    const processedRef = db.doc(`processed_transactions/${entryId}`);
    const variantRef = db.doc(`product_variants/${variantId}`);
    const whSummaryRef = db.doc(`summaries/inventory_wh_${warehouseId}`);
    const globalSummaryRef = db.doc("summaries/inventory_global");
    const warehouseRef = db.doc(`warehouses/${warehouseId}`);
    const alertRef = db.doc(`system_alerts/low_stock_${variantId}`);
    const syncRegistryRef = db.doc("summaries/sync_registry");
    const dashboardHubRef = db.doc("summaries/dashboard_hub");
    try {
        await db.runTransaction(async (transaction) => {
            // --- READ PHASE ---
            const processedSnap = await transaction.get(processedRef);
            if (processedSnap.exists) {
                console.log(`[IdempotencyGuard] StockEntry ${entryId} already processed. Skipping execution.`);
                return;
            }
            const [variantSnap, whSummarySnap, globalSummarySnap, warehouseSnap, alertSnap] = await Promise.all([
                transaction.get(variantRef),
                transaction.get(whSummaryRef),
                transaction.get(globalSummaryRef),
                transaction.get(warehouseRef),
                transaction.get(alertRef),
            ]);
            const variant = variantSnap.exists ? (variantSnap.data() || {}) : {};
            const oldWac = Number(variant.weightedAverageCost || 0);
            const currentStockMap = (variant.currentStock || {});
            const oldTotalStock = Object.values(currentStockMap).reduce((acc, val) => acc + Number(val || 0), 0);
            // Weighted Average Cost calculation:
            let newWac = oldWac;
            if (costPrice > 0 && netQty > 0) {
                const totalExistingValue = Math.max(0, oldTotalStock) * oldWac;
                const incomingValue = netQty * costPrice;
                const totalNewStock = Math.max(0, oldTotalStock) + netQty;
                newWac = totalNewStock > 0 ? (totalExistingValue + incomingValue) / totalNewStock : costPrice;
            }
            else if (newWac <= 0 && costPrice > 0) {
                newWac = costPrice;
            }
            // Warehouse Inventory Summary (summaries/inventory_wh_[id])
            const whItems = whSummarySnap.exists ? (whSummarySnap.data()?.items || {}) : {};
            const oldWhItem = whItems[variantId] || {};
            const newWhStock = Math.max(0, Number(oldWhItem.currentStock || 0) + netQty);
            const itemCost = newWac > 0 ? newWac : Number(entry.costPrice || oldWhItem.weightedAverageCost || 0);
            const minQty = Number((variant.minQuantities || {})[warehouseId] ?? variant.minQuantity ?? oldWhItem.minQuantity ?? 0);
            const pName = entry.productName || variant.productName || oldWhItem.productName || "منتج";
            const capacity = Number(entry.capacity || variant.capacity || oldWhItem.capacity || 0);
            const spec = entry.specification || variant.specification || oldWhItem.specification || "";
            const barcode = variant.barcode || oldWhItem.barcode || "";
            whItems[variantId] = {
                variantId: variantId,
                productId: variant.productId || oldWhItem.productId || "",
                productName: pName,
                capacity: capacity,
                specification: spec,
                barcode: barcode,
                currentStock: newWhStock,
                minQuantity: minQty,
                weightedAverageCost: itemCost,
                sellingPrice: Number(variant.sellingPrice || oldWhItem.sellingPrice || 0),
                updatedAt: now,
                isDiscontinued: Boolean(variant.isDiscontinued ?? oldWhItem.isDiscontinued),
            };
            const whTotalValue = Object.values(whItems).reduce((acc, item) => acc + (Number(item?.currentStock || 0) * Number(item?.weightedAverageCost || 0)), 0);
            const whTotalItemsCount = Object.values(whItems).reduce((acc, item) => acc + Number(item?.currentStock || 0), 0);
            // Global Inventory Summary (summaries/inventory_global)
            const globalItems = globalSummarySnap.exists ? (globalSummarySnap.data()?.items || {}) : {};
            const oldGlobalItem = globalItems[variantId] || {};
            const newGlobalStock = Math.max(0, Number(oldGlobalItem.currentStock || 0) + netQty);
            globalItems[variantId] = {
                variantId: variantId,
                productId: variant.productId || oldGlobalItem.productId || "",
                productName: pName,
                capacity: capacity,
                specification: spec,
                barcode: barcode,
                currentStock: newGlobalStock,
                minQuantity: Number(variant.minQuantity || oldGlobalItem.minQuantity || 0),
                weightedAverageCost: itemCost,
                sellingPrice: Number(variant.sellingPrice || oldGlobalItem.sellingPrice || 0),
                updatedAt: now,
                isDiscontinued: Boolean(variant.isDiscontinued ?? oldGlobalItem.isDiscontinued),
            };
            const globalTotalValue = Object.values(globalItems).reduce((acc, item) => acc + (Number(item?.currentStock || 0) * Number(item?.weightedAverageCost || 0)), 0);
            const globalTotalItemsCount = Object.values(globalItems).reduce((acc, item) => acc + Number(item?.currentStock || 0), 0);
            // Low Stock Check & Alert Handling (Atomically inside transaction)
            const whName = warehouseSnap.exists ? (warehouseSnap.data()?.name || warehouseId) : warehouseId;
            const isLowStock = minQty > 0 && newWhStock <= minQty;
            let alertDelta = 0;
            if (isLowStock) {
                const specSuffix = spec ? ` | ${spec}` : "";
                const alertData = {
                    id: `low_stock_${variantId}`,
                    type: "low_stock",
                    title: `مخزون منخفض: ${pName}`,
                    message: `${capacity}A${specSuffix} - ${whName}: الحالي ${newWhStock} من ${minQty}`,
                    relatedId: variantId,
                    resolved: false,
                    timestamp: now,
                    data: {
                        capacity: capacity,
                        specification: spec,
                        warehouseId: warehouseId,
                        warehouseName: whName,
                        currentStock: newWhStock,
                        threshold: minQty,
                    },
                };
                transaction.set(alertRef, alertData, { merge: true });
                if (!alertSnap.exists || alertSnap.data()?.resolved === true) {
                    alertDelta = 1;
                }
            }
            else if (alertSnap.exists) {
                // If stock is now safe, delete or resolve alert
                transaction.delete(alertRef);
                if (alertSnap.data()?.resolved !== true) {
                    alertDelta = -1;
                }
            }
            // --- WRITE PHASE ---
            // 1. Mark as processed (Idempotency Guard)
            transaction.set(processedRef, {
                entryId: entryId,
                variantId: variantId,
                warehouseId: warehouseId,
                processedAt: now,
            });
            // 2. Update product_variants/${variantId}
            transaction.set(variantRef, {
                currentStock: {
                    [warehouseId]: firestore_2.FieldValue.increment(netQty),
                },
                weightedAverageCost: newWac,
                updatedAt: now,
            }, { merge: true });
            // 3. Update summaries/inventory_wh_${warehouseId}
            transaction.set(whSummaryRef, {
                id: `inventory_wh_${warehouseId}`,
                warehouseId: warehouseId,
                items: whItems,
                totalValue: Math.max(0, whTotalValue),
                totalItemsCount: Math.max(0, whTotalItemsCount),
                lastUpdated: now,
                version: firestore_2.FieldValue.increment(1),
            }, { merge: true });
            // 4. Update warehouses/${warehouseId} stocks and updatedAt
            transaction.set(warehouseRef, {
                stocks: {
                    [variantId]: firestore_2.FieldValue.increment(netQty),
                },
                updatedAt: now,
            }, { merge: true });
            // 5. Update summaries/inventory_global
            transaction.set(globalSummaryRef, {
                id: "inventory_global",
                warehouseId: null,
                items: globalItems,
                totalValue: Math.max(0, globalTotalValue),
                totalItemsCount: Math.max(0, globalTotalItemsCount),
                lastUpdated: now,
                version: firestore_2.FieldValue.increment(1),
            }, { merge: true });
            // 6. Update Sync Registry & Dashboard Hub
            transaction.set(syncRegistryRef, {
                inventoryVersion: firestore_2.FieldValue.increment(1),
                suppliersVersion: firestore_2.FieldValue.increment(1),
                financialVersion: firestore_2.FieldValue.increment(1),
                alertsVersion: alertDelta !== 0 ? firestore_2.FieldValue.increment(1) : firestore_2.FieldValue.increment(0),
                lastModified: now,
            }, { merge: true });
            const hubUpdates = {
                "inventorySummary.totalValue": Math.max(0, globalTotalValue),
                "inventorySummary.totalItemsCount": Math.max(0, globalTotalItemsCount),
                "syncRegistry.inventoryVersion": firestore_2.FieldValue.increment(1),
                "syncRegistry.suppliersVersion": firestore_2.FieldValue.increment(1),
                "syncRegistry.financialVersion": firestore_2.FieldValue.increment(1),
                "syncRegistry.lastModified": now,
                lastUpdated: now,
            };
            if (alertDelta !== 0) {
                hubUpdates["scrapAndAlertsSummary.activeLowStockAlertsCount"] = firestore_2.FieldValue.increment(alertDelta);
                hubUpdates["syncRegistry.alertsVersion"] = firestore_2.FieldValue.increment(1);
            }
            transaction.set(dashboardHubRef, hubUpdates, { merge: true });
        });
    }
    catch (error) {
        console.error(`Error in onStockEntryCreated transaction execution for ${entryId}:`, error);
    }
    console.timeEnd(`onStockEntryCreated_${entryId}`);
});
exports.onStockEntryUpdated = (0, firestore_1.onDocumentUpdated)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    const b = event.data.before.data() || {};
    const a = event.data.after.data() || {};
    if (b.quantity === a.quantity &&
        b.returnedQuantity === a.returnedQuantity &&
        b.costPrice === a.costPrice &&
        b.totalCost === a.totalCost &&
        b.status === a.status &&
        b.remainingBalance === a.remainingBalance) {
        console.log(`[IdempotencyGuard] StockEntry ${event.params.entryId} update had no domain changes. Skipping execution.`);
        return;
    }
    console.time("onStockEntryUpdated_executionTime");
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("onStockEntryUpdated_executionTime");
});
exports.onStockEntryDeleted = (0, firestore_1.onDocumentDeleted)("stock_entries/{entryId}", async (event) => {
    if (!event.data)
        return;
    console.time("onStockEntryDeleted_executionTime");
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("onStockEntryDeleted_executionTime");
});
//# sourceMappingURL=stockEntries.js.map