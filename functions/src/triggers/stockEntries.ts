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
    console.time("onStockEntryCreated_executionTime");
    console.time("applyDashboardHubDelta");
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("applyDashboardHubDelta");
    console.timeEnd("onStockEntryCreated_executionTime");
  }
);

export const onStockEntryUpdated = onDocumentUpdated(
  "stock_entries/{entryId}",
  async (event) => {
    if (!event.data) return;
    console.time("onStockEntryUpdated_executionTime");
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("onStockEntryUpdated_executionTime");
  }
);

export const onStockEntryDeleted = onDocumentDeleted(
  "stock_entries/{entryId}",
  async (event) => {
    if (!event.data) return;
    console.time("onStockEntryDeleted_executionTime");
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "suppliers", "financial"],
    });
    console.timeEnd("onStockEntryDeleted_executionTime");
  }
);
