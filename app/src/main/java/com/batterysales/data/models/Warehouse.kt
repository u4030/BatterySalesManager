package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Warehouse(
//    @DocumentId
    val id: String = "",
    val name: String = "",
    val location: String = "",
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true
) {
    companion object {
        const val COLLECTION_NAME = "warehouses"
    }
}
