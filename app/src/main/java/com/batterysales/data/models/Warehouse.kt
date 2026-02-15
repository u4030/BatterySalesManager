package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId

data class Warehouse(
//    @DocumentId
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val isActive: Boolean = true
) {
    companion object {
        const val COLLECTION_NAME = "warehouses"
    }
}
