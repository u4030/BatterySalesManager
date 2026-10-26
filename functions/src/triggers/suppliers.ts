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
