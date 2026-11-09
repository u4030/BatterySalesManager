import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";
import { runWithIdempotencyGuard } from "../utils/idempotency";

function getTransactionDelta(data: any): number {
  if (!data) return 0;
  const type = (data.type || "").toUpperCase();
  const amount = Number(data.amount || 0);

  if (type === "INCOME" || type === "PAYMENT") {
    return amount;
  } else if (type === "EXPENSE" || type === "REFUND") {
    return -amount;
  }
  return 0;
}

/**
 * Checks if a warehouse has pending or unstable invoices
 */
async function checkWarehouseUnstableInvoices(warehouseId: string): Promise<boolean> {
  const db = admin.firestore();
  const snap = await db
    .collection("invoices")
    .where("warehouseId", "==", warehouseId)
    .where("status", "in", ["PENDING_SYNC", "pending_sync", "PENDING", "pending"])
    .limit(1)
    .get();

  return !snap.empty;
}

export const onTransactionCreated = onDocumentCreated(
  "transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const transactionId = event.params.transactionId;
    const eventId = event.id;
    const data = event.data.data();
    if (!data) return;

    await runWithIdempotencyGuard(eventId, `transaction_created_${transactionId}`, async (transaction) => {
      const delta = getTransactionDelta(data);
      const whId = data.warehouseId as string | undefined;
      const isDailyTransfer = (data.description || "").includes("ترحيل") || data.isDailyTransfer === true;

      // Server Gateway Lock on daily cash transfers
      if (isDailyTransfer && whId && whId !== "main" && whId !== "MAIN") {
        const hasUnstable = await checkWarehouseUnstableInvoices(whId);
        if (hasUnstable) {
          const db = admin.firestore();
          const txRef = db.collection("transactions").doc(transactionId);
          transaction.set(
            txRef,
            {
              status: "BLOCKED",
              error: "SERVER_GATEWAY_LOCK",
              rejectionReason: "يُحظر الترحيل! توجد فواتير معلقة لدى أجهزة البائعين (SERVER_GATEWAY_LOCK).",
            },
            { merge: true }
          );
          throw new Error(`SERVER_GATEWAY_LOCK: Cash transfer transaction ${transactionId} blocked due to unstable invoices in warehouse ${whId}.`);
        }
      }

      const subDeltas: Record<string, number> = {};
      if (whId && whId !== "main" && whId !== "MAIN") {
        subDeltas[whId] = delta;
      }

      await applyDashboardHubDelta({
        cashDelta: delta,
        mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? delta : 0,
        subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
        updatedDomains: ["financial"],
      });
    });
  }
);

export const onTransactionUpdated = onDocumentUpdated(
  "transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const transactionId = event.params.transactionId;
    const eventId = event.id;
    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    if (!beforeData || !afterData) return;

    const beforeDelta = getTransactionDelta(beforeData);
    const afterDelta = getTransactionDelta(afterData);
    const netDelta = afterDelta - beforeDelta;

    if (Math.abs(netDelta) < 0.0001) return;

    await runWithIdempotencyGuard(eventId, `transaction_updated_${transactionId}`, async () => {
      const whId = afterData.warehouseId as string | undefined;
      const subDeltas: Record<string, number> = {};
      if (whId && whId !== "main" && whId !== "MAIN") {
        subDeltas[whId] = netDelta;
      }

      await applyDashboardHubDelta({
        cashDelta: netDelta,
        mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? netDelta : 0,
        subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
        updatedDomains: ["financial"],
      });
    });
  }
);

export const onTransactionDeleted = onDocumentDeleted(
  "transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const transactionId = event.params.transactionId;
    const eventId = event.id;
    const data = event.data.data();
    if (!data) return;

    await runWithIdempotencyGuard(eventId, `transaction_deleted_${transactionId}`, async () => {
      const delta = -getTransactionDelta(data);
      const whId = data.warehouseId as string | undefined;

      const subDeltas: Record<string, number> = {};
      if (whId && whId !== "main" && whId !== "MAIN") {
        subDeltas[whId] = delta;
      }

      await applyDashboardHubDelta({
        cashDelta: delta,
        mainTreasuryDelta: (!whId || whId === "main" || whId === "MAIN") ? delta : 0,
        subWarehouseDeltas: Object.keys(subDeltas).length > 0 ? subDeltas : undefined,
        updatedDomains: ["financial"],
      });
    });
  }
);
