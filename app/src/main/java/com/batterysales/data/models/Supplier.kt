package com.batterysales.data.models

import java.util.Date

data class Supplier(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val yearlyTarget: Double = 0.0,
    val totalDebit: Double = 0.0, // Total purchases
    val totalCredit: Double = 0.0, // Total payments
    val resetDate: Date? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "suppliers"
    }
}
