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
exports.onTransactionDeleted = exports.onTransactionUpdated = exports.onTransactionCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = __importStar(require("firebase-admin"));
const dashboardHub_1 = require("../aggregators/dashboardHub");
const idempotency_1 = require("../utils/idempotency");
function getTransactionDelta(data) {
    if (!data)
        return 0;
    const type = (data.type || "").toUpperCase();
    const amount = Number(data.amount || 0);
    if (type === "INCOME" || type === "PAYMENT") {
        return amount;
    }
    else if (type === "EXPENSE" || type === "REFUND") {
        return -amount;
    }
    return 0;
}
/**
 * Checks if a warehouse has pending or unstable invoices
 */
async function checkWarehouseUnstableInvoices(warehouseId) {
    const db = admin.firestore();
    const snap = await db
        .collection("invoices")
        .where("warehouseId", "==", warehouseId)
        .where("status", "in", ["PENDING_SYNC", "pending_sync", "PENDING", "pending"])
        .limit(1)
        .get();
    return !snap.empty;
}
exports.onTransactionCreated = (0, firestore_1.onDocumentCreated)("transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const transactionId = event.params.transactionId;
    const eventId = event.id;
    const data = event.data.data();
    if (!data)
        return;
    await (0, idempotency_1.runWithIdempotencyGuard)(eventId, `transaction_created_${transactionId}`, async (transaction) => {
        const delta = getTransactionDelta(data);
        const whId = data.warehouseId;
        const isDailyTransfer = (data.description || "").includes("ترحيل") || data.isDailyTransfer === true;
        // Server Gateway Lock on daily cash transfers
        if (isDailyTransfer && whId && whId !== "main" && whId !== "MAIN") {
            const hasUnstable = await checkWarehouseUnstableInvoices(whId);
            if (hasUnstable) {
                const db = admin.firestore();
                const txRef = db.collection("transactions").doc(transactionId);
                transaction.set(txRef, {
                    status: "BLOCKED",
                    error: "SERVER_GATEWAY_LOCK",
                    rejectionReason: "يُحظر الترحيل! توجد فواتير معلقة لدى أجهزة البائعين (SERVER_GATEWAY_LOCK).",
                }, { merge: true });
                throw new Error(`SERVER_GATEWAY_LOCK: Cash transfer transaction ${transactionId} blocked due to unstable invoices in warehouse ${whId}.`);
            }
        }
        const subDeltas = {};
        if (whId && whId !== "main" && whId !== "MAIN") {
            subDeltas[whId] = delta;
        }
        await (0, dashboardHub_1.applyDashboardHubDelta)({
            cashDelta: delta,
            mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? delta : 0,
            subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
            updatedDomains: ["financial"],
        });
    });
});
exports.onTransactionUpdated = (0, firestore_1.onDocumentUpdated)("transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const transactionId = event.params.transactionId;
    const eventId = event.id;
    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();
    if (!beforeData || !afterData)
        return;
    const beforeDelta = getTransactionDelta(beforeData);
    const afterDelta = getTransactionDelta(afterData);
    const netDelta = afterDelta - beforeDelta;
    if (Math.abs(netDelta) < 0.0001)
        return;
    await (0, idempotency_1.runWithIdempotencyGuard)(eventId, `transaction_updated_${transactionId}`, async () => {
        const whId = afterData.warehouseId;
        const subDeltas = {};
        if (whId && whId !== "main" && whId !== "MAIN") {
            subDeltas[whId] = netDelta;
        }
        await (0, dashboardHub_1.applyDashboardHubDelta)({
            cashDelta: netDelta,
            mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? netDelta : 0,
            subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
            updatedDomains: ["financial"],
        });
    });
});
exports.onTransactionDeleted = (0, firestore_1.onDocumentDeleted)("transactions/{transactionId}", async (event) => {
    if (!event.data)
        return;
    const transactionId = event.params.transactionId;
    const eventId = event.id;
    const data = event.data.data();
    if (!data)
        return;
    await (0, idempotency_1.runWithIdempotencyGuard)(eventId, `transaction_deleted_${transactionId}`, async () => {
        const delta = -getTransactionDelta(data);
        const whId = data.warehouseId;
        const subDeltas = {};
        if (whId && whId !== "main" && whId !== "MAIN") {
            subDeltas[whId] = delta;
        }
        await (0, dashboardHub_1.applyDashboardHubDelta)({
            cashDelta: delta,
            mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? delta : 0,
            subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
            updatedDomains: ["financial"],
        });
    });
});
//# sourceMappingURL=transactions.js.map