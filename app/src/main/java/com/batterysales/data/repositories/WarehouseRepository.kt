package com.batterysales.data.repositories

import com.batterysales.data.models.Warehouse
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class WarehouseRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    fun getWarehouses(): Flow<List<Warehouse>> = callbackFlow {
        val listenerRegistration = firestore.collection(Warehouse.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val warehouses = snapshot.toObjects(Warehouse::class.java)
                    trySend(warehouses).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addWarehouse(warehouse: Warehouse) {
        firestore.collection(Warehouse.COLLECTION_NAME).add(warehouse).await()
    }

    suspend fun getWarehouse(warehouseId: String): Warehouse? {
        return firestore.collection(Warehouse.COLLECTION_NAME)
            .document(warehouseId)
            .get()
            .await()
            .toObject(Warehouse::class.java)
    }
}
