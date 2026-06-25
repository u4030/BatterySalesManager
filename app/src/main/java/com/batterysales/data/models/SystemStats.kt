package com.batterysales.data.models

import java.util.Date

data class SystemStats(
    val totalSupplierDebt: Double = 0.0,
    val totalCustomerDebt: Double = 0.0,
    val totalInventoryValue: Double = 0.0,
    val totalInventoryQuantity: Int = 0,
    val totalCashBalance: Double = 0.0, // Pre-aggregated sum of income - expense
    val totalBankBalance: Double = 0.0,
    val totalUnpaidChecks: Double = 0.0,
    val totalUnpaidBills: Double = 0.0,
    val updatedAt: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "system_stats"
        const val DOCUMENT_ID = "global_stats"
    }
}
 
