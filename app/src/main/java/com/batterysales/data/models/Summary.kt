package com.batterysales.data.models

import java.util.Date

/**
 * Represents a single item in the inventory summary list.
 */
data class InventorySummaryItem(
    val variantId: String = "",
    val productId: String = "",
    val productName: String = "",
    val capacity: Int = 0,
    var specification: String = "",
    val barcode: String = "",
    val currentStock: Int = 0,
    val minQuantity: Int = 0,
    val weightedAverageCost: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val updatedAt: Date = Date()
)

/**
 * Represents the entire inventory state for a warehouse or globally.
 * Stored in summaries/inventory_global or summaries/inventory_wh_{id}
 */
data class InventorySummary(
    val id: String = "",
    val warehouseId: String? = null, // null for global
    val items: Map<String, InventorySummaryItem> = emptyMap(), // Keyed by variantId
    val lastUpdated: Date = Date(),
    val totalItemsCount: Int = 0,
    val totalValue: Double = 0.0,
    val version: Long = 0
)

/**
 * Represents a single item in the supplier overview list.
 */
data class SupplierSummaryItem(
    val supplierId: String = "",
    val name: String = "",
    val currentBalance: Double = 0.0,
    val totalDebit: Double = 0.0,
    val totalCredit: Double = 0.0,
    val updatedAt: Date = Date()
)

/**
 * Represents the overall state of all suppliers.
 * Stored in summaries/suppliers_overview
 */
data class SuppliersOverview(
    val id: String = "suppliers_overview",
    val suppliers: Map<String, SupplierSummaryItem> = emptyMap(), // Keyed by supplierId
    val lastUpdated: Date = Date(),
    val totalSupplierDebt: Double = 0.0,
    val version: Long = 0
)

/**
 * Cache for a specific supplier's FIFO report results.
 * Stored in suppliers/{id}/report_cache
 */
data class SupplierReportCache(
    val supplierId: String = "",
    val balance: Double = 0.0,
    val totalDebit: Double = 0.0,
    val totalCredit: Double = 0.0,
    val regularOrders: List<Map<String, Any>> = emptyList(), // Serialized PurchaseOrderItem data
    val obligatedOrders: List<Map<String, Any>> = emptyList(),
    val lastCalculated: Date = Date()
)
