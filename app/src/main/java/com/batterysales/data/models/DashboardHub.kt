package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class FinancialStatusSummary(
    @PropertyName("totalCashBalance") var totalCashBalance: Double = 0.0,
    @PropertyName("mainTreasuryBalance") var mainTreasuryBalance: Double = 0.0,
    @PropertyName("subWarehouseBalances") var subWarehouseBalances: Map<String, Double> = emptyMap(),
    @PropertyName("updatedAt") var updatedAt: Date = Date()
) {
    constructor() : this(0.0, 0.0, emptyMap(), Date())
}

data class BankSummary(
    @PropertyName("globalBankBalance") var globalBankBalance: Double = 0.0,
    @PropertyName("totalUnpaidChecks") var totalUnpaidChecks: Double = 0.0,
    @PropertyName("totalUnpaidBills") var totalUnpaidBills: Double = 0.0,
    @PropertyName("updatedAt") var updatedAt: Date = Date()
) {
    constructor() : this(0.0, 0.0, 0.0, Date())
}

data class SupplierSummary(
    @PropertyName("totalSupplierDebt") var totalSupplierDebt: Double = 0.0,
    @PropertyName("totalSuppliersCount") var totalSuppliersCount: Int = 0,
    @PropertyName("updatedAt") var updatedAt: Date = Date()
) {
    constructor() : this(0.0, 0, Date())
}

data class InventorySummaryData(
    @PropertyName("totalValue") var totalValue: Double = 0.0,
    @PropertyName("totalItemsCount") var totalItemsCount: Int = 0,
    @PropertyName("updatedAt") var updatedAt: Date = Date()
) {
    constructor() : this(0.0, 0, Date())
}

data class ScrapAndAlertsSummary(
    @PropertyName("totalScrapAmperes") var totalScrapAmperes: Double = 0.0,
    @PropertyName("totalScrapValue") var totalScrapValue: Double = 0.0,
    @PropertyName("activeLowStockAlertsCount") var activeLowStockAlertsCount: Int = 0,
    @PropertyName("updatedAt") var updatedAt: Date = Date()
) {
    constructor() : this(0.0, 0.0, 0, Date())
}

data class DashboardHub(
    @PropertyName("id") var id: String = "dashboard_hub",
    @PropertyName("lastUpdated") var lastUpdated: Date = Date(),
    @PropertyName("version") var version: Long = 0,
    @PropertyName("financialStatus") var financialStatus: FinancialStatusSummary = FinancialStatusSummary(),
    @PropertyName("bankSummary") var bankSummary: BankSummary = BankSummary(),
    @PropertyName("supplierSummary") var supplierSummary: SupplierSummary = SupplierSummary(),
    @PropertyName("inventorySummary") var inventorySummary: InventorySummaryData = InventorySummaryData(),
    @PropertyName("scrapAndAlertsSummary") var scrapAndAlertsSummary: ScrapAndAlertsSummary = ScrapAndAlertsSummary(),
    @PropertyName("syncRegistry") var syncRegistry: SyncRegistry = SyncRegistry()
) {
    constructor() : this(
        id = "dashboard_hub",
        lastUpdated = Date(),
        version = 0,
        financialStatus = FinancialStatusSummary(),
        bankSummary = BankSummary(),
        supplierSummary = SupplierSummary(),
        inventorySummary = InventorySummaryData(),
        scrapAndAlertsSummary = ScrapAndAlertsSummary(),
        syncRegistry = SyncRegistry()
    )
}
