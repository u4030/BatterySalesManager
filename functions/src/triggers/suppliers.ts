import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

export const onSupplierCreated = onDocumentCreated(
  "suppliers/{supplierId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["suppliers"],
    });
  }
);

export const onSupplierUpdated = onDocumentUpdated(
  "suppliers/{supplierId}",
  async (event) => {
    if (!event.data) return;
    const b = event.data.before.data() || {};
    const a = event.data.after.data() || {};

    if (
      b.name === a.name &&
      b.currentBalance === a.currentBalance &&
      b.totalDebit === a.totalDebit &&
      b.totalCredit === a.totalCredit &&
      b.unallocatedCredit === a.unallocatedCredit
    ) {
      console.log(`[IdempotencyGuard] Supplier ${event.params.supplierId} update had no domain changes. Skipping execution.`);
      return;
    }

    await applyDashboardHubDelta({
      updatedDomains: ["suppliers"],
    });
  }
);

export const onSupplierDeleted = onDocumentDeleted(
  "suppliers/{supplierId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["suppliers"],
    });
  }
);
