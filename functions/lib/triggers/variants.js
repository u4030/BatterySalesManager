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
const dashboardHub_1 = require("../aggregators/dashboardHub");
exports.onVariantWritten = (0, firestore_1.onDocumentWritten)("product_variants/{variantId}", async (event) => {
    if (!event.data)
        return;
    const db = admin.firestore();
    const variantId = event.params.variantId;
    const afterSnap = event.data.after;
    if (!afterSnap.exists) {
        // Document deleted
        await (0, dashboardHub_1.applyDashboardHubDelta)({ updatedDomains: ["inventory"] });
        return;
    }
    const variant = afterSnap.data();
    if (!variant || variant.archived || variant.isDiscontinued) {
        await (0, dashboardHub_1.applyDashboardHubDelta)({ updatedDomains: ["inventory"] });
        return;
    }
    let createdAlertsCount = 0;
    try {
        const warehousesSnap = await db.collection("warehouses").get();
        const warehouseDocs = warehousesSnap.docs;
        let pName = variant.productName || "";
        if (!pName && variant.productId) {
            const productSnap = await db.collection("products").doc(variant.productId).get();
            if (productSnap.exists) {
                pName = productSnap.data()?.name || "منتج";
            }
        }
        if (!pName)
            pName = "منتج";
        const batch = db.batch();
        const currentStockMap = (variant.currentStock || {});
        const minQuantitiesMap = (variant.minQuantities || {});
        const defaultMinQty = Number(variant.minQuantity || 0);
        warehouseDocs.forEach((whDoc) => {
            const whId = whDoc.id;
            const whName = whDoc.data()?.name || "مخزن";
            const currentQty = currentStockMap[whId] ?? 0;
            const threshold = minQuantitiesMap[whId] ?? defaultMinQty;
            if (threshold > 0 && currentQty <= threshold) {
                const alertRef = db.collection("system_alerts").doc(`low_stock_${variantId}_${whId}`);
                const specSuffix = variant.specification ? ` | ${variant.specification}` : "";
                batch.set(alertRef, {
                    id: alertRef.id,
                    type: "low_stock",
                    title: `مخزون منخفض: ${pName}`,
                    message: `${variant.capacity || 0}A${specSuffix} في ${whName} | الكمية: ${currentQty} (الحد: ${threshold})`,
                    relatedId: variantId,
                    warehouseId: whId,
                    warehouseName: whName,
                    resolved: false,
                    timestamp: firestore_2.FieldValue.serverTimestamp(),
                    data: {
                        capacity: variant.capacity || 0,
                        specification: variant.specification || "",
                        currentStock: currentQty,
                        threshold: threshold,
                    },
                }, { merge: true });
                createdAlertsCount += 1;
            }
        });
        if (createdAlertsCount > 0) {
            // Also update sync_registry last_alert_update
            const now = firestore_2.FieldValue.serverTimestamp();
            batch.set(db.doc("summaries/sync_registry"), {
                last_alert_update: now,
                lastModified: now,
            }, { merge: true });
            batch.set(db.doc("summaries/dashboard_hub"), {
                "syncRegistry.last_alert_update": now,
                lastUpdated: now,
            }, { merge: true });
            await batch.commit();
        }
    }
    catch (error) {
        console.error("Error updating low_stock alerts in onVariantWritten:", error);
    }
    // Active alert count is accurately updated by alerts.ts trigger when system_alerts are created
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "alerts"],
    });
});
//# sourceMappingURL=variants.js.map