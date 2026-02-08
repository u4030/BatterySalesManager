package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
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
 * - specification: المواصفة الفنية للمنتج
 * - createdAt: تاريخ إنشاء سجل المنتج
 * - updatedAt: تاريخ آخر تحديث
 * - archived: لتحديد ما إذا كان المنتج مؤرشفًا (محذوفًا منطقيًا)
 */
data class Product(
//    @DocumentId
    val id: String = "",
    val name: String = "", // اسم المنتج العام، مثال: "بطاريات بوش"
    @get:PropertyName("notes")
    @set:PropertyName("notes")
    var specification: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val archived: Boolean = false
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
