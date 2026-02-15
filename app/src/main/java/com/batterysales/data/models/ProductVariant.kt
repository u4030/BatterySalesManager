package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * ProductVariant - نموذج بيانات متغير المنتج (السعة)
 *
 * يمثل سعة معينة أو نوعًا فرعيًا من منتج أساسي.
 * يرتبط بمنتج أساسي عبر `productId`.
 *
 * الخصائص:
 * - id: المعرف الفريد للمتغير (معرف Firestore)
 * - productId: معرف المنتج الأساسي الذي ينتمي إليه هذا المتغير
 * - capacity: السعة بالأمبير (مثال: 55, 70)
 * - sellingPrice: سعر بيع هذه السعة المحددة
 * - barcode: الباركود الخاص بهذه السعة
 * - specification: المواصفة الفنية لهذه السعة
 * - createdAt: تاريخ إنشاء سجل المتغير
 * - updatedAt: تاريخ آخر تحديث
 * - archived: لتحديد ما إذا كان المتغير مؤرشفًا
 */
data class ProductVariant(
//    @DocumentId
    val id: String = "",
    val productId: String = "", // للربط مع المنتج الأساسي
    val capacity: Int = 0,
    val sellingPrice: Double = 0.0,
    val barcode: String = "",
    val minQuantity: Int = 0, // Default/Legacy minimum quantity
    val minQuantities: Map<String, Int> = emptyMap(), // Per-warehouse minimum quantities: WarehouseId -> MinQuantity
    var specification: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val archived: Boolean = false
) {
    companion object {
        const val COLLECTION_NAME = "product_variants"
    }

    /**
     * التحقق من صحة بيانات متغير المنتج
     */
    fun isValid(): Boolean {
        return productId.isNotBlank() && capacity > 0
    }

    /**
     * الحصول على رسالة خطأ التحقق
     */
    fun getValidationError(): String? {
        return when {
            productId.isBlank() -> "معرف المنتج الأساسي مطلوب"
            capacity <= 0 -> "السعة يجب أن تكون أكبر من صفر"
            else -> null
        }
    }
}
