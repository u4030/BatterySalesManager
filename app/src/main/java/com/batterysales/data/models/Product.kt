package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * Product - نموذج بيانات المنتج
 *
 * يمثل منتج (بطارية) في النظام
 * يحتوي على جميع المعلومات المتعلقة بالمنتج
 */
data class Product(
    /**
     * معرف المنتج الفريد
     * يتم إنشاؤه تلقائياً بواسطة Firestore
     */
    @DocumentId
    val id: String = "",

    /**
     * اسم المنتج
     * مثال: "بطارية سيارة 60 أمبير"
     */
    val name: String = "",

    /**
     * السعة (بالأمبير)
     * مثال: 60, 75, 100
     */
    val capacity: Int = 0,

    /**
     * نوع المنتج / الشركة المصنعة
     * مثال: "Bosch", "Varta", "Delkor"
     */
    val productType: String = "",

    /**
     * الكمية الحالية للمنتج
     */
    val quantity: Int = 0,

    /**
     * الحد الأدنى للكمية
     * عندما تنخفض الكمية عن هذا الحد، يتم إظهار تنبيه.
     */
    val minimumQuantity: Int = 5,

    /**
     * سعر التكلفة (بالريال السعودي)
     */
    val costPrice: Double = 0.0,

    /**
     * سعر البيع (بالريال السعودي)
     */
    val sellingPrice: Double = 0.0,

    /**
     * الباركود
     * رمز فريد للمنتج
     */
    val barcode: String = "",

    /**
     * تاريخ إنشاء المنتج
     */
    val createdAt: Date = Date(),

    /**
     * تاريخ آخر تحديث
     */
    val updatedAt: Date = Date(),

    /**
     * هل المنتج نشط؟
     * true = المنتج متاح للبيع
     * false = المنتج غير متاح (حذف منطقي)
     */
    val isActive: Boolean = true,

    /**
     * ملاحظات إضافية عن المنتج
     */
    val notes: String = ""
) {
    // دوال مساعدة
    fun getProfitPerUnit(): Double = sellingPrice - costPrice
    fun isLowStock(): Boolean = quantity <= minimumQuantity
    fun isOutOfStock(): Boolean = quantity == 0

    companion object {
        const val COLLECTION_NAME = "products"
    }
}
