package com.batterysales.data.models

import java.util.Date

data class InventoryReportItem(
    val product: Product,
    val variant: ProductVariant,
    val warehouseQuantities: Map<String, Int>, // WarehouseID to Quantity
    val totalQuantity: Int,
    val averageCost: Double,
    val totalCostValue: Double
)

data class SupplierReportItem(
    val supplier: Supplier,
    val totalDebit: Double, // Purchases
    val totalCredit: Double, // Payments
    val balance: Double, // Debit - Credit
    val targetProgress: Double,
    val regularOrders: List<PurchaseOrderItem>, // Orders NOT linked to checks/bills
    val obligatedOrders: List<PurchaseOrderItem> // Orders linked to checks/bills
)

data class PurchaseOrderItem(
    val entry: StockEntry,
    val linkedPaidAmount: Double,
    val remainingBalance: Double,
    val referenceNumbers: List<String> = emptyList(),
    val items: List<StockEntry> = emptyList(),
    val autoLinkedAmount: Double = 0.0,
    val hasManualLink: Boolean = false,
    val totalActualPaid: Double = 0.0,
    val totalLinkedAmount: Double = 0.0,
    val coverageSummary: String = "",
    val isCleared: Boolean = false // Fully paid by cash or cleared checks
)

data class LedgerItem(
    val entry: StockEntry,
    val warehouseName: String
)
