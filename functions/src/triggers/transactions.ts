import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

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

export const onTransactionCreated = onDocumentCreated(
  "transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const data = event.data.data();
    const delta = getTransactionDelta(data);
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
  }
);

export const onTransactionUpdated = onDocumentUpdated(
  "transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const beforeData = event.data.before.data();
    const afterData = event.data.after.data();

    const beforeDelta = getTransactionDelta(beforeData);
    const afterDelta = getTransactionDelta(afterData);
    const netDelta = afterDelta - beforeDelta;

    if (Math.abs(netDelta) < 0.0001) return;

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
  }
);

export const onTransactionDeleted = onDocumentDeleted(
  "transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const data = event.data.data();
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
  }
);
