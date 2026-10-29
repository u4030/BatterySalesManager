package com.batterysales.data.models

import java.util.Date

data class HubFinancialStatus(
    val cashBalance: Double = 0.0,
    val warehouseBalances: Map<String, Double> = emptyMap()
) {
    fun getWarehouseCashBalance(warehouseId: String): Double {
        return warehouseBalances[warehouseId] ?: 0.0
    }
}

data class HubBankSummary(
    val globalBankBalance: Double = 0.0,
    val totalUnpaidBills: Double = 0.0,
    val totalUnpaidChecks: Double = 0.0
)

data class HubSupplierSummary(
    val totalSupplierDebt: Double = 0.0
)

data class HubInventorySummary(
    val totalItemsCount: Int = 0,
    val totalValue: Double = 0.0
)

data class DashboardHub(
    val financialStatus: HubFinancialStatus = HubFinancialStatus(),
    val bankSummary: HubBankSummary = HubBankSummary(),
    val supplierSummary: HubSupplierSummary = HubSupplierSummary(),
    val inventorySummary: HubInventorySummary = HubInventorySummary(),
    val lastUpdated: Date = Date()
)
