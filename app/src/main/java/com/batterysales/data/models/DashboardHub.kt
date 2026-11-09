package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class FinancialStatusSummary(
    @get:PropertyName("totalCashBalance") @set:PropertyName("totalCashBalance") var totalCashBalance: Double = 0.0,
    @get:PropertyName("mainTreasuryBalance") @set:PropertyName("mainTreasuryBalance") var mainTreasuryBalance: Double = 0.0,
    @get:PropertyName("subWarehouseBalances") @set:PropertyName("subWarehouseBalances") var subWarehouseBalances: Map<String, Double> = emptyMap(),
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
)

data class BankSummary(
    @get:PropertyName("globalBankBalance") @set:PropertyName("globalBankBalance") var globalBankBalance: Double = 0.0,
    @get:PropertyName("totalUnpaidChecks") @set:PropertyName("totalUnpaidChecks") var totalUnpaidChecks: Double = 0.0,
    @get:PropertyName("totalUnpaidBills") @set:PropertyName("totalUnpaidBills") var totalUnpaidBills: Double = 0.0,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
)

data class SupplierSummary(
    @get:PropertyName("totalSupplierDebt") @set:PropertyName("totalSupplierDebt") var totalSupplierDebt: Double = 0.0,
    @get:PropertyName("totalSuppliersCount") @set:PropertyName("totalSuppliersCount") var totalSuppliersCount: Int = 0,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
)

data class InventorySummaryData(
    @get:PropertyName("totalValue") @set:PropertyName("totalValue") var totalValue: Double = 0.0,
    @get:PropertyName("totalItemsCount") @set:PropertyName("totalItemsCount") var totalItemsCount: Int = 0,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
)

data class ScrapAndAlertsSummary(
    @get:PropertyName("totalScrapAmperes") @set:PropertyName("totalScrapAmperes") var totalScrapAmperes: Double = 0.0,
    @get:PropertyName("totalScrapValue") @set:PropertyName("totalScrapValue") var totalScrapValue: Double = 0.0,
    @get:PropertyName("activeLowStockAlertsCount") @set:PropertyName("activeLowStockAlertsCount") var activeLowStockAlertsCount: Int = 0,
    @get:PropertyName("active_low_stock_alerts_count") @set:PropertyName("active_low_stock_alerts_count") var activeLowStockAlertsCountSnake: Int = 0,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
) {
    val resolvedAlertsCount: Int
        get() = if (activeLowStockAlertsCount != 0) activeLowStockAlertsCount else activeLowStockAlertsCountSnake
}

data class DashboardHub(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "dashboard_hub",
    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated") var lastUpdated: Date = Date(),
    @get:PropertyName("last_updated") @set:PropertyName("last_updated") var lastUpdatedSnake: Date? = null,
    @get:PropertyName("version") @set:PropertyName("version") var version: Long = 0,
    @get:PropertyName("financialStatus") @set:PropertyName("financialStatus") var financialStatus: FinancialStatusSummary = FinancialStatusSummary(),
    @get:PropertyName("bankSummary") @set:PropertyName("bankSummary") var bankSummary: BankSummary = BankSummary(),
    @get:PropertyName("supplierSummary") @set:PropertyName("supplierSummary") var supplierSummary: SupplierSummary = SupplierSummary(),
    @get:PropertyName("inventorySummary") @set:PropertyName("inventorySummary") var inventorySummary: InventorySummaryData = InventorySummaryData(),
    @get:PropertyName("scrapAndAlertsSummary") @set:PropertyName("scrapAndAlertsSummary") var scrapAndAlertsSummary: ScrapAndAlertsSummary = ScrapAndAlertsSummary(),
    @get:PropertyName("syncRegistry") @set:PropertyName("syncRegistry") var syncRegistry: SyncRegistry = SyncRegistry()
)
