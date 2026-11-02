"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.ensureDashboardHubExists = ensureDashboardHubExists;
exports.applyDashboardHubDelta = applyDashboardHubDelta;
const admin = __importStar(require("firebase-admin"));
const firestore_1 = require("firebase-admin/firestore");
/**
 * Ensures /summaries/dashboard_hub exists with default structure.
 */
async function ensureDashboardHubExists() {
    const db = admin.firestore();
    const hubRef = db.doc("summaries/dashboard_hub");
    const snap = await hubRef.get();
    if (!snap.exists) {
        const now = firestore_1.Timestamp.now();
        const defaultSync = {
            id: "sync_registry",
            inventoryVersion: 1,
            suppliersVersion: 1,
            financialVersion: 1,
            alertsVersion: 1,
            lastModified: now,
        };
        const defaultHub = {
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
async function applyDashboardHubDelta(params) {
    const db = admin.firestore();
    await ensureDashboardHubExists();
    const now = firestore_1.Timestamp.now();
    const hubRef = db.doc("summaries/dashboard_hub");
    const syncRef = db.doc("summaries/sync_registry");
    const updates = {
        lastUpdated: now,
        "syncRegistry.lastModified": now,
    };
    const syncUpdates = {
        lastModified: now,
    };
    const domains = params.updatedDomains || [];
    if (domains.includes("inventory")) {
        updates["syncRegistry.inventoryVersion"] = firestore_1.FieldValue.increment(1);
        syncUpdates["inventoryVersion"] = firestore_1.FieldValue.increment(1);
    }
    if (domains.includes("suppliers")) {
        updates["syncRegistry.suppliersVersion"] = firestore_1.FieldValue.increment(1);
        syncUpdates["suppliersVersion"] = firestore_1.FieldValue.increment(1);
    }
    if (domains.includes("financial")) {
        updates["syncRegistry.financialVersion"] = firestore_1.FieldValue.increment(1);
        syncUpdates["financialVersion"] = firestore_1.FieldValue.increment(1);
    }
    if (domains.includes("alerts")) {
        updates["syncRegistry.alertsVersion"] = firestore_1.FieldValue.increment(1);
        syncUpdates["alertsVersion"] = firestore_1.FieldValue.increment(1);
    }
    if (params.cashDelta) {
        updates["financialStatus.totalCashBalance"] = firestore_1.FieldValue.increment(params.cashDelta);
        updates["financialStatus.updatedAt"] = now;
    }
    if (params.mainTreasuryDelta) {
        updates["financialStatus.mainTreasuryBalance"] = firestore_1.FieldValue.increment(params.mainTreasuryDelta);
    }
    if (params.subWarehouseDeltas) {
        Object.entries(params.subWarehouseDeltas).forEach(([whId, val]) => {
            updates[`financialStatus.subWarehouseBalances.${whId}`] = firestore_1.FieldValue.increment(val);
        });
    }
    if (params.bankDelta) {
        updates["bankSummary.globalBankBalance"] = firestore_1.FieldValue.increment(params.bankDelta);
        updates["bankSummary.updatedAt"] = now;
    }
    if (params.unpaidChecksDelta) {
        updates["bankSummary.totalUnpaidChecks"] = firestore_1.FieldValue.increment(params.unpaidChecksDelta);
        updates["bankSummary.updatedAt"] = now;
    }
    if (params.unpaidBillsDelta) {
        updates["bankSummary.totalUnpaidBills"] = firestore_1.FieldValue.increment(params.unpaidBillsDelta);
        updates["bankSummary.updatedAt"] = now;
    }
    if (params.supplierDebtDelta) {
        updates["supplierSummary.totalSupplierDebt"] = firestore_1.FieldValue.increment(params.supplierDebtDelta);
        updates["supplierSummary.updatedAt"] = now;
    }
    if (params.inventoryValueDelta) {
        updates["inventorySummary.totalValue"] = firestore_1.FieldValue.increment(params.inventoryValueDelta);
        updates["inventorySummary.updatedAt"] = now;
    }
    if (params.inventoryCountDelta) {
        updates["inventorySummary.totalItemsCount"] = firestore_1.FieldValue.increment(params.inventoryCountDelta);
        updates["inventorySummary.updatedAt"] = now;
    }
    if (params.scrapAmperesDelta) {
        updates["scrapAndAlertsSummary.totalScrapAmperes"] = firestore_1.FieldValue.increment(params.scrapAmperesDelta);
        updates["scrapAndAlertsSummary.updatedAt"] = now;
    }
    if (params.scrapValueDelta) {
        updates["scrapAndAlertsSummary.totalScrapValue"] = firestore_1.FieldValue.increment(params.scrapValueDelta);
        updates["scrapAndAlertsSummary.updatedAt"] = now;
    }
    if (params.activeAlertsDelta) {
        updates["scrapAndAlertsSummary.activeLowStockAlertsCount"] = firestore_1.FieldValue.increment(params.activeAlertsDelta);
        updates["scrapAndAlertsSummary.updatedAt"] = now;
    }
    const batch = db.batch();
    batch.update(hubRef, updates);
    batch.update(syncRef, syncUpdates);
    await batch.commit();
}
//# sourceMappingURL=dashboardHub.js.map