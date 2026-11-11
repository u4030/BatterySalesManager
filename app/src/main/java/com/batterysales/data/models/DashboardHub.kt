package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

class FinancialStatusSummary {
    @PropertyName("totalCashBalance") var totalCashBalance: Double = 0.0
    @PropertyName("mainTreasuryBalance") var mainTreasuryBalance: Double = 0.0
    @PropertyName("subWarehouseBalances") var subWarehouseBalances: Map<String, Double> = emptyMap()
    @PropertyName("updatedAt") var updatedAt: Date = Date()

    constructor()
    constructor(
        totalCashBalance: Double,
        mainTreasuryBalance: Double,
        subWarehouseBalances: Map<String, Double>,
        updatedAt: Date
    ) {
        this.totalCashBalance = totalCashBalance
        this.mainTreasuryBalance = mainTreasuryBalance
        this.subWarehouseBalances = subWarehouseBalances
        this.updatedAt = updatedAt
    }
}

class BankSummary {
    @PropertyName("globalBankBalance") var globalBankBalance: Double = 0.0
    @PropertyName("totalUnpaidChecks") var totalUnpaidChecks: Double = 0.0
    @PropertyName("totalUnpaidBills") var totalUnpaidBills: Double = 0.0
    @PropertyName("updatedAt") var updatedAt: Date = Date()

    constructor()
    constructor(
        globalBankBalance: Double,
        totalUnpaidChecks: Double,
        totalUnpaidBills: Double,
        updatedAt: Date
    ) {
        this.globalBankBalance = globalBankBalance
        this.totalUnpaidChecks = totalUnpaidChecks
        this.totalUnpaidBills = totalUnpaidBills
        this.updatedAt = updatedAt
    }
}

class SupplierSummary {
    @PropertyName("totalSupplierDebt") var totalSupplierDebt: Double = 0.0
    @PropertyName("totalSuppliersCount") var totalSuppliersCount: Int = 0
    @PropertyName("updatedAt") var updatedAt: Date = Date()

    constructor()
    constructor(
        totalSupplierDebt: Double,
        totalSuppliersCount: Int,
        updatedAt: Date
    ) {
        this.totalSupplierDebt = totalSupplierDebt
        this.totalSuppliersCount = totalSuppliersCount
        this.updatedAt = updatedAt
    }
}

class InventorySummaryData {
    @PropertyName("totalValue") var totalValue: Double = 0.0
    @PropertyName("totalItemsCount") var totalItemsCount: Int = 0
    @PropertyName("updatedAt") var updatedAt: Date = Date()

    constructor()
    constructor(
        totalValue: Double,
        totalItemsCount: Int,
        updatedAt: Date
    ) {
        this.totalValue = totalValue
        this.totalItemsCount = totalItemsCount
        this.updatedAt = updatedAt
    }
}

class ScrapAndAlertsSummary {
    @PropertyName("totalScrapAmperes") var totalScrapAmperes: Double = 0.0
    @PropertyName("totalScrapValue") var totalScrapValue: Double = 0.0
    @PropertyName("activeLowStockAlertsCount") var activeLowStockAlertsCount: Int = 0
    @PropertyName("updatedAt") var updatedAt: Date = Date()

    constructor()
    constructor(
        totalScrapAmperes: Double,
        totalScrapValue: Double,
        activeLowStockAlertsCount: Int,
        updatedAt: Date
    ) {
        this.totalScrapAmperes = totalScrapAmperes
        this.totalScrapValue = totalScrapValue
        this.activeLowStockAlertsCount = activeLowStockAlertsCount
        this.updatedAt = updatedAt
    }
}

class DashboardHub {
    @PropertyName("id") var id: String = "dashboard_hub"
    @PropertyName("lastUpdated") var lastUpdated: Date = Date()
    @PropertyName("version") var version: Long = 0
    @PropertyName("financialStatus") var financialStatus: FinancialStatusSummary = FinancialStatusSummary()
    @PropertyName("bankSummary") var bankSummary: BankSummary = BankSummary()
    @PropertyName("supplierSummary") var supplierSummary: SupplierSummary = SupplierSummary()
    @PropertyName("inventorySummary") var inventorySummary: InventorySummaryData = InventorySummaryData()
    @PropertyName("scrapAndAlertsSummary") var scrapAndAlertsSummary: ScrapAndAlertsSummary = ScrapAndAlertsSummary()
    @PropertyName("syncRegistry") var syncRegistry: SyncRegistry = SyncRegistry()

    // Fallback fields directly on DashboardHub for "syncRegistry.lastModified" or "syncRegistry.inventoryVersion" if flattened
    @PropertyName("syncRegistry.lastModified") var syncRegistryLastModified: Date? = null
    @PropertyName("syncRegistry.inventoryVersion") var syncRegistryInventoryVersion: Long = 0L

    constructor()
    constructor(
        id: String,
        lastUpdated: Date,
        version: Long,
        financialStatus: FinancialStatusSummary,
        bankSummary: BankSummary,
        supplierSummary: SupplierSummary,
        inventorySummary: InventorySummaryData,
        scrapAndAlertsSummary: ScrapAndAlertsSummary,
        syncRegistry: SyncRegistry
    ) {
        this.id = id
        this.lastUpdated = lastUpdated
        this.version = version
        this.financialStatus = financialStatus
        this.bankSummary = bankSummary
        this.supplierSummary = supplierSummary
        this.inventorySummary = inventorySummary
        this.scrapAndAlertsSummary = scrapAndAlertsSummary
        this.syncRegistry = syncRegistry
    }
}
