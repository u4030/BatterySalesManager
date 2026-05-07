package com.batterysales.data.models

import java.util.Date

/**
 * SystemStats - Document for global business metrics
 */
data class SystemStats(
    val totalSupplierDebt: Double = 0.0,
    val totalCustomerDebt: Double = 0.0,
    val totalCashBalance: Double = 0.0, // Total cash in all treasuries
    val totalInventoryValue: Double = 0.0,
    val totalInventoryQuantity: Int = 0,
    val updatedAt: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "system_metadata"
        const val DOCUMENT_ID = "global_stats"
    }
}
