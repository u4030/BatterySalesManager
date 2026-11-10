package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Represents a single item in the inventory summary list.
 */
data class InventorySummaryItem(
    @PropertyName("variantId") var variantId: String = "",
    @PropertyName("productId") var productId: String = "",
    @PropertyName("productName") var productName: String = "",
    @PropertyName("capacity") var capacity: Int = 0,
    @PropertyName("specification") var specification: String = "",
    @PropertyName("barcode") var barcode: String = "",
    @PropertyName("currentStock") var currentStock: Int = 0,
    @PropertyName("minQuantity") var minQuantity: Int = 0,
    @PropertyName("weightedAverageCost") var weightedAverageCost: Double = 0.0,
    @PropertyName("sellingPrice") var sellingPrice: Double = 0.0,
    @PropertyName("updatedAt") var updatedAt: Date = Date(),
    @get:PropertyName("isDiscontinued") @set:PropertyName("isDiscontinued")
    var isDiscontinued: Boolean = false
) {
    constructor() : this(
        variantId = "",
        productId = "",
        productName = "",
        capacity = 0,
        specification = "",
        barcode = "",
        currentStock = 0,
        minQuantity = 0,
        weightedAverageCost = 0.0,
        sellingPrice = 0.0,
        updatedAt = Date(),
        isDiscontinued = false
    )
}

/**
 * Represents the entire inventory state for a warehouse or globally.
 * Stored in summaries/inventory_global or summaries/inventory_wh_{id}
 */
data class InventorySummary(
    @PropertyName("id") var id: String = "",
    @PropertyName("warehouseId") var warehouseId: String? = null, // null for global
    @PropertyName("items") var items: Map<String, InventorySummaryItem> = emptyMap(), // Keyed by variantId
    @PropertyName("lastUpdated") var lastUpdated: Date = Date(),
    @PropertyName("totalItemsCount") var totalItemsCount: Int = 0,
    @PropertyName("totalValue") var totalValue: Double = 0.0,
    @PropertyName("version") var version: Long = 0
) {
    constructor() : this(
        id = "",
        warehouseId = null,
        items = emptyMap(),
        lastUpdated = Date(),
        totalItemsCount = 0,
        totalValue = 0.0,
        version = 0
    )
}

/**
 * Represents a single item in the supplier overview list.
 */
data class SupplierSummaryItem(
    @PropertyName("supplierId") var supplierId: String = "",
    @PropertyName("name") var name: String = "",
    @PropertyName("currentBalance") var currentBalance: Double = 0.0,
    @PropertyName("totalDebit") var totalDebit: Double = 0.0,
    @PropertyName("totalCredit") var totalCredit: Double = 0.0,
    @PropertyName("updatedAt") var updatedAt: Date = Date()
) {
    constructor() : this(
        supplierId = "",
        name = "",
        currentBalance = 0.0,
        totalDebit = 0.0,
        totalCredit = 0.0,
        updatedAt = Date()
    )
}

/**
 * Represents the overall state of all suppliers.
 * Stored in summaries/suppliers_overview
 */
data class SuppliersOverview(
    @PropertyName("id") var id: String = "suppliers_overview",
    @PropertyName("suppliers") var suppliers: Map<String, SupplierSummaryItem> = emptyMap(), // Keyed by supplierId
    @PropertyName("lastUpdated") var lastUpdated: Date = Date(),
    @PropertyName("totalSupplierDebt") var totalSupplierDebt: Double = 0.0,
    @PropertyName("version") var version: Long = 0
) {
    constructor() : this(
        id = "suppliers_overview",
        suppliers = emptyMap(),
        lastUpdated = Date(),
        totalSupplierDebt = 0.0,
        version = 0
    )
}

/**
 * Cache for a specific supplier's FIFO report results.
 * Stored in suppliers/{id}/report_cache
 */
data class SupplierReportCache(
    @PropertyName("supplierId") var supplierId: String = "",
    @PropertyName("balance") var balance: Double = 0.0,
    @PropertyName("totalDebit") var totalDebit: Double = 0.0,
    @PropertyName("totalCredit") var totalCredit: Double = 0.0,
    @PropertyName("regularOrders") var regularOrders: List<Map<String, Any>> = emptyList(), // Serialized PurchaseOrderItem data
    @PropertyName("obligatedOrders") var obligatedOrders: List<Map<String, Any>> = emptyList(),
    @PropertyName("lastCalculated") var lastCalculated: Date = Date()
) {
    constructor() : this(
        supplierId = "",
        balance = 0.0,
        totalDebit = 0.0,
        totalCredit = 0.0,
        regularOrders = emptyList(),
        obligatedOrders = emptyList(),
        lastCalculated = Date()
    )
}
