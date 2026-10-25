import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

function getBillUnpaidDetails(data: any): { unpaidChecks: number; unpaidBills: number; totalAmount: number } {
  if (!data) return { unpaidChecks: 0, unpaidBills: 0, totalAmount: 0 };

  const status = (data.status || "").toUpperCase();
  const type = (data.billType || "").toUpperCase();
  const amount = Number(data.amount || 0);
  const paidAmount = Number(data.paidAmount || 0);
  const remaining = Math.max(0, amount - paidAmount);

  let unpaidChecks = 0;
  let unpaidBills = 0;

  if (status !== "PAID" && status !== "CANCELLED") {
    if (type === "CHECK") {
      unpaidChecks = remaining;
    } else {
      unpaidBills = remaining;
    }
  }

  return { unpaidChecks, unpaidBills, totalAmount: amount };
}

export const onBillCreated = onDocumentCreated(
  "bills/{billId}",
  async (event) => {
    if (!event.data) return;
    const details = getBillUnpaidDetails(event.data.data());

    await applyDashboardHubDelta({
      unpaidChecksDelta: details.unpaidChecks,
      unpaidBillsDelta: details.unpaidBills,
      supplierDebtDelta: details.totalAmount,
      updatedDomains: ["financial", "suppliers"],
    });
  }
);

export const onBillUpdated = onDocumentUpdated(
  "bills/{billId}",
  async (event) => {
    if (!event.data) return;
    const beforeDetails = getBillUnpaidDetails(event.data.before.data());
    const afterDetails = getBillUnpaidDetails(event.data.after.data());

    await applyDashboardHubDelta({
      unpaidChecksDelta: afterDetails.unpaidChecks - beforeDetails.unpaidChecks,
      unpaidBillsDelta: afterDetails.unpaidBills - beforeDetails.unpaidBills,
      supplierDebtDelta: afterDetails.totalAmount - beforeDetails.totalAmount,
      updatedDomains: ["financial", "suppliers"],
    });
  }
);

export const onBillDeleted = onDocumentDeleted(
  "bills/{billId}",
  async (event) => {
    if (!event.data) return;
    const details = getBillUnpaidDetails(event.data.data());

    await applyDashboardHubDelta({
      unpaidChecksDelta: -details.unpaidChecks,
      unpaidBillsDelta: -details.unpaidBills,
      supplierDebtDelta: -details.totalAmount,
      updatedDomains: ["financial", "suppliers"],
    });
  }
);
