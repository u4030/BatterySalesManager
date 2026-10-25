package com.batterysales.data.models

import java.util.Date

data class FinancialStatusSummary(
    val totalCashBalance: Double = 0.0,
    val mainTreasuryBalance: Double = 0.0,
    val subWarehouseBalances: Map<String, Double> = emptyMap(),
    val updatedAt: Date = Date()
)

data class BankSummary(
    val globalBankBalance: Double = 0.0,
    val totalUnpaidChecks: Double = 0.0,
    val totalUnpaidBills: Double = 0.0,
    val updatedAt: Date = Date()
)

data class SupplierSummary(
    val totalSupplierDebt: Double = 0.0,
    val totalSuppliersCount: Int = 0,
    val updatedAt: Date = Date()
)

data class InventorySummaryData(
    val totalValue: Double = 0.0,
    val totalItemsCount: Int = 0,
    val updatedAt: Date = Date()
)

data class ScrapAndAlertsSummary(
    val totalScrapAmperes: Double = 0.0,
    val totalScrapValue: Double = 0.0,
    val activeLowStockAlertsCount: Int = 0,
    val updatedAt: Date = Date()
)

data class DashboardHub(
    val id: String = "dashboard_hub",
    val lastUpdated: Date = Date(),
    val financialStatus: FinancialStatusSummary = FinancialStatusSummary(),
    val bankSummary: BankSummary = BankSummary(),
    val supplierSummary: SupplierSummary = SupplierSummary(),
    val inventorySummary: InventorySummaryData = InventorySummaryData(),
    val scrapAndAlertsSummary: ScrapAndAlertsSummary = ScrapAndAlertsSummary(),
    val syncRegistry: SyncRegistry = SyncRegistry()
)
