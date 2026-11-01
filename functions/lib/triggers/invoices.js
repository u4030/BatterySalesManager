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
exports.onInvoiceDeleted = exports.onInvoiceUpdated = exports.onInvoiceCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = __importStar(require("firebase-admin"));
const firestore_2 = require("firebase-admin/firestore");
const dashboardHub_1 = require("../aggregators/dashboardHub");
const processedEvents = new Set();
exports.onInvoiceCreated = (0, firestore_1.onDocumentCreated)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    const eventId = event.id;
    // Idempotency Guardrail
    if (processedEvents.has(eventId)) {
        console.log(`[IdempotencyGuard] Event ${eventId} for invoice ${event.params.invoiceId} already processed. Skipping.`);
        return;
    }
    processedEvents.add(eventId);
    if (processedEvents.size > 1000) {
        const firstKey = processedEvents.values().next().value;
        if (firstKey)
            processedEvents.delete(firstKey);
    }
    console.time("onInvoiceCreated_executionTime");
    const db = admin.firestore();
    const invoice = event.data.data();
    if (!invoice)
        return;
    const warehouseId = invoice.warehouseId || "";
    const items = (invoice.items || []);
    const paidAmount = Number(invoice.paidAmount || 0);
    const paymentMethod = invoice.paymentMethod || "cash";
    const scrapAmps = Number(invoice.oldBatteriesTotalAmperes || 0);
    const scrapVal = Number(invoice.oldBatteriesValue || 0);
    const isBank = paymentMethod === "bank";
    const now = firestore_2.FieldValue.serverTimestamp();
    const batch = db.batch();
    // 1. Decrement variant stock on product_variants & summaries using FieldValue.increment
    items.forEach((item) => {
        const variantId = item.productId;
        const qty = Number(item.quantity || 0);
        if (variantId && qty > 0) {
            // Decrement variant currentStock map
            if (warehouseId) {
                batch.set(db.doc(`product_variants/${variantId}`), {
                    currentStock: {
                        [warehouseId]: firestore_2.FieldValue.increment(-qty),
                    },
                }, { merge: true });
                // Decrement warehouse summary item
                batch.set(db.doc(`summaries/inventory_wh_${warehouseId}`), {
                    [`items.${variantId}.currentStock`]: firestore_2.FieldValue.increment(-qty),
                    lastUpdated: now,
                    version: firestore_2.FieldValue.increment(1),
                }, { merge: true });
            }
            // Decrement global summary item
            batch.set(db.doc("summaries/inventory_global"), {
                [`items.${variantId}.currentStock`]: firestore_2.FieldValue.increment(-qty),
                lastUpdated: now,
                version: firestore_2.FieldValue.increment(1),
            }, { merge: true });
        }
    });
    // 2. Commit batch
    await batch.commit();
    // 3. Update Dashboard Hub delta
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        cashDelta: !isBank ? paidAmount : 0,
        bankDelta: isBank ? paidAmount : 0,
        scrapAmperesDelta: scrapAmps,
        scrapValueDelta: scrapVal,
        updatedDomains: ["inventory", "financial"],
    });
    console.timeEnd("onInvoiceCreated_executionTime");
});
exports.onInvoiceUpdated = (0, firestore_1.onDocumentUpdated)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    const b = event.data.before.data() || {};
    const a = event.data.after.data() || {};
    if (b.paidAmount === a.paidAmount &&
        b.remainingAmount === a.remainingAmount &&
        b.status === a.status) {
        console.log(`[IdempotencyGuard] Invoice ${event.params.invoiceId} update had no domain changes. Skipping.`);
        return;
    }
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "financial"],
    });
});
exports.onInvoiceDeleted = (0, firestore_1.onDocumentDeleted)("invoices/{invoiceId}", async (event) => {
    if (!event.data)
        return;
    await (0, dashboardHub_1.applyDashboardHubDelta)({
        updatedDomains: ["inventory", "financial"],
    });
});
//# sourceMappingURL=invoices.js.map