package com.batterysales.data.models

import java.util.Date

/**
 * Stores pre-calculated financial balances.
 * Path: summaries/financial_status
 */
data class FinancialStatus(
    val id: String = "financial_status",
    val warehouseBalances: Map<String, WarehouseBalance> = emptyMap(), // warehouseId to balances
    val globalBankBalance: Double = 0.0,
    val globalCashBalance: Double = 0.0,
    val totalUnpaidBills: Double = 0.0,
    val totalUnpaidChecks: Double = 0.0,
    val lastUpdated: Date = Date()
)

data class WarehouseBalance(
    val warehouseId: String = "",
    val cashBalance: Double = 0.0,
    val bankBalance: Double = 0.0,
    val pendingCollection: Double = 0.0 // Total unpaid customer invoices
)
