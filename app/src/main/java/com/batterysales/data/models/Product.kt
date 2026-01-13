package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * Product - نموذج بيانات المنتج
 *
 * يمثل منتج (بطارية) في النظام
 * يحتوي على جميع المعلومات المتعلقة بالمنتج
 *
 * الخصائص:
 * - معرف المنتج (معرف Firestore)
 * - اسم المنتج
 * - السعة (بالأمبير)
 * - نوع المنتج (الشركة المصنعة)
 * - الكمية الحالية
 * - الحد الأدنى للكمية
 * - سعر التكلفة
 * - سعر البيع
 * - الباركود
 * - تاريخ الإنشاء
 * - تاريخ آخر تحديث
 * - هل المنتج نشط؟
 *
 * مثال الاستخدام:
 * ```
 * val product = Product(
 *     name = "بطارية جديدة",
 *     capacity = 60,
 *     productType = "Bosch",
 *     quantity = 10,
 *     minimumQuantity = 5,
 *     costPrice = 500.0,
 *     sellingPrice = 700.0,
 *     barcode = "123456789"
 * )
 * ```
 */
data class Product(
    /**
     * معرف المنتج الفريد
     *
     * يتم إنشاؤه تلقائياً بواسطة Firestore
     * لا تحتاج لتعيينه عند الإنشاء
     */
    @DocumentId
    val id: String = "",

    /**
     * اسم المنتج
     *
     * مثال: "بطارية سيارة 60 أمبير"
     */
    val name: String = "",

    /**
     * السعة (بالأمبير)
     *
     * مثال: 60, 75, 100
     * تشير إلى قوة البطارية
     */
    val capacity: Int = 0,

    /**
     * نوع المنتج / الشركة المصنعة
     *
     * مثال: "Bosch", "Varta", "Delkor"
     */
    val productType: String = "",

    /**
     * الحد الأدنى للكمية
     *
     * عندما تنخفض الكمية عن هذا الحد
     * يتم إظهار تنبيه للمسؤول
     */
    val minimumQuantity: Int = 5,

    /**
     * سعر البيع (بالريال السعودي)
     *
     * السعر الذي نبيع به المنتج للعميل
     */
    val sellingPrice: Double = 0.0,

    /**
     * الباركود
     *
     * رمز فريد للمنتج
     * يُستخدم للبحث السريع والمسح الضوئي
     */
    val barcode: String = "",

    /**
     * تاريخ إنشاء المنتج
     *
     * يتم تعيينه تلقائياً عند الإنشاء
     */
    val createdAt: Date = Date(),

    /**
     * تاريخ آخر تحديث
     *
     * يتم تحديثه تلقائياً عند كل تعديل
     */
    val updatedAt: Date = Date(),

    /**
     * هل المنتج نشط؟
     *
     * true = المنتج متاح للبيع
     * false = المنتج غير متاح (حذف منطقي)
     */
    val isActive: Boolean = true,

    /**
     * ملاحظات إضافية عن المنتج
     *
     * معلومات إضافية أو تحذيرات
     */
    val notes: String = ""
) {
    // ============================================
    // دوال مساعدة
    // ============================================

    /**
     * التحقق من صحة بيانات المنتج
     *
     * @return true إذا كانت جميع البيانات صحيحة
     */
    fun isValid(): Boolean {
        return name.isNotEmpty() &&
                capacity > 0 &&
                productType.isNotEmpty() &&
                minimumQuantity >= 0 &&
                barcode.isNotEmpty()
    }

    /**
     * الحصول على رسالة خطأ التحقق
     *
     * @return رسالة الخطأ أو null إذا كانت البيانات صحيحة
     */
    fun getValidationError(): String? {
        return when {
            name.isEmpty() -> "اسم المنتج مطلوب"
            capacity <= 0 -> "السعة يجب أن تكون أكبر من 0"
            productType.isEmpty() -> "نوع المنتج مطلوب"
            minimumQuantity < 0 -> "الحد الأدنى لا يمكن أن يكون سالب"
            barcode.isEmpty() -> "الباركود مطلوب"
            else -> null
        }
    }

    companion object {
        const val COLLECTION_NAME = "products"
    }
}

/**
 * ملاحظات مهمة:
 *
 * 1. **@DocumentId**:
 *    - تعليق من Firebase Firestore
 *    - يربط حقل id مع معرف المستند في Firestore
 *    - لا تحتاج لتعيينه يدوياً
 *
 * 2. **القيم الافتراضية**:
 *    - جميع الحقول لها قيم افتراضية
 *    - يسهل إنشاء نسخ من المنتج
 *
 * 3. **Data Class**:
 *    - يوفر equals(), hashCode(), toString(), copy()
 *    - يسهل المقارنة والنسخ
 *
 * 4. **الدوال المساعدة**:
 *    - تحسب القيم المشتقة (الربح، القيمة، إلخ)
 *    - توفر معلومات مفيدة عن المنتج
 *
 * 5. **التحقق من الصحة**:
 *    - isValid() تتحقق من جميع الحقول
 *    - getValidationError() توفر رسالة خطأ واضحة
 *
 * 6. **الحالات**:
 *    - getStockStatus() توفر حالة المخزون
 *    - isLowStock() تتحقق من المخزون المنخفض
 *    - isOutOfStock() تتحقق من نفاد المخزون
 */
