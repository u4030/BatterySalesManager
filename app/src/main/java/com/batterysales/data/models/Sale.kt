package com.batterysales.data.models

import java.util.Date

/**
 * نموذج الفاتورة (المبيعة)
 * 
 * يمثل عملية بيع واحدة
 */
//data class Invoice(
//    val id: String = "",
//    val invoiceNumber: String = "", // رقم الفاتورة الفريد
//    val productId: String = "", // معرف المنتج المباع
//    val productName: String = "",
//    val capacity: Int = 0, // سعة البطارية المباعة
//    val salePrice: Double = 0.0, // سعر البيع
//    val buyerName: String = "",
//    val buyerPhone: String = "",
//    val remainingAmount: Double = 0.0, // المبلغ المتبقي (ذمم)
//    val oldBatteryCapacity: Int = 0, // سعة البطارية القديمة المرتجعة
//    val createdAt: Date = Date(),
//    val updatedAt: Date = Date(),
//    val userId: String = "", // معرف البائع
//    val isDeleted: Boolean = false // للحذف المنطقي
//) {
//    companion object {
//        const val COLLECTION_NAME = "invoices"
//    }
//}

/**
 * نموذج الدفعة (Payment)
 * 
 * يمثل دفعة واحدة من الذمم
 */
//data class Payment(
//    val id: String = "",
//    val invoiceId: String = "", // معرف الفاتورة المرتبطة
//    val amount: Double = 0.0, // مبلغ الدفعة
//    val paymentDate: Date = Date(),
//    val paymentMethod: String = "", // نقد، شيك، تحويل، إلخ
//    val notes: String = "",
//    val createdAt: Date = Date()
//) {
//    companion object {
//        const val COLLECTION_NAME = "payments"
//    }
//}

/**
 * نموذج ملخص المبيعات اليومية
 * 
 * يحتفظ بملخص المبيعات لكل يوم
 */
data class DailySalesSummary(
    val id: String = "",
    val date: Date = Date(),
    val totalSales: Double = 0.0, // إجمالي المبيعات
    val totalInvoices: Int = 0, // عدد الفواتير
    val totalDebts: Double = 0.0, // إجمالي الذمم
    val totalOldBatteryCapacity: Int = 0, // إجمالي سعة البطاريات القديمة
    val userId: String = "", // معرف البائع
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "daily_sales_summary"
    }
}
