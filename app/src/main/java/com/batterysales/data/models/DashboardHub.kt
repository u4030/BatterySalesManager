package com.batterysales.data.models

import java.util.Date

data class DashboardHub(
    val id: String = "dashboard_hub",
    val financialStatus: FinancialStatus = FinancialStatus(),
    val bankSummary: BankSummary = BankSummary(),
    val supplierSummary: SupplierSummary = SupplierSummary(),
    val inventorySummary: InventorySummaryHub = InventorySummaryHub(),
    val lastUpdated: Date = Date(),
    val version: Long = 0
)

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
