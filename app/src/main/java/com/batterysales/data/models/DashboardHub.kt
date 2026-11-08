package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class FinancialStatusSummary(
    @get:PropertyName("totalCashBalance") @set:PropertyName("totalCashBalance") var totalCashBalance: Double = 0.0,
    @get:PropertyName("mainTreasuryBalance") @set:PropertyName("mainTreasuryBalance") var mainTreasuryBalance: Double = 0.0,
    @get:PropertyName("subWarehouseBalances") @set:PropertyName("subWarehouseBalances") var subWarehouseBalances: Map<String, Double> = emptyMap(),
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date()
)

data class BankSummary(
    @get:PropertyName("globalBankBalance") @set:PropertyName("globalBankBalance") var globalBankBalance: Double = 0.0,
    @get:PropertyName("totalUnpaidChecks") @set:PropertyName("totalUnpaidChecks") var totalUnpaidChecks: Double = 0.0,
    @get:PropertyName("totalUnpaidBills") @set:PropertyName("totalUnpaidBills") var totalUnpaidBills: Double = 0.0,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date()
)

data class SupplierSummary(
    @get:PropertyName("totalSupplierDebt") @set:PropertyName("totalSupplierDebt") var totalSupplierDebt: Double = 0.0,
    @get:PropertyName("totalSuppliersCount") @set:PropertyName("totalSuppliersCount") var totalSuppliersCount: Int = 0,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date()
)

data class InventorySummaryData(
    @get:PropertyName("totalValue") @set:PropertyName("totalValue") var totalValue: Double = 0.0,
    @get:PropertyName("totalItemsCount") @set:PropertyName("totalItemsCount") var totalItemsCount: Int = 0,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date()
)

data class ScrapAndAlertsSummary(
    @get:PropertyName("totalScrapAmperes") @set:PropertyName("totalScrapAmperes") var totalScrapAmperes: Double = 0.0,
    @get:PropertyName("totalScrapValue") @set:PropertyName("totalScrapValue") var totalScrapValue: Double = 0.0,
    @get:PropertyName("activeLowStockAlertsCount") @set:PropertyName("activeLowStockAlertsCount") var activeLowStockAlertsCount: Int = 0,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date()
)

data class DashboardHub(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "dashboard_hub",
    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated") var lastUpdated: Date = Date(),
    @get:PropertyName("financialStatus") @set:PropertyName("financialStatus") var financialStatus: FinancialStatusSummary = FinancialStatusSummary(),
    @get:PropertyName("bankSummary") @set:PropertyName("bankSummary") var bankSummary: BankSummary = BankSummary(),
    @get:PropertyName("supplierSummary") @set:PropertyName("supplierSummary") var supplierSummary: SupplierSummary = SupplierSummary(),
    @get:PropertyName("inventorySummary") @set:PropertyName("inventorySummary") var inventorySummary: InventorySummaryData = InventorySummaryData(),
    @get:PropertyName("scrapAndAlertsSummary") @set:PropertyName("scrapAndAlertsSummary") var scrapAndAlertsSummary: ScrapAndAlertsSummary = ScrapAndAlertsSummary(),
    @get:PropertyName("syncRegistry") @set:PropertyName("syncRegistry") var syncRegistry: SyncRegistry = SyncRegistry()
)
