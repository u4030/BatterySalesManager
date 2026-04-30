package com.batterysales.data.models

import java.util.Date

data class StockEntry(
    val id: String = "",
    val productVariantId: String = "",
    val productName: String = "", // Denormalized product name
    val capacity: Int = 0, // Denormalized capacity
    val warehouseId: String = "",
    val quantity: Int = 0,
    val costPrice: Double = 0.0, // Cost per item
    val costPerAmpere: Double = 0.0,
    val totalAmperes: Int = 0,
    val totalCost: Double = 0.0,
    val grandTotalAmperes: Int = 0,
    val grandTotalCost: Double = 0.0,
    val timestamp: Date = Date(),
    val invoiceDate: Date? = null, // تاريخ الفاتورة الفعلي (اختياري)
    val supplier: String = "", // Legacy supplier name
    val supplierId: String = "", // Link to Supplier model
    val invoiceId: String? = null, // Link to invoice for sales entries
    val invoiceNumber: String = "", // Purchase invoice number or reference
    val orderId: String = "", // Unique ID for a batch of entries (one order)
    val status: String = "approved", // approved, pending
    val createdBy: String = "",
    val createdByUserName: String = "",
    val returnedQuantity: Int = 0,
    val returnDate: Date? = null
) {
    /**
     * Calculates the net impact of this entry on stock levels.
     * For purchases (quantity > 0): quantity - returnedQuantity
     * For sales (quantity < 0): quantity + returnedQuantity (since quantity is negative)
     */
    fun getNetQuantity(): Int {
        return if (quantity >= 0) {
            (quantity - returnedQuantity).coerceAtLeast(0)
        } else {
            (quantity + returnedQuantity).coerceAtMost(0)
        }
    }

    /**
     * يحسب التكلفة الصافية لهذا القيد بعد خصم المرتجعات.
     * القيمة الناتجة تكون موجبة للمشتريات وسالبة للمبيعات/المرتجعات.
     */
    fun getNetCost(): Double {
        return if (quantity == 0) 0.0
        else {
            val ratio = getNetQuantity().toDouble() / quantity
            getEffectiveTotalCost() * ratio
        }
    }

    /**
     * يعيد إجمالي التكلفة مع مراعاة الإشارة (موجب للمشتريات، سالب للمبيعات).
     * يعتمد على totalCost المسجل أو يحسبه من السعر والكمية.
     */
    fun getEffectiveTotalCost(): Double {
        val sign = if (quantity >= 0) 1.0 else -1.0
        val cost = if (Math.abs(totalCost) > 0.001) Math.abs(totalCost) else (Math.abs(quantity.toDouble()) * costPrice)
        return cost * sign
    }

    /**
     * يعيد تاريخ الفاتورة الفعلي إذا وجد، وإلا يعيد تاريخ الإدخال
     */
    fun getEffectiveDate(): Date = invoiceDate ?: timestamp

    companion object {
        const val COLLECTION_NAME = "stock_entries"

        const val STATUS_APPROVED = "approved"
        const val STATUS_PENDING = "pending"
    }
}
