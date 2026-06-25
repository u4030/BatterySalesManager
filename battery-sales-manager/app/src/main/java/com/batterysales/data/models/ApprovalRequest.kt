package com.batterysales.data.models

import java.util.Date

data class ApprovalRequest(
    val id: String = "",
    val requesterId: String = "",
    val requesterName: String = "",
    val targetType: String = "", // PRODUCT, VARIANT
    val actionType: String = "", // EDIT, DELETE
    val targetId: String = "",
    val productName: String = "",
    val variantCapacity: String = "",
    val productData: Product? = null,
    val variantData: ProductVariant? = null,
    val oldProductData: Product? = null,
    val oldVariantData: ProductVariant? = null,
    val status: String = STATUS_PENDING,
    val timestamp: Date = Date(),
    val adminId: String? = null,
    val adminNote: String? = null
) {
    companion object {
        const val COLLECTION_NAME = "approval_requests"
        
        const val TARGET_PRODUCT = "PRODUCT"
        const val TARGET_VARIANT = "VARIANT"
        
        const val ACTION_EDIT = "EDIT"
        const val ACTION_DELETE = "DELETE"
        
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
    }
}
