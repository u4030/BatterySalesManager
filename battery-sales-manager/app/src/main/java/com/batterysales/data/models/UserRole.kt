package com.batterysales.data.models

/**
 * UserRole - تحديد أدوار وصلاحيات المستخدمين في النظام
 */
enum class UserRole(val label: String) {
    ADMIN("مدير النظام"),      // صلاحيات كاملة: إدارة المستخدمين، التقارير، الإعدادات
    SELLER("بائع"),           // صلاحيات محدودة: المبيعات، الفواتير، المستودع
    ACCOUNTANT("محاسب"),      // صلاحيات مالية: الخزينة، الكمبيالات، التقارير المالية
    WAREHOUSE_MANAGER("مدير مستودع"); // صلاحيات المخزون: المنتجات، الجرد، التوريد

    companion object {
        fun fromString(role: String): UserRole {
            return try {
                valueOf(role.uppercase())
            } catch (e: Exception) {
                SELLER // الدور الافتراضي في حال الخطأ
            }
        }
    }
}
