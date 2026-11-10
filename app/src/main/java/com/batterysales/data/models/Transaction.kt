package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class Transaction(
    @PropertyName("id") var id: String = "",
    @PropertyName("type") var type: TransactionType = TransactionType.INCOME, // نوع العملية
    @PropertyName("amount") var amount: Double = 0.0, // المبلغ
    @PropertyName("description") var description: String = "", // الوصف
    @PropertyName("relatedId") var relatedId: String? = null, // معرف الفاتورة أو المصروف المرتبطة
    @PropertyName("referenceNumber") var referenceNumber: String = "", // رقم الشيك أو السند
    @PropertyName("warehouseId") var warehouseId: String? = null, // المستودع المرتبط (للخزينة لكل مستودع)
    @PropertyName("paymentMethod") var paymentMethod: String = "cash", // cash, e-wallet, visa
    @PropertyName("createdAt") var createdAt: Date = Date(),
    @PropertyName("notes") var notes: String = "",
    @get:PropertyName("isSystemManaged") @set:PropertyName("isSystemManaged") var isSystemManaged: Boolean = false,
    @get:PropertyName("systemManaged") @set:PropertyName("systemManaged") var systemManagedFlag: Boolean = false,
    @get:PropertyName("auditNotes") @set:PropertyName("auditNotes") var auditNotes: List<String> = emptyList()
) {
    constructor() : this(
        id = "",
        type = TransactionType.INCOME,
        amount = 0.0,
        description = "",
        relatedId = null,
        referenceNumber = "",
        warehouseId = null,
        paymentMethod = "cash",
        createdAt = Date(),
        notes = "",
        isSystemManaged = false,
        systemManagedFlag = false,
        auditNotes = emptyList()
    )

    companion object {
        const val COLLECTION_NAME = "transactions"
    }
}
