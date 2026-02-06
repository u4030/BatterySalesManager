package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Payment(
//    @DocumentId
    val id: String = "",
    val invoiceId: String = "", // معرف الفاتورة المرتبطة
    val amount: Double = 0.0, // مبلغ الدفعة
    val paymentDate: Date = Date(),
    val paymentMethod: String = "", // نقد، شيك، تحويل، إلخ
    val notes: String = "",
    val createdAt: Date = Date(),
    val timestamp: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "payments"
    }
}
