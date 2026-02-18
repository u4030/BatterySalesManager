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
                    val warehouses = snapshot.documents.mapNotNull { it.toObject(Warehouse::class.java)?.copy(id = it.id) }
                    trySend(warehouses).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getWarehousesOnce(): List<Warehouse> {
        val snapshot = firestore.collection(Warehouse.COLLECTION_NAME).get().await()
        return snapshot.documents.mapNotNull { it.toObject(Warehouse::class.java)?.copy(id = it.id) }
    }

    suspend fun addWarehouse(warehouse: Warehouse) {
        val docRef = firestore.collection(Warehouse.COLLECTION_NAME).document()
        val finalWarehouse = warehouse.copy(id = docRef.id)
        docRef.set(finalWarehouse).await()
    }

    suspend fun getWarehouse(warehouseId: String): Warehouse? {
        val snapshot = firestore.collection(Warehouse.COLLECTION_NAME)
            .document(warehouseId)
            .get()
            .await()
        return snapshot.toObject(Warehouse::class.java)?.copy(id = snapshot.id)
    }

    suspend fun updateWarehouse(warehouse: Warehouse) {
        firestore.collection(Warehouse.COLLECTION_NAME)
            .document(warehouse.id)
            .set(warehouse)
            .await()
    }

    suspend fun deleteWarehouse(warehouseId: String) {
        firestore.collection(Warehouse.COLLECTION_NAME)
            .document(warehouseId)
            .delete()
            .await()
    }
}
