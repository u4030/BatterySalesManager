import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

export const onStockEntryCreated = onDocumentCreated(
  "stock_entries/{entryId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "suppliers", "financial"],
    });
  }
);

export const onStockEntryUpdated = onDocumentUpdated(
  "stock_entries/{entryId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "suppliers", "financial"],
    });
  }
);

export const onStockEntryDeleted = onDocumentDeleted(
  "stock_entries/{entryId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "suppliers", "financial"],
    });
  }
);
