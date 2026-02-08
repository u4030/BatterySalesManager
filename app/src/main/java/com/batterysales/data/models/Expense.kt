package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Expense(
//    @DocumentId
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val timestamp: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "expenses"
    }
}
