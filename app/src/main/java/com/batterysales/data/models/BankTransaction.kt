package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class BankTransaction(
//    @DocumentId
    val id: String = "",
    val billId: String? = null,
    val amount: Double = 0.0,
    val type: BankTransactionType = BankTransactionType.DEPOSIT,
    val description: String = "",
    val referenceNumber: String = "",
    val date: Date = Date(),
    val notes: String = ""
) {
    companion object {
        const val COLLECTION_NAME = "bank_transactions"
    }
}

enum class BankTransactionType {
    DEPOSIT,
    WITHDRAWAL
}
