import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

export const onAlertCreated = onDocumentCreated(
  "system_alerts/{alertId}",
  async (event) => {
    if (!event.data) return;
    const isResolved = event.data.data()?.resolved === true;
    await applyDashboardHubDelta({
      activeAlertsDelta: isResolved ? 0 : 1,
      updatedDomains: ["alerts"],
    });
  }
);

export const onAlertUpdated = onDocumentUpdated(
  "system_alerts/{alertId}",
  async (event) => {
    if (!event.data) return;
    const beforeResolved = event.data.before.data()?.resolved === true;
    const afterResolved = event.data.after.data()?.resolved === true;

    let delta = 0;
    if (beforeResolved && !afterResolved) delta = 1;
    if (!beforeResolved && afterResolved) delta = -1;

    if (delta !== 0) {
      await applyDashboardHubDelta({
        activeAlertsDelta: delta,
        updatedDomains: ["alerts"],
      });
    }
  }
);

export const onAlertDeleted = onDocumentDeleted(
  "system_alerts/{alertId}",
  async (event) => {
    if (!event.data) return;
    const wasResolved = event.data.data()?.resolved === true;
    if (!wasResolved) {
      await applyDashboardHubDelta({
        activeAlertsDelta: -1,
        updatedDomains: ["alerts"],
      });
    }
  }
);
