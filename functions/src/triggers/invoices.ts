import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

export const onInvoiceCreated = onDocumentCreated(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "financial", "alerts"],
    });
  }
);

export const onInvoiceUpdated = onDocumentUpdated(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "financial", "alerts"],
    });
  }
);

export const onInvoiceDeleted = onDocumentDeleted(
  "invoices/{invoiceId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "financial", "alerts"],
    });
  }
);
