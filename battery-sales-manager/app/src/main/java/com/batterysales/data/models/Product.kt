package com.batterysales.data.models

import com.google.firebase.firestore.Exclude
import java.util.Date

/**
 * Product - نموذج بيانات المنتج الأساسي
 */
data class Product(
    val id: String = "",
    val name: String = "",
    val supplierId: String = "",
    var specification: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val archived: Boolean = false
) {
    companion object {
        const val COLLECTION_NAME = "products"
    }

    @Exclude
    fun isValid(): Boolean = name.isNotBlank()

    @Exclude
    fun getValidationError(): String? {
        return if (name.isBlank()) "اسم المنتج مطلوب" else null
    }
}
 
