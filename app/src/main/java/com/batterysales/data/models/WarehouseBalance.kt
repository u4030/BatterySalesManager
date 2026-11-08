package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName

/**
 * Stores financial balance aggregates per warehouse.
 * Enforces bidirectional @get:PropertyName and @set:PropertyName for dual serialization.
 */
data class WarehouseBalance(
    @get:PropertyName("warehouse_name") @set:PropertyName("warehouse_name") var warehouseName: String = "",
    @get:PropertyName("warehouse_id") @set:PropertyName("warehouse_id") var warehouseId: String = "",
    @get:PropertyName("warehouseName") @set:PropertyName("warehouseName") var warehouseNameCamel: String = "",
    @get:PropertyName("warehouseId") @set:PropertyName("warehouseId") var warehouseIdCamel: String = "",
    @get:PropertyName("cashBalance") @set:PropertyName("cashBalance") var cashBalance: Double = 0.0,
    @get:PropertyName("bankBalance") @set:PropertyName("bankBalance") var bankBalance: Double = 0.0,
    @get:PropertyName("pendingCollection") @set:PropertyName("pendingCollection") var pendingCollection: Double = 0.0,
    @get:PropertyName("totalReceivables") @set:PropertyName("totalReceivables") var totalReceivables: Double = 0.0,
    @get:PropertyName("todayCollection") @set:PropertyName("todayCollection") var todayCollection: Double = 0.0,
    @get:PropertyName("todayCollectionCount") @set:PropertyName("todayCollectionCount") var todayCollectionCount: Int = 0,
    @get:PropertyName("lastInvoiceDate") @set:PropertyName("lastInvoiceDate") var lastInvoiceDate: String = ""
) {
    val resolvedWarehouseId: String
        get() = warehouseId.ifBlank { warehouseIdCamel }

    val resolvedWarehouseName: String
        get() = warehouseName.ifBlank { warehouseNameCamel }
}
