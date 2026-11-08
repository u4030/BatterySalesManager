import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { runWithIdempotencyGuard } from "../utils/idempotency";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

/**
 * Validates whether a warehouse has any pending or unstable invoices.
 * If pending invoices exist, throws or returns false.
 */
async function checkUnstableInvoices(warehouseId: string): Promise<boolean> {
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
async function validateFifoRollingDailyOrder(
  warehouseId: string,
  targetDateStr: string
): Promise<void> {
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
        throw new Error(
          `[FIFO_ROLLING_VIOLATION] Cannot settle date ${targetDateStr} for warehouse ${warehouseId} before earlier unsettled day: ${docDateStr}.`
        );
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
export const onVaultTransferCreated = onDocumentCreated(
  "vault_transfers/{transferId}",
  async (event) => {
    if (!event.data) return;
    const transferId = event.params.transferId;
    const eventId = event.id;
    const data = event.data.data();

    if (!data) return;

    await runWithIdempotencyGuard(eventId, `vault_transfer_${transferId}`, async (transaction) => {
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

      // Read Phase: Fetch warehouse details synchronously to retrieve Arabic warehouse name with scoped block variable
      const whSnap = await transaction.get(db.collection("warehouses").doc(warehouseId));
      const correctWarehouseName = whSnap.exists ? (whSnap.data()?.name || "مستودع") : "مستودع غير معرف";

      // 1. Server Gateway Lock Check
      const hasUnstable = await checkUnstableInvoices(warehouseId);
      if (hasUnstable) {
        console.error(`[SERVER_GATEWAY_LOCK] Warehouse ${warehouseId} has pending/unstable invoices. Aborting transfer ${transferId}.`);
        transaction.set(
          transferRef,
          {
            status: "REJECTED",
            error: "SERVER_GATEWAY_LOCK",
            rejectionReason: "توجد فواتير معلقة لدى أجهزة البائعين لم تتم مزامنتها بعد (SERVER_GATEWAY_LOCK).",
            rejectedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
        throw new Error(`SERVER_GATEWAY_LOCK: Transfer ${transferId} aborted due to pending invoices in warehouse ${warehouseId}.`);
      }

      // 2. FIFO Rolling Check
      try {
        await validateFifoRollingDailyOrder(warehouseId, dateStr);
      } catch (fifoErr: any) {
        console.error(`[FIFO_VIOLATION] ${fifoErr.message}`);
        transaction.set(
          transferRef,
          {
            status: "REJECTED",
            error: "FIFO_ROLLING_VIOLATION",
            rejectionReason: fifoErr.message,
            rejectedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
        throw fifoErr;
      }

      // 3. Write Phase: Update daily document and warehouse balances atomically
      const dailyDocRef = db.doc(`summaries/daily_${warehouseId}_${dateStr}`);
      const financialStatusRef = db.doc("summaries/financial_status");
      const now = FieldValue.serverTimestamp();

      transaction.set(
        dailyDocRef,
        {
          warehouseId: warehouseId,
          warehouse_id: warehouseId,
          warehouseName: correctWarehouseName,
          warehouse_name: correctWarehouseName,
          isSettled: true,
          settledAt: now,
          unsettledCashBalance: 0,
          transferredAmount: FieldValue.increment(amount),
          lastUpdated: now,
        },
        { merge: true }
      );

      transaction.set(
        financialStatusRef,
        {
          warehouseBalances: {
            [warehouseId]: {
              cashBalance: FieldValue.increment(-amount),
            },
            [targetMainId]: {
              cashBalance: FieldValue.increment(amount),
            },
          },
          version: FieldValue.increment(1),
          lastUpdated: now,
        },
        { merge: true }
      );

      transaction.set(
        transferRef,
        {
          status: "CONFIRMED",
          confirmedAt: now,
        },
        { merge: true }
      );
    });

    const amount = Number(data.amount || 0);
    const warehouseId = data.warehouseId || data.sourceWarehouseId;
    if (warehouseId && amount > 0) {
      await applyDashboardHubDelta({
        subWarehouseDeltas: {
          [warehouseId]: -amount,
        },
        mainTreasuryDelta: amount,
        updatedDomains: ["financial"],
      });
    }
  }
);

/**
 * Callable HTTPS endpoint for executing a secure vault transfer
 * Enforces Firebase App Check (`enforceAppCheck: true`)
 */
export const executeVaultDeposition = onCall(
  {
    enforceAppCheck: true,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "يجب تسجيل الدخول لإجراء ترحيل الصندوق.");
    }

    const { warehouseId, amount, dateStr, targetWarehouseId } = request.data || {};
    if (!warehouseId || !amount || Number(amount) <= 0) {
      throw new HttpsError("invalid-argument", "المعاملات المدخلة غير صحيحة.");
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
      throw new HttpsError(
        "failed-precondition",
        "SERVER_GATEWAY_LOCK: يُحظر الترحيل! توجد فواتير معلقة لدى أجهزة البائعين في هذا المستودع."
      );
    }

    // 2. FIFO Rolling inspection
    try {
      await validateFifoRollingDailyOrder(warehouseId, targetDate);
    } catch (fifoErr: any) {
      throw new HttpsError("failed-precondition", fifoErr.message);
    }

    const db = admin.firestore();
    const transferRef = db.collection("vault_transfers").doc();
    const now = FieldValue.serverTimestamp();

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
  }
);
