import { Timestamp } from "firebase-admin/firestore";

export interface SyncRegistry {
  id: string;
  inventoryVersion: number;
  suppliersVersion: number;
  financialVersion: number;
  alertsVersion: number;
  lastModified: Timestamp;
}

export interface FinancialStatusSummary {
  totalCashBalance: number;
  mainTreasuryBalance: number;
  subWarehouseBalances: Record<string, number>; // warehouseId -> balance
  updatedAt: Timestamp;
}

export interface BankSummary {
  globalBankBalance: number;
  totalUnpaidChecks: number;
  totalUnpaidBills: number;
  updatedAt: Timestamp;
}

export interface SupplierSummary {
  totalSupplierDebt: number;
  totalSuppliersCount: number;
  updatedAt: Timestamp;
}

export interface InventorySummary {
  totalValue: number;
  totalItemsCount: number;
  updatedAt: Timestamp;
}

export interface ScrapAndAlertsSummary {
  totalScrapAmperes: number;
  totalScrapValue: number;
  activeLowStockAlertsCount: number;
  updatedAt: Timestamp;
}

export interface DashboardHub {
  id: string;
  lastUpdated: Timestamp;
  financialStatus: FinancialStatusSummary;
  bankSummary: BankSummary;
  supplierSummary: SupplierSummary;
  inventorySummary: InventorySummary;
  scrapAndAlertsSummary: ScrapAndAlertsSummary;
  syncRegistry: SyncRegistry;
}
