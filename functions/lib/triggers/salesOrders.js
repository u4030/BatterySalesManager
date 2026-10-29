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
exports.processSalesOrder = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = __importStar(require("firebase-admin"));
const firestore_2 = require("firebase-admin/firestore");
exports.processSalesOrder = (0, firestore_1.onDocumentCreated)("sales_orders/{orderId}", async (event) => {
    const snapshot = event.data;
    if (!snapshot)
        return;
    const db = admin.firestore();
    const orderId = event.params.orderId;
    const orderData = snapshot.data();
    if (!orderData)
        return;
    const txRef = db.collection("processed_transactions").doc(orderId);
    try {
        await db.runTransaction(async (transaction) => {
            const txSnap = await transaction.get(txRef);
            if (txSnap.exists)
                return; // Idempotency Guardrail check
            transaction.set(txRef, { processedAt: firestore_2.FieldValue.serverTimestamp() });
            // Zero Pre-Fetch Reads atomic stock deduction
            if (Array.isArray(orderData.items)) {
                for (const item of orderData.items) {
                    if (item.variantId && item.warehouseId && item.quantity) {
                        const stockRef = db.collection("stocks").doc(`${item.variantId}_${item.warehouseId}`);
                        transaction.set(stockRef, {
                            quantity: firestore_2.FieldValue.increment(-Number(item.quantity))
                        }, { merge: true });
                    }
                }
            }
            // Read-Model update for Customer Sales Summary
            if (orderData.customerId) {
                const customerSummaryRef = db.collection("customer_summaries").doc(orderData.customerId);
                transaction.set(customerSummaryRef, {
                    lastOrderDate: orderData.timestamp || firestore_2.FieldValue.serverTimestamp(),
                    totalSalesToDate: firestore_2.FieldValue.increment(Number(orderData.totalAmount || 0)),
                    recentInvoicesSummary: firestore_2.FieldValue.arrayUnion({
                        orderId: orderId,
                        total: Number(orderData.totalAmount || 0),
                        status: "COMPLETED"
                    })
                }, { merge: true });
            }
            transaction.update(snapshot.ref, { status: "COMPLETED" });
        });
    }
    catch (error) {
        console.error(`Transaction failed for order ${orderId}:`, error);
        await snapshot.ref.update({ status: "FAILED", error: error.message });
    }
});
//# sourceMappingURL=salesOrders.js.map