package com.batterysales.data.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class FinancialStatusSummary(
    @get:PropertyName("totalCashBalance") @set:PropertyName("totalCashBalance") var totalCashBalance: Double = 0.0,
    @get:PropertyName("total_cash_balance") @set:PropertyName("total_cash_balance") var totalCashBalanceSnake: Double = 0.0,

    @get:PropertyName("mainTreasuryBalance") @set:PropertyName("mainTreasuryBalance") var mainTreasuryBalance: Double = 0.0,
    @get:PropertyName("main_treasury_balance") @set:PropertyName("main_treasury_balance") var mainTreasuryBalanceSnake: Double = 0.0,

    @get:PropertyName("subWarehouseBalances") @set:PropertyName("subWarehouseBalances") var subWarehouseBalances: Map<String, Double> = emptyMap(),
    @get:PropertyName("sub_warehouse_balances") @set:PropertyName("sub_warehouse_balances") var subWarehouseBalancesSnake: Map<String, Double> = emptyMap(),

    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
)

data class BankSummary(
    @get:PropertyName("globalBankBalance") @set:PropertyName("globalBankBalance") var globalBankBalance: Double = 0.0,
    @get:PropertyName("global_bank_balance") @set:PropertyName("global_bank_balance") var globalBankBalanceSnake: Double = 0.0,

    @get:PropertyName("totalUnpaidChecks") @set:PropertyName("totalUnpaidChecks") var totalUnpaidChecks: Double = 0.0,
    @get:PropertyName("total_unpaid_checks") @set:PropertyName("total_unpaid_checks") var totalUnpaidChecksSnake: Double = 0.0,

    @get:PropertyName("totalUnpaidBills") @set:PropertyName("totalUnpaidBills") var totalUnpaidBills: Double = 0.0,
    @get:PropertyName("total_unpaid_bills") @set:PropertyName("total_unpaid_bills") var totalUnpaidBillsSnake: Double = 0.0,

    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
)

data class SupplierSummary(
    @get:PropertyName("totalSupplierDebt") @set:PropertyName("totalSupplierDebt") var totalSupplierDebt: Double = 0.0,
    @get:PropertyName("total_supplier_debt") @set:PropertyName("total_supplier_debt") var totalSupplierDebtSnake: Double = 0.0,

    @get:PropertyName("totalSuppliersCount") @set:PropertyName("totalSuppliersCount") var totalSuppliersCount: Int = 0,
    @get:PropertyName("total_suppliers_count") @set:PropertyName("total_suppliers_count") var totalSuppliersCountSnake: Int = 0,

    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
)

data class InventorySummaryData(
    @get:PropertyName("totalValue") @set:PropertyName("totalValue") var totalValue: Double = 0.0,
    @get:PropertyName("total_value") @set:PropertyName("total_value") var totalValueSnake: Double = 0.0,

    @get:PropertyName("totalItemsCount") @set:PropertyName("totalItemsCount") var totalItemsCount: Int = 0,
    @get:PropertyName("total_items_count") @set:PropertyName("total_items_count") var totalItemsCountSnake: Int = 0,

    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
)

data class ScrapAndAlertsSummary(
    @get:PropertyName("totalScrapAmperes") @set:PropertyName("totalScrapAmperes") var totalScrapAmperes: Double = 0.0,
    @get:PropertyName("total_scrap_amperes") @set:PropertyName("total_scrap_amperes") var totalScrapAmperesSnake: Double = 0.0,

    @get:PropertyName("totalScrapValue") @set:PropertyName("totalScrapValue") var totalScrapValue: Double = 0.0,
    @get:PropertyName("total_scrap_value") @set:PropertyName("total_scrap_value") var totalScrapValueSnake: Double = 0.0,

    @get:PropertyName("activeLowStockAlertsCount") @set:PropertyName("activeLowStockAlertsCount") var activeLowStockAlertsCount: Int = 0,
    @get:PropertyName("active_low_stock_alerts_count") @set:PropertyName("active_low_stock_alerts_count") var activeLowStockAlertsCountSnake: Int = 0,

    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") var updatedAtSnake: Date? = null
) {
    @get:Exclude
    val resolvedAlertsCount: Int
        get() = if (activeLowStockAlertsCount != 0) activeLowStockAlertsCount else activeLowStockAlertsCountSnake
}

data class DashboardHub(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "dashboard_hub",
    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated") var lastUpdated: Date = Date(),
    @get:PropertyName("last_updated") @set:PropertyName("last_updated") var lastUpdatedSnake: Date? = null,
    @get:PropertyName("version") @set:PropertyName("version") var version: Long = 0,

    @get:PropertyName("financialStatus") @set:PropertyName("financialStatus") var financialStatus: FinancialStatusSummary = FinancialStatusSummary(),
    @get:PropertyName("financial_status") @set:PropertyName("financial_status") var financialStatusSnake: FinancialStatusSummary? = null,

    @get:PropertyName("bankSummary") @set:PropertyName("bankSummary") var bankSummary: BankSummary = BankSummary(),
    @get:PropertyName("bank_summary") @set:PropertyName("bank_summary") var bankSummarySnake: BankSummary? = null,

    @get:PropertyName("supplierSummary") @set:PropertyName("supplierSummary") var supplierSummary: SupplierSummary = SupplierSummary(),
    @get:PropertyName("supplier_summary") @set:PropertyName("supplier_summary") var supplierSummarySnake: SupplierSummary? = null,

    @get:PropertyName("inventorySummary") @set:PropertyName("inventorySummary") var inventorySummary: InventorySummaryData = InventorySummaryData(),
    @get:PropertyName("inventory_summary") @set:PropertyName("inventory_summary") var inventorySummarySnake: InventorySummaryData? = null,

    @get:PropertyName("scrapAndAlertsSummary") @set:PropertyName("scrapAndAlertsSummary") var scrapAndAlertsSummary: ScrapAndAlertsSummary = ScrapAndAlertsSummary(),
    @get:PropertyName("scrap_and_alerts_summary") @set:PropertyName("scrap_and_alerts_summary") var scrapAndAlertsSummarySnake: ScrapAndAlertsSummary? = null,

    @get:PropertyName("syncRegistry") @set:PropertyName("syncRegistry") var syncRegistry: SyncRegistry = SyncRegistry(),
    @get:PropertyName("sync_registry") @set:PropertyName("sync_registry") var syncRegistrySnake: SyncRegistry? = null
)
