package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class OldBatteryTransaction(
//    @DocumentId
    val id: String = "",
    val invoiceId: String? = null,
    val quantity: Int = 0,
    val warehouseId: String = "",
    val totalAmperes: Double = 0.0,
    val type: OldBatteryTransactionType = OldBatteryTransactionType.INTAKE, // INTAKE (from customer) or SALE (sold to recycler)
    val amount: Double = 0.0, // Only for SALE type
    val date: Date = Date(),
    val notes: String = "",
    val createdByUserName: String = ""
) {
    companion object {
        const val COLLECTION_NAME = "old_battery_transactions"
    }
}

enum class OldBatteryTransactionType {
    INTAKE,
    SALE,
    ADJUSTMENT
}
