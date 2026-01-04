package com.batterysales.data.models

import java.util.Date

data class Client(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val totalPurchases: Double = 0.0,
    val balance: Double = 0.0,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "clients"
    }
}
