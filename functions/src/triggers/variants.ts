import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

export const onVariantCreated = onDocumentCreated(
  "product_variants/{variantId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory"],
    });
  }
);

export const onVariantUpdated = onDocumentUpdated(
  "product_variants/{variantId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory"],
    });
  }
);

export const onVariantDeleted = onDocumentDeleted(
  "product_variants/{variantId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory"],
    });
  }
);
