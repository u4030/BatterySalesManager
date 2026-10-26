import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { applyDashboardHubDelta } from "../aggregators/dashboardHub";

export const onWarehouseCreated = onDocumentCreated(
  "warehouses/{warehouseId}",
  async (event) => {
    if (!event.data) return;
    const db = admin.firestore();
    const whId = event.params.warehouseId;
    const warehouse = event.data.data();

    if (!warehouse) return;

    try {
      const batch = db.batch();
      const scrapDocRef = db.collection("scrap_warehouses").doc(`scrap_wh_${whId}`);

      batch.set(scrapDocRef, {
        id: scrapDocRef.id,
        name: `سكراب - ${warehouse.name || whId}`,
        parentWarehouseId: whId,
        totalQuantity: 0,
        totalAmperes: 0,
      }, { merge: true });

      await batch.commit();
    } catch (error) {
      console.error("Error creating scrap_warehouse in onWarehouseCreated:", error);
    }

    await applyDashboardHubDelta({
      subWarehouseDeltas: { [whId]: 0 },
      updatedDomains: ["inventory", "financial"],
    });
  }
);

export const onWarehouseUpdated = onDocumentUpdated(
  "warehouses/{warehouseId}",
  async (event) => {
    if (!event.data) return;
    const b = event.data.before.data() || {};
    const a = event.data.after.data() || {};

    if (b.name === a.name && b.isActive === a.isActive && b.location === a.location) {
      console.log(`[IdempotencyGuard] Warehouse ${event.params.warehouseId} update had no domain changes. Skipping execution.`);
      return;
    }

    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "financial"],
    });
  }
);

export const onWarehouseDeleted = onDocumentDeleted(
  "warehouses/{warehouseId}",
  async (event) => {
    if (!event.data) return;
    await applyDashboardHubDelta({
      updatedDomains: ["inventory", "financial"],
    });
  }
);
