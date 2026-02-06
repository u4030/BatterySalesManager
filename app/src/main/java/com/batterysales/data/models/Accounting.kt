package com.batterysales.data.models

import java.util.Date

/**
 * نموذج المصروف
 * 
 * يمثل مصروف واحد
 */
//data class Expense(
//    val id: String = "",
//    val description: String = "", // وصف المصروف
//    val amount: Double = 0.0, // مبلغ المصروف
//    val category: ExpenseCategory = ExpenseCategory.OTHER, // فئة المصروف
//    val createdAt: Date = Date(),
//    val updatedAt: Date = Date(),
//    val relatedBillId: String? = null, // معرف الكمبيالة المرتبطة (إن وجدت)
//    val notes: String = ""
//) {
//    companion object {
//        const val COLLECTION_NAME = "expenses"
//    }
//}

/**
 * فئات المصروفات
 */
enum class ExpenseCategory {
    SALARY,        // راتب الموظفين
    UTILITIES,     // المرافق (كهرباء، ماء، إنترنت)
    RENT,          // الإيجار
    TRANSPORTATION, // النقل والتوصيل
    MAINTENANCE,   // الصيانة
    SUPPLIES,      // المستلزمات
    ADVERTISING,   // الإعلان
    INSURANCE,     // التأمين
    TAXES,         // الضرائب
    OTHER          // أخرى
}

/**
 * نموذج ملخص المحاسبة اليومية
 * 
 * يحتفظ بملخص الواردات والمصروفات لكل يوم
 */
data class DailyAccountingSummary(
    val id: String = "",
    val date: Date = Date(),
    val totalIncome: Double = 0.0, // إجمالي الواردات (من المبيعات)
    val totalExpenses: Double = 0.0, // إجمالي المصروفات
    val netAmount: Double = 0.0, // الصافي (الواردات - المصروفات)
    val totalDebts: Double = 0.0, // إجمالي الذمم المتبقية
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "daily_accounting_summary"
    }
}

/**
 * نموذج العملية المحاسبية
 * 
 * يسجل كل عملية مالية (واردة أو صادرة)
 */
//data class Transaction(
//    val id: String = "",
//    val type: TransactionType = TransactionType.INCOME, // نوع العملية
//    val amount: Double = 0.0, // المبلغ
//    val description: String = "", // الوصف
//    val relatedId: String? = null, // معرف الفاتورة أو المصروف المرتبطة
//    val createdAt: Date = Date(),
//    val notes: String = ""
//) {
//    companion object {
//        const val COLLECTION_NAME = "transactions"
//    }
//}

/**
 * أنواع العمليات المحاسبية
 */
enum class TransactionType {
    INCOME,    // واردات (من المبيعات)
    EXPENSE,   // مصروفات
    PAYMENT,   // دفعة من الذمم
    REFUND     // استرجاع
}
