package com.batterysales.data.models

data class ScrapWarehouse(
    val id: String = "",
    val name: String = "",
    val parentWarehouseId: String = "",
    val totalQuantity: Int = 0,
    val totalAmperes: Double = 0.0,
    val isActive: Boolean = true
) {
    companion object {
        const val COLLECTION_NAME = "scrap_warehouses"
    }
}
