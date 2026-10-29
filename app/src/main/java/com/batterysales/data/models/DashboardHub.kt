package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class DashboardHub(
    val id: String = "dashboard_hub",
    val financialStatus: DashboardFinancialStatus = DashboardFinancialStatus(),
    val bankSummary: BankSummary = BankSummary(),
    val supplierSummary: SupplierSummary = SupplierSummary(),
    val inventorySummary: InventorySummaryHub = InventorySummaryHub(),
    val lastUpdated: Date = Date(),
    val version: Long = 0
)

data class DashboardFinancialStatus(
    @get:PropertyName("totalCashBalance")
    @set:PropertyName("totalCashBalance")
    var totalCashBalance: Double = 0.0,

    @get:PropertyName("globalCashBalance")
    @set:PropertyName("globalCashBalance")
    var globalCashBalance: Double = 0.0,

    @get:PropertyName("subWarehouseBalances")
    @set:PropertyName("subWarehouseBalances")
    var subWarehouseBalances: Map<String, Double> = emptyMap(),

    @get:PropertyName("warehouseBalances")
    @set:PropertyName("warehouseBalances")
    var warehouseBalances: Map<String, WarehouseBalance> = emptyMap()
) {
    val cashBalance: Double
        get() = if (totalCashBalance != 0.0) totalCashBalance else globalCashBalance

    fun getWarehouseCashBalance(whId: String): Double {
        return subWarehouseBalances[whId]
            ?: warehouseBalances[whId]?.cashBalance
            ?: 0.0
    }
}

data class BankSummary(
    val globalBankBalance: Double = 0.0,
    val totalUnpaidBills: Double = 0.0,
    val totalUnpaidChecks: Double = 0.0
)

data class SupplierSummary(
    val totalSupplierDebt: Double = 0.0
)

data class InventorySummaryHub(
    val totalItemsCount: Int = 0,
    val totalValue: Double = 0.0
)
