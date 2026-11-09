package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Stores pre-calculated financial balances.
 * Path: summaries/financial_status
 */
data class FinancialStatus(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "financial_status",
    @get:PropertyName("warehouseBalances") @set:PropertyName("warehouseBalances") var warehouseBalances: Map<String, WarehouseBalance> = emptyMap(),
    @get:PropertyName("globalBankBalance") @set:PropertyName("globalBankBalance") var globalBankBalance: Double = 0.0,
    @get:PropertyName("globalCashBalance") @set:PropertyName("globalCashBalance") var globalCashBalance: Double = 0.0,
    @get:PropertyName("totalUnpaidBills") @set:PropertyName("totalUnpaidBills") var totalUnpaidBills: Double = 0.0,
    @get:PropertyName("totalUnpaidChecks") @set:PropertyName("totalUnpaidChecks") var totalUnpaidChecks: Double = 0.0,
    @get:PropertyName("totalCustomerDebt") @set:PropertyName("totalCustomerDebt") var totalCustomerDebt: Double = 0.0,
    @get:PropertyName("total_customer_debt") @set:PropertyName("total_customer_debt") var totalCustomerDebtSnake: Double = 0.0,
    @get:PropertyName("totalReceivables") @set:PropertyName("totalReceivables") var totalReceivables: Double = 0.0,
    @get:PropertyName("total_receivables") @set:PropertyName("total_receivables") var totalReceivablesSnake: Double = 0.0,
    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated") var lastUpdated: Date = Date(),
    @get:PropertyName("last_updated") @set:PropertyName("last_updated") var lastUpdatedSnake: Date? = null,
    @get:PropertyName("version") @set:PropertyName("version") var version: Long = 0,
    
    // Aggregate collections for Dashboard
    @get:PropertyName("todayCollection") @set:PropertyName("todayCollection") var todayCollection: Double = 0.0,
    @get:PropertyName("today_collection") @set:PropertyName("today_collection") var todayCollectionSnake: Double = 0.0,
    @get:PropertyName("todayCollectionCount") @set:PropertyName("todayCollectionCount") var todayCollectionCount: Int = 0,
    @get:PropertyName("today_collection_count") @set:PropertyName("today_collection_count") var todayCollectionCountSnake: Int = 0
) {
    val resolvedCustomerDebt: Double
        get() = if (totalCustomerDebt != 0.0) totalCustomerDebt else totalCustomerDebtSnake
}
