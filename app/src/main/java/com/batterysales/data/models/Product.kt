package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Product(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val capacity: Int = 0,
    val productType: String = "",
    val costPrice: Double = 0.0,
    val barcode: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isActive: Boolean = true,
    val notes: String = ""
) {
    companion object {
        const val COLLECTION_NAME = "products"
    }
}
