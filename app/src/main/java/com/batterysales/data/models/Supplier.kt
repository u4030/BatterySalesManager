package com.batterysales.data.models

import java.util.Date

data class Supplier(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val yearlyTarget: Double = 0.0,
    val yearlyTarget2: Double = 0.0,
    val yearlyTarget3: Double = 0.0,
    val resetDate: Date? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    companion object {
        const val COLLECTION_NAME = "suppliers"
    }
}
