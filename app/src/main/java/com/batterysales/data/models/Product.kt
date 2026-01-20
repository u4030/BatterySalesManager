package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * Product - نموذج بيانات المنتج الأساسي
 *
 * يمثل العلامة التجارية أو النوع العام للمنتج، مثل "Bosch" أو "ACDelco".
 * لا يحتوي على تفاصيل تختلف باختلاف السعة.
 *
 * الخصائص:
 * - id: معرف المنتج (معرف Firestore)
 * - name: اسم المنتج أو العلامة التجارية (مثال: "بطاريات بوش")
 * - notes: ملاحظات عامة عن هذا النوع من المنتجات
 * - createdAt: تاريخ إنشاء سجل المنتج
 * - updatedAt: تاريخ آخر تحديث
 * - isArchived: لتحديد ما إذا كان المنتج مؤرشفًا (محذوفًا منطقيًا)
 */
data class Product(
    @DocumentId
    val id: String = "",
    val name: String = "", // اسم المنتج العام، مثال: "بطاريات بوش"
    val notes: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isArchived: Boolean = false
) {
    companion object {
        const val COLLECTION_NAME = "products"
    }

    /**
     * التحقق من صحة بيانات المنتج
     *
     * @return true إذا كان الاسم غير فارغ
     */
    fun isValid(): Boolean {
        return name.isNotBlank()
    }

    /**
     * الحصول على رسالة خطأ التحقق
     *
     * @return رسالة الخطأ أو null إذا كانت البيانات صحيحة
     */
    fun getValidationError(): String? {
        return when {
            name.isBlank() -> "اسم المنتج مطلوب"
            else -> null
        }
    }
}
