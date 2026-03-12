package com.batterysales.data.models


data class Warehouse(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    var isActive: Boolean = true,
    var isMain: Boolean = false
) {
    companion object {
        const val COLLECTION_NAME = "warehouses"
    }
}
