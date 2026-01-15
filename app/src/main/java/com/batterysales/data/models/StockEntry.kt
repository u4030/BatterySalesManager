package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class StockEntry(
    @DocumentId
    val id: String = "",
    val productId: String = "",
    val warehouseId: String = "",
    val quantity: Int = 0,
    val costPrice: Double = 0.0,
    val timestamp: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "stock_entries"
    }
}
