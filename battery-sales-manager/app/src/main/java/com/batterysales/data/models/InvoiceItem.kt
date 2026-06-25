package com.batterysales.data.models

/**
 * InvoiceItem - عنصر من عناصر الفاتورة
 *
 * يمثل منتجاً واحداً أو خدمة داخل الفاتورة مع تفاصيل الكمية والسعر والضرائب.
 */
data class InvoiceItem(
    val productId: String = "",
    val productName: String = "",
    val description: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0,
    val itemDiscount: Double = 0.0,
    val itemTax: Double = 0.0,
    val total: Double = 0.0,
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
) {
    /**
     * حساب الإجمالي قبل الخصم والضريبة
     */
    fun calculateTotal(): Double = quantity * price

    /**
     * حساب الإجمالي بعد الخصم وقبل الضريبة
     */
    fun calculateTotalAfterDiscount(): Double = calculateTotal() - itemDiscount

    /**
     * حساب الإجمالي النهائي شاملاً الضريبة
     */
    fun calculateFinalTotal(): Double = calculateTotalAfterDiscount() + itemTax
}
