package com.batterysales.data.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class StockEntry(
    @PropertyName("id") var id: String = "",
    @PropertyName("productVariantId") var productVariantId: String = "",
    @PropertyName("productName") var productName: String = "", // Denormalized product name
    @PropertyName("capacity") var capacity: Int = 0, // Denormalized capacity
    @PropertyName("specification") var specification: String = "", // Denormalized specification
    @PropertyName("warehouseId") var warehouseId: String = "",
    @PropertyName("quantity") var quantity: Int = 0,
    @PropertyName("costPrice") var costPrice: Double = 0.0, // Cost per item
    @PropertyName("costPerAmpere") var costPerAmpere: Double = 0.0,
    @PropertyName("totalAmperes") var totalAmperes: Int = 0,
    @PropertyName("totalCost") var totalCost: Double = 0.0,
    @PropertyName("grandTotalAmperes") var grandTotalAmperes: Int = 0,
    @PropertyName("grandTotalCost") var grandTotalCost: Double = 0.0,
    @PropertyName("timestamp") var timestamp: Date = Date(),
    @PropertyName("invoiceDate") var invoiceDate: Date? = null, // تاريخ الفاتورة الفعلي (اختياري)
    @PropertyName("supplier") var supplier: String = "", // Legacy supplier name
    @PropertyName("supplierId") var supplierId: String = "", // Link to Supplier model
    @PropertyName("invoiceId") var invoiceId: String? = null, // Link to invoice for sales entries
    @PropertyName("invoiceNumber") var invoiceNumber: String = "", // Purchase invoice number or reference
    @PropertyName("orderId") var orderId: String = "", // Unique ID for a batch of entries (one order)
    @PropertyName("status") var status: String = "approved", // approved, pending
    @PropertyName("createdBy") var createdBy: String = "",
    @PropertyName("createdByUserName") var createdByUserName: String = "",
    @PropertyName("returnedQuantity") var returnedQuantity: Int = 0,
    @PropertyName("returnDate") var returnDate: Date? = null,
    @PropertyName("remainingBalance") var remainingBalance: Double? = null, // المتبقي من قيمة الطلبية (بعد الكاش والمرتجعات)
    @PropertyName("isSettled") var isSettled: Boolean = false, // هل الطلبية مسددة بالكامل؟
    @PropertyName("settlementNotes") var settlementNotes: List<String> = emptyList(), // ملاحظات التسوية (أرقام الشيكات، المرتجعات، إلخ)
    @PropertyName("linkedAllocations") var linkedAllocations: Map<String, Double> = emptyMap(), // تتبع المبالغ المرتبطة من كل سند (ID -> Amount)
    @PropertyName("netQuantity") var netQuantity: Int? = null,
    @PropertyName("netCost") var netCost: Double? = null,
    @PropertyName("unitPrice") var unitPrice: Double? = null
) {
    constructor() : this(
        id = "",
        productVariantId = "",
        productName = "",
        capacity = 0,
        specification = "",
        warehouseId = "",
        quantity = 0,
        costPrice = 0.0,
        costPerAmpere = 0.0,
        totalAmperes = 0,
        totalCost = 0.0,
        grandTotalAmperes = 0,
        grandTotalCost = 0.0,
        timestamp = Date(),
        invoiceDate = null,
        supplier = "",
        supplierId = "",
        invoiceId = null,
        invoiceNumber = "",
        orderId = "",
        status = "approved",
        createdBy = "",
        createdByUserName = "",
        returnedQuantity = 0,
        returnDate = null,
        remainingBalance = null,
        isSettled = false,
        settlementNotes = emptyList(),
        linkedAllocations = emptyMap(),
        netQuantity = null,
        netCost = null,
        unitPrice = null
    )

    /**
     * Calculates the net impact of this entry on stock levels.
     * For purchases (quantity > 0): quantity - returnedQuantity
     * For sales (quantity < 0): quantity + returnedQuantity (since quantity is negative)
     */
    @Exclude
    fun getNetQuantity(): Int {
        if (netQuantity != null) return netQuantity!!
        return if (quantity >= 0) {
            (quantity - returnedQuantity).coerceAtLeast(0)
        } else {
            (quantity + returnedQuantity).coerceAtMost(0)
        }
    }

    /**
     * يحسب التكلفة الصافية لهذا القيد بعد خصم المرتجعات
     */
    @Exclude
    fun getNetCost(): Double {
        if (netCost != null) return netCost!!
        return if (quantity == 0) 0.0
        else {
            val ratio = getNetQuantity().toDouble() / quantity
            getEffectiveTotalCost() * ratio
        }
    }

    /**
     * يعيد إجمالي التكلفة المسجل، أو يحسبه من السعر والكمية إذا كان مفقوداً (للبيانات القديمة)
     */
    @Exclude
    fun getEffectiveTotalCost(): Double {
        // نستخدم القيمة المطلقة للمقارنة بـ 0.001 لضمان شمول القيم السالبة المسجلة
        return if (Math.abs(totalCost) > 0.001) totalCost else (quantity * costPrice)
    }

    /**
     * يعيد تاريخ الفاتورة الفعلي إذا وجد، وإلا يعيد تاريخ الإدخال
     */
    @Exclude
    fun getEffectiveDate(): Date = invoiceDate ?: timestamp

    /**
     * يحسب تكلفة الوحدة الواحدة بشكل صحيح مع دعم البيانات القديمة والجديدة
     */
    @Exclude
    fun getUnitPrice(): Double {
        if (unitPrice != null) return unitPrice!!
        return if (quantity != 0 && Math.abs(totalCost) > 0.001) Math.abs(totalCost / quantity)
        else costPrice
    }

    companion object {
        const val COLLECTION_NAME = "stock_entries"

        const val STATUS_APPROVED = "approved"
        const val STATUS_PENDING = "pending"
    }
}
