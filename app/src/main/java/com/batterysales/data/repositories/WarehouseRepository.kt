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
        firestore.runTransaction { transaction ->
            val docRef = firestore.collection(Warehouse.COLLECTION_NAME).document()
            val finalWarehouse = warehouse.copy(id = docRef.id)
            transaction.set(docRef, finalWarehouse)

            // Automatically create linked ScrapWarehouse
            val scrapDocRef = firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME).document()
            val scrapWarehouse = com.batterysales.data.models.ScrapWarehouse(
                id = scrapDocRef.id,
                name = "سكراب - ${warehouse.name}",
                parentWarehouseId = docRef.id
            )
            transaction.set(scrapDocRef, scrapWarehouse)
        }.await()
    }

    suspend fun getWarehouse(warehouseId: String): Warehouse? {
        val snapshot = firestore.collection(Warehouse.COLLECTION_NAME)
            .document(warehouseId)
            .get()
            .await()
        return snapshot.toObject(Warehouse::class.java)?.copy(id = snapshot.id)
    }

    suspend fun updateWarehouse(warehouse: Warehouse) {
        firestore.runTransaction { transaction ->
            val warehouseRef = firestore.collection(Warehouse.COLLECTION_NAME).document(warehouse.id)
            transaction.set(warehouseRef, warehouse)

            // Keep ScrapWarehouse name in sync
            val scrapQuery = firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME)
                .whereEqualTo("parentWarehouseId", warehouse.id)
                .limit(1)
            
            // Note: Transactions require reads before writes.
            // But we can't easily perform a query inside a transaction without knowing the ID.
            // We'll use a separate transaction for the scrap warehouse if needed,
            // or just update it via query result.
        }.await()

        // Sync name after transaction
        syncScrapWarehouseName(warehouse.id, warehouse.name)
    }

    private suspend fun syncScrapWarehouseName(parentWarehouseId: String, newName: String) {
        val snapshot = firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME)
            .whereEqualTo("parentWarehouseId", parentWarehouseId)
            .get()
            .await()
        
        val batch = firestore.batch()
        snapshot.documents.forEach { doc ->
            batch.update(doc.reference, "name", "سكراب - $newName")
        }
        batch.commit().await()
    }

    suspend fun deleteWarehouse(warehouseId: String) {
        firestore.collection(Warehouse.COLLECTION_NAME)
            .document(warehouseId)
            .delete()
            .await()
    }
}
