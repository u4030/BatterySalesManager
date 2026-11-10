package com.batterysales.data.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Invoice - نموذج بيانات الفواتير
 *
 * يمثل فاتورة مبيعات واحدة في النظام
 */
data class Invoice(
    @PropertyName("id") var id: String = "",
    @PropertyName("invoiceNumber") var invoiceNumber: String = "",
    @PropertyName("customerId") var customerId: String = "",
    @PropertyName("customerName") var customerName: String = "",
    @PropertyName("customerPhone") var customerPhone: String = "",
    @PropertyName("customerAddress") var customerAddress: String = "",
    @PropertyName("items") var items: List<InvoiceItem> = emptyList(),
    @PropertyName("subtotal") var subtotal: Double = 0.0,
    @PropertyName("tax") var tax: Double = 0.0,
    @PropertyName("taxRate") var taxRate: Double = 0.0,
    @PropertyName("discount") var discount: Double = 0.0,
    @PropertyName("discountRate") var discountRate: Double = 0.0,
    @PropertyName("totalAmount") var totalAmount: Double = 0.0,
    @PropertyName("oldBatteriesValue") var oldBatteriesValue: Double = 0.0,
    @PropertyName("oldBatteriesQuantity") var oldBatteriesQuantity: Int = 0,
    @PropertyName("oldBatteriesTotalAmperes") var oldBatteriesTotalAmperes: Double = 0.0,
    @PropertyName("finalAmount") var finalAmount: Double = 0.0,
    @PropertyName("paidAmount") var paidAmount: Double = 0.0,
    @PropertyName("remainingAmount") var remainingAmount: Double = 0.0,
    @PropertyName("status") var status: String = "draft",
    @PropertyName("paymentMethod") var paymentMethod: String = "cash",
    @PropertyName("warehouseId") var warehouseId: String = "",
    @PropertyName("invoiceDate") var invoiceDate: Date = Date(0), // Default to epoch to identify missing data in older documents
    @PropertyName("dueDate") var dueDate: Date = Date(),
    @PropertyName("paidDate") var paidDate: Date? = null,
    @PropertyName("notes") var notes: String = "",
    @PropertyName("sellerId") var sellerId: String = "",
    @PropertyName("sellerName") var sellerName: String = "",
    @PropertyName("createdAt") var createdAt: Date = Date(),
    @PropertyName("updatedAt") var updatedAt: Date = Date(),
    @PropertyName("partiallyPaid") var partiallyPaid: Boolean? = null,
    @PropertyName("paymentMethodLabel") var paymentMethodLabelStr: String? = null,
    @PropertyName("statusLabel") var statusLabelStr: String? = null
) {
    constructor() : this(
        id = "",
        invoiceNumber = "",
        customerId = "",
        customerName = "",
        customerPhone = "",
        customerAddress = "",
        items = emptyList(),
        subtotal = 0.0,
        tax = 0.0,
        taxRate = 0.0,
        discount = 0.0,
        discountRate = 0.0,
        totalAmount = 0.0,
        oldBatteriesValue = 0.0,
        oldBatteriesQuantity = 0,
        oldBatteriesTotalAmperes = 0.0,
        finalAmount = 0.0,
        paidAmount = 0.0,
        remainingAmount = 0.0,
        status = "draft",
        paymentMethod = "cash",
        warehouseId = "",
        invoiceDate = Date(0),
        dueDate = Date(),
        paidDate = null,
        notes = "",
        sellerId = "",
        sellerName = "",
        createdAt = Date(),
        updatedAt = Date(),
        partiallyPaid = null,
        paymentMethodLabelStr = null,
        statusLabelStr = null
    )

    // ============================================
    // دوال مساعدة
    // ============================================

    @Exclude
    fun isPaid(): Boolean = paidAmount >= totalAmount
    @Exclude
    fun isPending(): Boolean = status == "pending"
    @Exclude
    fun isCompleted(): Boolean = status == "completed"
    @Exclude
    fun isDraft(): Boolean = status == "draft"
    @Exclude
    fun isCancelled(): Boolean = status == "cancelled"
    @Exclude
    fun isOverdue(): Boolean {
        val now = Date()
        return now.after(dueDate) && !isPaid()
    }
    @Exclude
    fun isPartiallyPaid(): Boolean = paidAmount > 0 && paidAmount < totalAmount
    @Exclude
    fun calculateRemainingAmount(): Double = (totalAmount - paidAmount).coerceAtLeast(0.0)
    @Exclude
    fun daysUntilDue(): Long {
        val now = Date()
        val diffInMillis = dueDate.time - now.time
        return diffInMillis / (1000 * 60 * 60 * 24)
    }
    @Exclude
    fun daysOverdue(): Long {
        if (!isOverdue()) return 0
        return daysUntilDue() * -1
    }
    @Exclude
    fun calculateLateFee(): Double {
        if (!isOverdue()) return 0.0
        val daysOverdue = daysOverdue()
        return remainingAmount * 0.005 * daysOverdue
    }
    @Exclude
    fun getStatusLabel(): String = when (status) {
        "draft" -> "مسودة"
        "pending" -> "معلقة"
        "completed" -> "مكتملة"
        "paid" -> "مسددة"
        "cancelled" -> "ملغاة"
        else -> "غير معروفة"
    }
    @Exclude
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
