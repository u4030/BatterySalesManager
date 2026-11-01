import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

function getBankDelta(data: any): number {
  if (!data) return 0;
  const type = (data.type || "").toUpperCase();
  const amount = Number(data.amount || 0);

  if (type === "DEPOSIT" || type === "INCOME") {
    return amount;
  } else if (type === "WITHDRAWAL" || type === "EXPENSE") {
    return -amount;
  }
  return 0;
}

export const onBankTransactionCreated = onDocumentCreated(
  "bank_transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const delta = getBankDelta(event.data.data());
    await applyDashboardHubDelta({
      bankDelta: delta,
      updatedDomains: ["financial"],
    });
  }
);

export const onBankTransactionUpdated = onDocumentUpdated(
  "bank_transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const beforeDelta = getBankDelta(event.data.before.data());
    const afterDelta = getBankDelta(event.data.after.data());
    const netDelta = afterDelta - beforeDelta;

    if (Math.abs(netDelta) < 0.0001) return;

    await applyDashboardHubDelta({
      bankDelta: netDelta,
      updatedDomains: ["financial"],
    });
  }
);

export const onBankTransactionDeleted = onDocumentDeleted(
  "bank_transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const delta = -getBankDelta(event.data.data());
    await applyDashboardHubDelta({
      bankDelta: delta,
      updatedDomains: ["financial"],
    });
  }
);
