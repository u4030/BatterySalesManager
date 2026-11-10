package com.batterysales.data.models

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class SystemAlert(
    @PropertyName("id") var id: String = "",
    @PropertyName("type") var type: String = "", // low_stock, pending_approval, financial_due
    @PropertyName("title") var title: String = "",
    @PropertyName("message") var message: String = "",
    @PropertyName("relatedId") var relatedId: String = "", // variantId, entryId, etc.
    @PropertyName("variantId") var variantId: String = "",
    @PropertyName("warehouseId") var warehouseId: String? = null,
    @PropertyName("warehouseName") var warehouseName: String? = null,
    @PropertyName("resolved") var resolved: Boolean = false,
    @PropertyName("status") var status: String = "active",
    @PropertyName("timestamp") var timestamp: Date = Date(),
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
    @get:PropertyName("read") @set:PropertyName("read") var readAlert: Boolean = false,
    @PropertyName("currentStock") var currentStock: Any? = null,
    @PropertyName("threshold") var threshold: Any? = null,
    @PropertyName("capacity") var capacity: Any? = null,
    @PropertyName("specification") var specification: String = "",
    @PropertyName("data") var data: Map<String, Any> = emptyMap()
) {
    constructor() : this(
        id = "",
        type = "",
        title = "",
        message = "",
        relatedId = "",
        variantId = "",
        warehouseId = null,
        warehouseName = null,
        resolved = false,
        status = "active",
        timestamp = Date(),
        isRead = false,
        readAlert = false,
        currentStock = null,
        threshold = null,
        capacity = null,
        specification = "",
        data = emptyMap()
    )

    companion object {
        const val COLLECTION_NAME = "system_alerts"
        
        const val TYPE_LOW_STOCK = "low_stock"
        const val TYPE_PENDING_APPROVAL = "pending_approval"
        const val TYPE_FINANCIAL_DUE = "financial_due"
    }
}
