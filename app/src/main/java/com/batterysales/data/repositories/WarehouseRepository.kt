package com.batterysales.data.repositories

import com.batterysales.data.models.Warehouse
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class WarehouseRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getWarehouses(): List<Warehouse> {
        return firestore.collection(Warehouse.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(Warehouse::class.java)
    }

    suspend fun addWarehouse(warehouse: Warehouse) {
        firestore.collection(Warehouse.COLLECTION_NAME).add(warehouse).await()
    }
}
