package com.batterysales.data.models

import java.util.Date

data class SystemAlert(
    val id: String = "",
    val type: String = "", // low_stock, pending_approval, financial_due
    val title: String = "",
    val message: String = "",
    val relatedId: String = "", // variantId, entryId, etc.
    val warehouseId: String? = null,
    val warehouseName: String? = null,
    val timestamp: Date = Date(),
    val isRead: Boolean = false,
    val data: Map<String, Any> = emptyMap()
) {
    companion object {
        const val COLLECTION_NAME = "system_alerts"

        const val TYPE_LOW_STOCK = "low_stock"
        const val TYPE_PENDING_APPROVAL = "pending_approval"
        const val TYPE_FINANCIAL_DUE = "financial_due"
    }
}
