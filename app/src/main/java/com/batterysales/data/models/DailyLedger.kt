package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Model representing daily ledger documents in summaries/daily_${warehouseId}_${dateStr}.
 * Enforces bidirectional @get:PropertyName and @set:PropertyName for snake_case and camelCase.
 */
data class DailyLedger(
    @get:PropertyName("warehouse_name") @set:PropertyName("warehouse_name") var warehouseName: String = "",
    @get:PropertyName("warehouse_id") @set:PropertyName("warehouse_id") var warehouseId: String = "",
    @get:PropertyName("warehouseName") @set:PropertyName("warehouseName") var warehouseNameCamel: String = "",
    @get:PropertyName("warehouseId") @set:PropertyName("warehouseId") var warehouseIdCamel: String = "",
    @get:PropertyName("date") @set:PropertyName("date") var date: String = "",
    @get:PropertyName("totalSales") @set:PropertyName("totalSales") var totalSales: Double = 0.0,
    @get:PropertyName("totalInvoices") @set:PropertyName("totalInvoices") var totalInvoices: Int = 0,
    @get:PropertyName("totalPaid") @set:PropertyName("totalPaid") var totalPaid: Double = 0.0,
    @get:PropertyName("totalDebt") @set:PropertyName("totalDebt") var totalDebt: Double = 0.0,
    @get:PropertyName("unsettledCashBalance") @set:PropertyName("unsettledCashBalance") var unsettledCashBalance: Double = 0.0,
    @get:PropertyName("isSettled") @set:PropertyName("isSettled") var isSettled: Boolean = false,
    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated") var lastUpdated: Date = Date()
) {
    val resolvedWarehouseId: String
        get() = warehouseId.ifBlank { warehouseIdCamel }

    val resolvedWarehouseName: String
        get() = warehouseName.ifBlank { warehouseNameCamel }
}
