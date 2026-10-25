import * as admin from "firebase-admin";
import { Timestamp, FieldValue } from "firebase-admin/firestore";
import { DashboardHub, SyncRegistry } from "../types";

/**
 * Ensures /summaries/dashboard_hub exists with default structure.
 */
export async function ensureDashboardHubExists(): Promise<void> {
  const db = admin.firestore();
  const hubRef = db.doc("summaries/dashboard_hub");
  const snap = await hubRef.get();

  if (!snap.exists) {
    const now = Timestamp.now();
    const defaultSync: SyncRegistry = {
      id: "sync_registry",
      inventoryVersion: 1,
      suppliersVersion: 1,
      financialVersion: 1,
      alertsVersion: 1,
      lastModified: now,
    };

    const defaultHub: DashboardHub = {
      id: "dashboard_hub",
      lastUpdated: now,
      financialStatus: {
        totalCashBalance: 0,
        mainTreasuryBalance: 0,
        subWarehouseBalances: {},
        updatedAt: now,
      },
      bankSummary: {
        globalBankBalance: 0,
        totalUnpaidChecks: 0,
        totalUnpaidBills: 0,
        updatedAt: now,
      },
      supplierSummary: {
        totalSupplierDebt: 0,
        totalSuppliersCount: 0,
        updatedAt: now,
      },
      inventorySummary: {
        totalValue: 0,
        totalItemsCount: 0,
        updatedAt: now,
      },
      scrapAndAlertsSummary: {
        totalScrapAmperes: 0,
        totalScrapValue: 0,
        activeLowStockAlertsCount: 0,
        updatedAt: now,
      },
      syncRegistry: defaultSync,
    };

    const batch = db.batch();
    batch.set(hubRef, defaultHub, { merge: true });
    batch.set(db.doc("summaries/sync_registry"), defaultSync, { merge: true });
    await batch.commit();
  }
}

/**
 * Incrementally applies financial, inventory, supplier, or alert deltas
 * to /summaries/dashboard_hub and /summaries/sync_registry without reading full collections.
 */
export async function applyDashboardHubDelta(params: {
  cashDelta?: number;
  mainTreasuryDelta?: number;
  subWarehouseDeltas?: Record<string, number>;
  bankDelta?: number;
  unpaidChecksDelta?: number;
  unpaidBillsDelta?: number;
  supplierDebtDelta?: number;
  inventoryValueDelta?: number;
  inventoryCountDelta?: number;
  scrapAmperesDelta?: number;
  scrapValueDelta?: number;
  activeAlertsDelta?: number;
  updatedDomains?: Array<"inventory" | "suppliers" | "financial" | "alerts">;
}): Promise<void> {
  const db = admin.firestore();
  await ensureDashboardHubExists();

  const now = Timestamp.now();
  const hubRef = db.doc("summaries/dashboard_hub");
  const syncRef = db.doc("summaries/sync_registry");

  const updates: Record<string, any> = {
    lastUpdated: now,
    "syncRegistry.lastModified": now,
  };

  const syncUpdates: Record<string, any> = {
    lastModified: now,
  };

  const domains = params.updatedDomains || [];
  if (domains.includes("inventory")) {
    updates["syncRegistry.inventoryVersion"] = FieldValue.increment(1);
    syncUpdates["inventoryVersion"] = FieldValue.increment(1);
  }
  if (domains.includes("suppliers")) {
    updates["syncRegistry.suppliersVersion"] = FieldValue.increment(1);
    syncUpdates["suppliersVersion"] = FieldValue.increment(1);
  }
  if (domains.includes("financial")) {
    updates["syncRegistry.financialVersion"] = FieldValue.increment(1);
    syncUpdates["financialVersion"] = FieldValue.increment(1);
  }
  if (domains.includes("alerts")) {
    updates["syncRegistry.alertsVersion"] = FieldValue.increment(1);
    syncUpdates["alertsVersion"] = FieldValue.increment(1);
  }

  if (params.cashDelta) {
    updates["financialStatus.totalCashBalance"] = FieldValue.increment(params.cashDelta);
    updates["financialStatus.updatedAt"] = now;
  }
  if (params.mainTreasuryDelta) {
    updates["financialStatus.mainTreasuryBalance"] = FieldValue.increment(params.mainTreasuryDelta);
  }
  if (params.subWarehouseDeltas) {
    Object.entries(params.subWarehouseDeltas).forEach(([whId, val]) => {
      updates[`financialStatus.subWarehouseBalances.${whId}`] = FieldValue.increment(val);
    });
  }

  if (params.bankDelta) {
    updates["bankSummary.globalBankBalance"] = FieldValue.increment(params.bankDelta);
    updates["bankSummary.updatedAt"] = now;
  }
  if (params.unpaidChecksDelta) {
    updates["bankSummary.totalUnpaidChecks"] = FieldValue.increment(params.unpaidChecksDelta);
    updates["bankSummary.updatedAt"] = now;
  }
  if (params.unpaidBillsDelta) {
    updates["bankSummary.totalUnpaidBills"] = FieldValue.increment(params.unpaidBillsDelta);
    updates["bankSummary.updatedAt"] = now;
  }

  if (params.supplierDebtDelta) {
    updates["supplierSummary.totalSupplierDebt"] = FieldValue.increment(params.supplierDebtDelta);
    updates["supplierSummary.updatedAt"] = now;
  }

  if (params.inventoryValueDelta) {
    updates["inventorySummary.totalValue"] = FieldValue.increment(params.inventoryValueDelta);
    updates["inventorySummary.updatedAt"] = now;
  }
  if (params.inventoryCountDelta) {
    updates["inventorySummary.totalItemsCount"] = FieldValue.increment(params.inventoryCountDelta);
    updates["inventorySummary.updatedAt"] = now;
  }

  if (params.scrapAmperesDelta) {
    updates["scrapAndAlertsSummary.totalScrapAmperes"] = FieldValue.increment(params.scrapAmperesDelta);
    updates["scrapAndAlertsSummary.updatedAt"] = now;
  }
  if (params.scrapValueDelta) {
    updates["scrapAndAlertsSummary.totalScrapValue"] = FieldValue.increment(params.scrapValueDelta);
    updates["scrapAndAlertsSummary.updatedAt"] = now;
  }
  if (params.activeAlertsDelta) {
    updates["scrapAndAlertsSummary.activeLowStockAlertsCount"] = FieldValue.increment(params.activeAlertsDelta);
    updates["scrapAndAlertsSummary.updatedAt"] = now;
  }

  const batch = db.batch();
  batch.update(hubRef, updates);
  batch.update(syncRef, syncUpdates);
  await batch.commit();
}
