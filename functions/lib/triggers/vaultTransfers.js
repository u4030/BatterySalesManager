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
exports.executeVaultDeposition = exports.onVaultTransferCreated = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const https_1 = require("firebase-functions/v2/https");
const admin = __importStar(require("firebase-admin"));
const firestore_2 = require("firebase-admin/firestore");
const idempotency_1 = require("../utils/idempotency");
const dashboardHub_1 = require("../aggregators/dashboardHub");
/**
 * Validates whether a warehouse has any pending or unstable invoices.
 * If pending invoices exist, throws or returns false.
 */
async function checkUnstableInvoices(warehouseId) {
    const db = admin.firestore();
    // Check for invoices with status PENDING_SYNC or pending
    const pendingSyncSnap = await db
        .collection("invoices")
        .where("warehouseId", "==", warehouseId)
        .where("status", "in", ["PENDING_SYNC", "pending_sync", "PENDING", "pending"])
        .limit(1)
        .get();
    if (!pendingSyncSnap.empty) {
        return true;
    }
    // Also check if any invoice has syncStatus == "pending" or unsynced == true
    const unsyncedSnap = await db
        .collection("invoices")
        .where("warehouseId", "==", warehouseId)
        .where("isPendingSync", "==", true)
        .limit(1)
        .get();
    return !unsyncedSnap.empty;
}
/**
 * Validates FIFO Rolling order for warehouse deposition.
 * Checks daily documents in summaries/daily_${warehouseId}_${dateStr}.
 * Previous unsettled dates cannot be skipped.
 */
async function validateFifoRollingDailyOrder(warehouseId, targetDateStr) {
    const db = admin.firestore();
    // Query summaries matching daily_${warehouseId}_*
    // Document IDs format: daily_${warehouseId}_${YYYY-MM-DD}
    const prefix = `daily_${warehouseId}_`;
    const snapshot = await db
        .collection("summaries")
        .where(admin.firestore.FieldPath.documentId(), ">=", prefix)
        .where(admin.firestore.FieldPath.documentId(), "<", prefix + "\uf8ff")
        .get();
    for (const doc of snapshot.docs) {
        const docDateStr = doc.id.replace(prefix, "");
        const data = doc.data();
        // If there is an earlier date that has not been settled/transferred (e.g., isSettled !== true or unsettledDebt > 0)
        if (docDateStr < targetDateStr) {
            const isSettled = data.isSettled === true || data.settled === true;
            const unsettledCash = Number(data.unsettledCashBalance || data.remainingCashToTransfer || 0);
            if (!isSettled && unsettledCash > 0.001) {
                throw new Error(`[FIFO_ROLLING_VIOLATION] Cannot settle date ${targetDateStr} for warehouse ${warehouseId} before earlier unsettled day: ${docDateStr}.`);
            }
        }
    }
}
/**
 * Firestore trigger on vault_transfers/{transferId}
 * Enforces:
 * 1. Idempotency Guard in 'idempotency_guards'
 * 2. Server Gateway Lock ('SERVER_GATEWAY_LOCK') if pending/unstable invoices exist
 * 3. FIFO Rolling validation against daily_${warehouseId}_${dateStr}
 * 4. Atomic balance adjustment and rollback if locked
 */
exports.onVaultTransferCreated = (0, firestore_1.onDocumentCreated)("vault_transfers/{transferId}", async (event) => {
    if (!event.data)
        return;
    const transferId = event.params.transferId;
    const eventId = event.id;
    const data = event.data.data();
    if (!data)
        return;
    await (0, idempotency_1.runWithIdempotencyGuard)(eventId, `vault_transfer_${transferId}`, async (transaction) => {
        const db = admin.firestore();
        const transferRef = db.collection("vault_transfers").doc(transferId);
        const warehouseId = data.warehouseId || data.sourceWarehouseId;
        const targetMainId = data.targetWarehouseId || "main_treasury";
        const amount = Number(data.amount || 0);
        const dateStr = data.dateStr || new Intl.DateTimeFormat("en-CA", {
            timeZone: "Asia/Amman",
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
        }).format(new Date());
        if (!warehouseId || amount <= 0) {
            console.warn(`[VaultTransfer] Invalid warehouseId or amount for transfer ${transferId}.`);
            return;
        }
        // Read Phase: Fetch warehouse details synchronously to retrieve Arabic warehouse name
        const whSnap = await transaction.get(db.doc(`warehouses/${warehouseId}`));
        const warehouseName = whSnap.exists ? (whSnap.data()?.name || "المستودع") : "المستودع";
        // 1. Server Gateway Lock Check
        const hasUnstable = await checkUnstableInvoices(warehouseId);
        if (hasUnstable) {
            console.error(`[SERVER_GATEWAY_LOCK] Warehouse ${warehouseId} has pending/unstable invoices. Aborting transfer ${transferId}.`);
            transaction.set(transferRef, {
                status: "REJECTED",
                error: "SERVER_GATEWAY_LOCK",
                rejectionReason: "توجد فواتير معلقة لدى أجهزة البائعين لم تتم مزامنتها بعد (SERVER_GATEWAY_LOCK).",
                rejectedAt: firestore_2.FieldValue.serverTimestamp(),
            }, { merge: true });
            throw new Error(`SERVER_GATEWAY_LOCK: Transfer ${transferId} aborted due to pending invoices in warehouse ${warehouseId}.`);
        }
        // 2. FIFO Rolling Check
        try {
            await validateFifoRollingDailyOrder(warehouseId, dateStr);
        }
        catch (fifoErr) {
            console.error(`[FIFO_VIOLATION] ${fifoErr.message}`);
            transaction.set(transferRef, {
                status: "REJECTED",
                error: "FIFO_ROLLING_VIOLATION",
                rejectionReason: fifoErr.message,
                rejectedAt: firestore_2.FieldValue.serverTimestamp(),
            }, { merge: true });
            throw fifoErr;
        }
        // 3. Write Phase: Update daily document and warehouse balances atomically
        const dailyDocRef = db.doc(`summaries/daily_${warehouseId}_${dateStr}`);
        const financialStatusRef = db.doc("summaries/financial_status");
        const now = firestore_2.FieldValue.serverTimestamp();
        transaction.set(dailyDocRef, {
            warehouseId: warehouseId,
            warehouseName: warehouseName,
            isSettled: true,
            settledAt: now,
            unsettledCashBalance: 0,
            transferredAmount: firestore_2.FieldValue.increment(amount),
            lastUpdated: now,
        }, { merge: true });
        transaction.set(financialStatusRef, {
            warehouseBalances: {
                [warehouseId]: {
                    cashBalance: firestore_2.FieldValue.increment(-amount),
                },
                [targetMainId]: {
                    cashBalance: firestore_2.FieldValue.increment(amount),
                },
            },
            version: firestore_2.FieldValue.increment(1),
            lastUpdated: now,
        }, { merge: true });
        transaction.set(transferRef, {
            status: "CONFIRMED",
            confirmedAt: now,
        }, { merge: true });
    });
    const amount = Number(data.amount || 0);
    const warehouseId = data.warehouseId || data.sourceWarehouseId;
    if (warehouseId && amount > 0) {
        await (0, dashboardHub_1.applyDashboardHubDelta)({
            subWarehouseDeltas: {
                [warehouseId]: -amount,
            },
            mainTreasuryDelta: amount,
            updatedDomains: ["financial"],
        });
    }
});
/**
 * Callable HTTPS endpoint for executing a secure vault transfer
 * Enforces Firebase App Check (`enforceAppCheck: true`)
 */
exports.executeVaultDeposition = (0, https_1.onCall)({
    enforceAppCheck: true,
}, async (request) => {
    if (!request.auth) {
        throw new https_1.HttpsError("unauthenticated", "يجب تسجيل الدخول لإجراء ترحيل الصندوق.");
    }
    const { warehouseId, amount, dateStr, targetWarehouseId } = request.data || {};
    if (!warehouseId || !amount || Number(amount) <= 0) {
        throw new https_1.HttpsError("invalid-argument", "المعاملات المدخلة غير صحيحة.");
    }
    const transferAmount = Number(amount);
    const targetDate = dateStr || new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Amman",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    }).format(new Date());
    // 1. Gateway Lock inspection
    const hasUnstable = await checkUnstableInvoices(warehouseId);
    if (hasUnstable) {
        throw new https_1.HttpsError("failed-precondition", "SERVER_GATEWAY_LOCK: يُحظر الترحيل! توجد فواتير معلقة لدى أجهزة البائعين في هذا المستودع.");
    }
    // 2. FIFO Rolling inspection
    try {
        await validateFifoRollingDailyOrder(warehouseId, targetDate);
    }
    catch (fifoErr) {
        throw new https_1.HttpsError("failed-precondition", fifoErr.message);
    }
    const db = admin.firestore();
    const transferRef = db.collection("vault_transfers").doc();
    const now = firestore_2.FieldValue.serverTimestamp();
    await transferRef.set({
        id: transferRef.id,
        warehouseId,
        sourceWarehouseId: warehouseId,
        targetWarehouseId: targetWarehouseId || "main_treasury",
        amount: transferAmount,
        dateStr: targetDate,
        requestedBy: request.auth.uid,
        status: "PENDING",
        createdAt: now,
    });
    return {
        success: true,
        transferId: transferRef.id,
        message: "تم تسجيل حركة ترحيل الصندوق بنجاح.",
    };
});
//# sourceMappingURL=vaultTransfers.js.map