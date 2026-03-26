package com.batterysales.data.models

import java.util.Date

/**
 * ProductVariant - نموذج بيانات متغير المنتج (السعة)
 */
data class ProductVariant(
    val id: String = "",
    val productId: String = "",
    val capacity: Int = 0,
    val sellingPrice: Double = 0.0,
    val barcode: String = "",
    val minQuantity: Int = 0,
    val minQuantities: Map<String, Int> = emptyMap(),
    var specification: String = "",
    val currentStock: Map<String, Int>? = null, // WarehouseId to Quantity. Null means migration needed.
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val archived: Boolean = false
) {
    companion object {
        const val COLLECTION_NAME = "product_variants"
    }

    fun isValid(): Boolean = productId.isNotBlank() && capacity > 0

    fun getValidationError(): String? {
        return when {
            productId.isBlank() -> "معرف المنتج الأساسي مطلوب"
            capacity <= 0 -> "السعة يجب أن تكون أكبر من صفر"
            else -> null
        }
    }
}
