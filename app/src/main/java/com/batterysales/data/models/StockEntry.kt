package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class StockEntry(
    @DocumentId
    val id: String = "",
    val productVariantId: String = "",
    val warehouseId: String = "",
    val quantity: Int = 0,
    val costPrice: Double = 0.0, // Cost per item
    val costPerAmpere: Double = 0.0,
    val totalAmperes: Int = 0,
    val totalCost: Double = 0.0,
    val grandTotalAmperes: Int = 0,
    val grandTotalCost: Double = 0.0,
    val timestamp: Date = Date(),
    val supplier: String = "",
    val invoiceId: String? = null, // Link to invoice for sales entries
    val status: String = "approved", // approved, pending
    val createdBy: String = ""
) {
    companion object {
        const val COLLECTION_NAME = "stock_entries"
    }
}
