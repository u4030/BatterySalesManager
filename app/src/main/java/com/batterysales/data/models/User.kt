package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * User - نموذج بيانات المستخدم
 *
 * يمثل مستخدم النظام (موظف أو مدير)
 * يحتوي على معلومات المستخدم والأدوار والصلاحيات
 *
 * الخصائص:
 * - معرف المستخدم
 * - البريد الإلكتروني
 * - اسم المستخدم
 * - رقم الهاتف
 * - الدور (مدير، بائع، محاسب)
 * - الصلاحيات
 * - تاريخ الإنشاء
 * - آخر تسجيل دخول
 * - هل المستخدم نشط؟
 *
 * مثال الاستخدام:
 * ```
 * val user = User(
 *     email = "admin@example.com",
 *     displayName = "أحمد محمد",
 *     phone = "0501234567",
 *     role = "admin"
 * )
 * ```
 */
data class User(
    /**
     * معرف المستخدم الفريد
     *
     * يتم إنشاؤه بواسطة Firebase Authentication
     */
//    @DocumentId
    val id: String = "",

    /**
     * البريد الإلكتروني
     *
     * يُستخدم لتسجيل الدخول
     * يجب أن يكون فريداً
     */
    val email: String = "",

    /**
     * اسم المستخدم (الاسم الكامل)
     *
     * مثال: "أحمد محمد علي"
     */
    val displayName: String = "",

    /**
     * رقم الهاتف
     *
     * للتواصل مع المستخدم
     */
    val phone: String = "",

    /**
     * الدور / الصلاحية
     *
     * القيم الممكنة:
     * - "admin" = مدير النظام
     * - "manager" = مدير المبيعات
     * - "seller" = بائع
     * - "accountant" = محاسب
     * - "warehouse" = مسؤول المستودع
     */
    val role: String = "seller",

    /**
     * المستودع المرتبط بالمستخدم (خاص بالبائع)
     */
    val warehouseId: String? = null,


    /**
     * قائمة الصلاحيات
     *
     * تحدد ما يمكن للمستخدم فعله
     * أمثلة:
     * - "view_sales"
     * - "create_invoice"
     * - "edit_product"
     * - "view_reports"
     * - "manage_users"
     */
    val permissions: List<String> = emptyList(),

    /**
     * تاريخ إنشاء الحساب
     */
    val createdAt: Date = Date(),

    /**
     * تاريخ آخر تسجيل دخول
     */
    val lastLoginAt: Date? = null,

    /**
     * هل المستخدم نشط؟
     *
     * true = المستخدم يمكنه تسجيل الدخول
     * false = المستخدم محظور (حذف منطقي)
     */
    val isActive: Boolean = true,

    /**
     * هل تم التحقق من البريد الإلكتروني؟
     */
    val isEmailVerified: Boolean = false,

    /**
     * ملاحظات إضافية
     */
    val notes: String = "",

    /**
     * صورة المستخدم (رابط)
     */
    val profileImage: String? = null,

    /**
     * عنوان المستخدم
     */
    val address: String = "",

    /**
     * المدينة
     */
    val city: String = "",

    /**
     * الرمز البريدي
     */
    val postalCode: String = ""
) {
    // ============================================
    // دوال مساعدة
    // ============================================

    /**
     * التحقق من أن المستخدم مدير
     *
     * @return true إذا كان الدور admin
     */
    fun isAdmin(): Boolean {
        return role == "admin"
    }

    /**
     * التحقق من أن المستخدم مدير مبيعات
     *
     * @return true إذا كان الدور manager
     */
    fun isManager(): Boolean {
        return role == "manager"
    }

    /**
     * التحقق من أن المستخدم بائع
     *
     * @return true إذا كان الدور seller
     */
    fun isSeller(): Boolean {
        return role == "seller"
    }

    /**
     * التحقق من أن المستخدم محاسب
     *
     * @return true إذا كان الدور accountant
     */
    fun isAccountant(): Boolean {
        return role == "accountant"
    }

    /**
     * التحقق من أن المستخدم مسؤول مستودع
     *
     * @return true إذا كان الدور warehouse
     */
    fun isWarehouseManager(): Boolean {
        return role == "warehouse"
    }

    /**
     * التحقق من وجود صلاحية معينة
     *
     * @param permission الصلاحية المطلوبة
     * @return true إذا كان لديه الصلاحية
     */
    fun hasPermission(permission: String): Boolean {
        return permissions.contains(permission)
    }

    /**
     * التحقق من وجود أي من الصلاحيات
     *
     * @param permissionsList قائمة الصلاحيات
     * @return true إذا كان لديه أي من الصلاحيات
     */
    fun hasAnyPermission(permissionsList: List<String>): Boolean {
        return permissionsList.any { permissions.contains(it) }
    }

    /**
     * التحقق من وجود جميع الصلاحيات
     *
     * @param permissionsList قائمة الصلاحيات
     * @return true إذا كان لديه جميع الصلاحيات
     */
    fun hasAllPermissions(permissionsList: List<String>): Boolean {
        return permissionsList.all { permissions.contains(it) }
    }

    /**
     * الحصول على وصف الدور
     *
     * @return وصف الدور بالعربية
     */
    fun getRoleText(): String {
        return when (role) {
            "admin" -> "مدير النظام"
            "manager" -> "مدير المبيعات"
            "seller" -> "بائع"
            "accountant" -> "محاسب"
            "warehouse" -> "مسؤول المستودع"
            else -> "غير معروف"
        }
    }

    /**
     * الحصول على الاسم الكامل للمستخدم
     *
     * @return الاسم الكامل
     */
    fun getFullName(): String {
        return displayName.ifEmpty { email.substringBefore("@") }
    }

    /**
     * الحصول على العنوان الكامل
     *
     * @return العنوان الكامل (العنوان، المدينة، الرمز البريدي)
     */
    fun getFullAddress(): String {
        val parts = listOf(address, city, postalCode).filter { it.isNotEmpty() }
        return parts.joinToString(", ")
    }

    /**
     * التحقق من أن المستخدم يمكنه إنشاء فواتير
     *
     * @return true إذا كان لديه الصلاحية
     */
    fun canCreateInvoice(): Boolean {
        return isAdmin() || isManager() || isSeller() || hasPermission("create_invoice")
    }

    /**
     * التحقق من أن المستخدم يمكنه عرض التقارير
     *
     * @return true إذا كان لديه الصلاحية
     */
    fun canViewReports(): Boolean {
        return isAdmin() || isManager() || isAccountant() || hasPermission("view_reports")
    }

    /**
     * التحقق من أن المستخدم يمكنه إدارة المستخدمين
     *
     * @return true إذا كان لديه الصلاحية
     */
    fun canManageUsers(): Boolean {
        return isAdmin() || hasPermission("manage_users")
    }

    /**
     * التحقق من أن المستخدم يمكنه تعديل المنتجات
     *
     * @return true إذا كان لديه الصلاحية
     */
    fun canEditProducts(): Boolean {
        return isAdmin() || isManager() || isWarehouseManager() || hasPermission("edit_product")
    }

    /**
     * التحقق من صحة بيانات المستخدم
     *
     * @return true إذا كانت جميع البيانات صحيحة
     */
    fun isValid(): Boolean {
        return email.isNotEmpty() &&
                displayName.isNotEmpty() &&
                phone.isNotEmpty() &&
                role.isNotEmpty() &&
                isValidEmail(email)
    }

    /**
     * الحصول على رسالة خطأ التحقق
     *
     * @return رسالة الخطأ أو null إذا كانت البيانات صحيحة
     */
    fun getValidationError(): String? {
        return when {
            email.isEmpty() -> "البريد الإلكتروني مطلوب"
            !isValidEmail(email) -> "البريد الإلكتروني غير صحيح"
            displayName.isEmpty() -> "اسم المستخدم مطلوب"
            displayName.length < 3 -> "اسم المستخدم يجب أن يكون 3 أحرف على الأقل"
            phone.isEmpty() -> "رقم الهاتف مطلوب"
            phone.length < 9 -> "رقم الهاتف غير صحيح"
            role.isEmpty() -> "الدور مطلوب"
            else -> null
        }
    }

    /**
     * التحقق من صحة البريد الإلكتروني
     *
     * @param email البريد الإلكتروني
     * @return true إذا كان البريد صحيح
     */
    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$"
        return email.matches(emailRegex.toRegex())
    }

    /**
     * الحصول على معلومات المستخدم كنص
     *
     * @return معلومات المستخدم
     */
    fun getInfo(): String {
        return """
            الاسم: $displayName
            البريد: $email
            الهاتف: $phone
            الدور: ${getRoleText()}
            الحالة: ${if (isActive) "نشط" else "معطل"}
            تاريخ الإنشاء: $createdAt
        """.trimIndent()
    }

    companion object {
        const val COLLECTION_NAME = "users"

        // أدوار المستخدمين
        const val ROLE_ADMIN = "admin"
        const val ROLE_MANAGER = "manager"
        const val ROLE_SELLER = "seller"
        const val ROLE_ACCOUNTANT = "accountant"
        const val ROLE_WAREHOUSE = "warehouse"

        // الصلاحيات
        const val PERMISSION_VIEW_SALES = "view_sales"
        const val PERMISSION_CREATE_INVOICE = "create_invoice"
        const val PERMISSION_EDIT_PRODUCT = "edit_product"
        const val PERMISSION_VIEW_REPORTS = "view_reports"
        const val PERMISSION_MANAGE_USERS = "manage_users"
        const val PERMISSION_DELETE_INVOICE = "delete_invoice"
        const val PERMISSION_EDIT_INVOICE = "edit_invoice"
        const val PERMISSION_VIEW_ACCOUNTING = "view_accounting"
        const val PERMISSION_EDIT_ACCOUNTING = "edit_accounting"
        const val PERMISSION_VIEW_TREASURY = "view_treasury"
        const val PERMISSION_USE_TREASURY = "use_treasury"
    }
}

/**
 * ملاحظات مهمة:
 *
 * 1. **الأدوار**:
 *    - admin: مدير النظام (جميع الصلاحيات)
 *    - manager: مدير المبيعات (إدارة المبيعات والتقارير)
 *    - seller: بائع (إنشاء فواتير فقط)
 *    - accountant: محاسب (إدارة المحاسبة والتقارير)
 *    - warehouse: مسؤول المستودع (إدارة المخزون)
 *
 * 2. **الصلاحيات**:
 *    - تحدد ما يمكن للمستخدم فعله
 *    - يمكن إضافة صلاحيات إضافية
 *    - يمكن الجمع بين عدة صلاحيات
 *
 * 3. **الدوال المساعدة**:
 *    - isAdmin(), isManager(), إلخ: التحقق من الدور
 *    - hasPermission(): التحقق من الصلاحية
 *    - canCreateInvoice(), canViewReports(), إلخ: التحقق من العمليات
 *
 * 4. **الحذف المنطقي**:
 *    - isActive = false بدلاً من الحذف الفعلي
 *    - يحافظ على السجلات التاريخية
 *
 * 5. **البيانات الشخصية**:
 *    - يتم حفظ معلومات المستخدم الشخصية
 *    - يمكن تحديثها في أي وقت
 *
 * 6. **الأمان**:
 *    - كلمة المرور لا تُحفظ في Firestore
 *    - يتم إدارتها بواسطة Firebase Authentication
 */
