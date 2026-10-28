import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

function getScrapDeltas(data: any): { amperes: number; amount: number } {
  if (!data) return { amperes: 0, amount: 0 };
  const type = (data.type || "").toUpperCase();
  const amperes = Number(data.totalAmperes || 0);
  const amount = Number(data.amount || 0);

  if (type === "INTAKE") {
    return { amperes, amount };
  } else if (type === "SALE") {
    return { amperes: -amperes, amount: -amount };
  }
  return { amperes: 0, amount: 0 };
}

export const onOldBatteryTransactionCreated = onDocumentCreated(
  "old_battery_transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const { amperes, amount } = getScrapDeltas(event.data.data());
    await applyDashboardHubDelta({
      scrapAmperesDelta: amperes,
      scrapValueDelta: amount,
      updatedDomains: ["inventory", "financial"],
    });
  }
);

export const onOldBatteryTransactionUpdated = onDocumentUpdated(
  "old_battery_transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const before = getScrapDeltas(event.data.before.data());
    const after = getScrapDeltas(event.data.after.data());

    await applyDashboardHubDelta({
      scrapAmperesDelta: after.amperes - before.amperes,
      scrapValueDelta: after.amount - before.amount,
      updatedDomains: ["inventory", "financial"],
    });
  }
);

export const onOldBatteryTransactionDeleted = onDocumentDeleted(
  "old_battery_transactions/{transactionId}",
  async (event) => {
    if (!event.data) return;
    const { amperes, amount } = getScrapDeltas(event.data.data());
    await applyDashboardHubDelta({
      scrapAmperesDelta: -amperes,
      scrapValueDelta: -amount,
      updatedDomains: ["inventory", "financial"],
    });
  }
);
