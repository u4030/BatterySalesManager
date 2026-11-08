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
    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated") var lastUpdated: Date = Date(),
    @get:PropertyName("version") @set:PropertyName("version") var version: Long = 0,
    
    // Aggregate collections for Dashboard
    @get:PropertyName("todayCollection") @set:PropertyName("todayCollection") var todayCollection: Double = 0.0,
    @get:PropertyName("todayCollectionCount") @set:PropertyName("todayCollectionCount") var todayCollectionCount: Int = 0
)

data class WarehouseBalance(
    @get:PropertyName("warehouseId") @set:PropertyName("warehouseId") var warehouseId: String = "",
    @get:PropertyName("cashBalance") @set:PropertyName("cashBalance") var cashBalance: Double = 0.0,
    @get:PropertyName("bankBalance") @set:PropertyName("bankBalance") var bankBalance: Double = 0.0,
    @get:PropertyName("pendingCollection") @set:PropertyName("pendingCollection") var pendingCollection: Double = 0.0,
    @get:PropertyName("todayCollection") @set:PropertyName("todayCollection") var todayCollection: Double = 0.0,
    @get:PropertyName("todayCollectionCount") @set:PropertyName("todayCollectionCount") var todayCollectionCount: Int = 0,
    @get:PropertyName("lastInvoiceDate") @set:PropertyName("lastInvoiceDate") var lastInvoiceDate: String = ""
)
 
