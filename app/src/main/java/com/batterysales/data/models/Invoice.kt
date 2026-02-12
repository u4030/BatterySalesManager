package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * Invoice - نموذج بيانات الفواتير
 *
 * يمثل فاتورة مبيعات واحدة في النظام
 *
 * مثال:
 * ```
 * val invoice = Invoice(
 *     id = "invoice_001",
 *     invoiceNumber = "INV-001",
 *     customerId = "customer_001",
 *     customerName = "أحمد محمد",
 *     items = listOf(
 *         InvoiceItem(productId = "prod_001", productName = "بطارية", quantity = 2, price = 500.0)
 *     ),
 *     subtotal = 1000.0,
 *     tax = 150.0,
 *     totalAmount = 1150.0,
 *     status = "completed"
 * )
 * ```
 */
data class Invoice(
//    @DocumentId
    val id: String = "",
    val invoiceNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val taxRate: Double = 0.0,
    val discount: Double = 0.0,
    val discountRate: Double = 0.0,
    val totalAmount: Double = 0.0,
    val oldBatteriesValue: Double = 0.0,
    val oldBatteriesQuantity: Int = 0,
    val oldBatteriesTotalAmperes: Double = 0.0,
    val finalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val status: String = "draft",
    val paymentMethod: String = "cash",
    val warehouseId: String = "",
    val invoiceDate: Date = Date(0), // Default to epoch to identify missing data in older documents
    val dueDate: Date = Date(),
    val paidDate: Date? = null,
    val notes: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    // ============================================
    // دوال مساعدة
    // ============================================

    fun isPaid(): Boolean = paidAmount >= totalAmount
    fun isPending(): Boolean = status == "pending"
    fun isCompleted(): Boolean = status == "completed"
    fun isDraft(): Boolean = status == "draft"
    fun isCancelled(): Boolean = status == "cancelled"
    fun isOverdue(): Boolean {
        val now = Date()
        return now.after(dueDate) && !isPaid()
    }
    fun isPartiallyPaid(): Boolean = paidAmount > 0 && paidAmount < totalAmount
    fun calculateRemainingAmount(): Double = (totalAmount - paidAmount).coerceAtLeast(0.0)
    fun daysUntilDue(): Long {
        val now = Date()
        val diffInMillis = dueDate.time - now.time
        return diffInMillis / (1000 * 60 * 60 * 24)
    }
    fun daysOverdue(): Long {
        if (!isOverdue()) return 0
        return daysUntilDue() * -1
    }
    fun calculateLateFee(): Double {
        if (!isOverdue()) return 0.0
        val daysOverdue = daysOverdue()
        return remainingAmount * 0.005 * daysOverdue
    }
    fun getStatusLabel(): String = when (status) {
        "draft" -> "مسودة"
        "pending" -> "معلقة"
        "completed" -> "مكتملة"
        "paid" -> "مسددة"
        "cancelled" -> "ملغاة"
        else -> "غير معروفة"
    }
    fun getPaymentMethodLabel(): String = when (paymentMethod) {
        "cash" -> "نقداً"
        "check" -> "شيك"
        "transfer" -> "تحويل بنكي"
        "card" -> "بطاقة ائتمان"
        else -> "غير معروفة"
    }

    companion object {
        const val COLLECTION_NAME = "invoices"
    }
}

/**
 * ملاحظات مهمة:
 *
 * 1. **status**: القيم الممكنة:
 *    - draft: مسودة
 *    - pending: معلقة
 *    - completed: مكتملة
 *    - paid: مسددة
 *    - cancelled: ملغاة
 *
 * 2. **paymentMethod**: القيم الممكنة:
 *    - cash: نقداً
 *    - check: شيك
 *    - transfer: تحويل بنكي
 *    - card: بطاقة ائتمان
 */
