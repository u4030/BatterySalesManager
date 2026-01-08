package com.batterysales.utils

/**
 * ثوابت التطبيق
 */
object Constants {
    // Firebase Collections
    object Collections {
        const val USERS = "users"
        const val PRODUCTS = "products"
        const val INVOICES = "invoices"
        const val OLD_BATTERIES = "old_batteries"
        const val BILLS = "bills"
        const val EXPENSES = "expenses"
        const val TRANSACTIONS = "transactions"
        const val DAILY_SALES_SUMMARY = "daily_sales_summary"
        const val DAILY_ACCOUNTING_SUMMARY = "daily_accounting_summary"
        const val PAYMENTS = "payments"
    }

    // User Roles
    object UserRoles {
        const val ADMIN = "ADMIN"
        const val SELLER = "SELLER"
    }

    // Bill Status
    object BillStatus {
        const val PAID = "PAID"
        const val UNPAID = "UNPAID"
        const val OVERDUE = "OVERDUE"
        const val PARTIAL = "PARTIAL"
    }

    // Bill Types
    object BillTypes {
        const val CHECK = "CHECK"
        const val BILL = "BILL"
        const val TRANSFER = "TRANSFER"
        const val OTHER = "OTHER"
    }

    // Expense Categories
    object ExpenseCategories {
        const val SALARY = "SALARY"
        const val UTILITIES = "UTILITIES"
        const val RENT = "RENT"
        const val TRANSPORTATION = "TRANSPORTATION"
        const val MAINTENANCE = "MAINTENANCE"
        const val SUPPLIES = "SUPPLIES"
        const val ADVERTISING = "ADVERTISING"
        const val INSURANCE = "INSURANCE"
        const val TAXES = "TAXES"
        const val OTHER = "OTHER"
    }

    // Transaction Types
    object TransactionTypes {
        const val INCOME = "INCOME"
        const val EXPENSE = "EXPENSE"
        const val PAYMENT = "PAYMENT"
        const val REFUND = "REFUND"
    }

    // Validation
    object Validation {
        const val MIN_PASSWORD_LENGTH = 6
        const val MIN_PHONE_LENGTH = 9
        const val MAX_PHONE_LENGTH = 15
    }

    // UI
    object UI {
        const val ANIMATION_DURATION = 300
        const val CORNER_RADIUS = 8
    }

    // Pagination
    object Pagination {
        const val PAGE_SIZE = 20
    }

    // Error Messages
    object ErrorMessages {
        const val INVALID_EMAIL = "البريد الإلكتروني غير صحيح"
        const val INVALID_PASSWORD = "كلمة المرور قصيرة جداً"
        const val INVALID_PHONE = "رقم الهاتف غير صحيح"
        const val NETWORK_ERROR = "خطأ في الاتصال"
        const val UNKNOWN_ERROR = "حدث خطأ غير متوقع"
        const val PRODUCT_NOT_FOUND = "المنتج غير موجود"
        const val INSUFFICIENT_STOCK = "المخزون غير كافي"
    }

    // Success Messages
    object SuccessMessages {
        const val SALE_ADDED = "تم إضافة المبيعة بنجاح"
        const val PRODUCT_ADDED = "تم إضافة المنتج بنجاح"
        const val BILL_ADDED = "تم إضافة الكمبيالة بنجاح"
        const val PAYMENT_ADDED = "تم إضافة الدفعة بنجاح"
        const val EXPENSE_ADDED = "تم إضافة المصروف بنجاح"
    }
}
