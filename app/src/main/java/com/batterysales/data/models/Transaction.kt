package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Transaction(
//    @DocumentId
    val id: String = "",
    val type: TransactionType = TransactionType.INCOME, // نوع العملية
    val amount: Double = 0.0, // المبلغ
    val description: String = "", // الوصف
    val relatedId: String? = null, // معرف الفاتورة أو المصروف المرتبطة
    val referenceNumber: String = "", // رقم الشيك أو السند
    val warehouseId: String? = null, // المستودع المرتبط (للخزينة لكل مستودع)
    val paymentMethod: String = "cash", // cash, e-wallet, visa
    val createdAt: Date = Date(),
    val notes: String = ""
) {
    companion object {
        const val COLLECTION_NAME = "transactions"
    }
}

//enum class TransactionType {
//    INCOME,    // واردات (من المبيعات)
//    EXPENSE,   // مصروفات
//    PAYMENT,   // دفعة من الذمم
//    REFUND     // استرجاع
//}
