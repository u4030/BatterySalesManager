package com.batterysales.data.repositories

import com.batterysales.data.models.Warehouse
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private var cachedWarehouses: List<Warehouse> = emptyList()

    fun getCachedWarehousesSnapshot(): List<Warehouse> = cachedWarehouses

    fun getWarehouseByIdCached(warehouseId: String): Warehouse? {
        return cachedWarehouses.find { it.id == warehouseId }
    }

    /**
     * Continuous snapshot listener streaming warehouses locally from cache.
     * Adheres to the Listen-Only protocol with MetadataChanges.INCLUDE.
     * Rejects false/pending write bounces during mobile websocket reconnections.
     */
    fun getWarehouses(): Flow<List<Warehouse>> = callbackFlow {
        val listenerRegistration = firestore.collection(Warehouse.COLLECTION_NAME)
            .limit(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    if (cachedWarehouses.isNotEmpty()) {
                        trySend(cachedWarehouses)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val warehouses = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Warehouse::class.java)?.copy(id = doc.id)
                    }
                    if (warehouses.isNotEmpty() || !snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) {
                        cachedWarehouses = warehouses
                        trySend(warehouses).isSuccess
                    } else if (cachedWarehouses.isNotEmpty()) {
                        trySend(cachedWarehouses).isSuccess
                    }
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Cache-first single fetch with fallback to SERVER only if CACHE is empty.
     */
    suspend fun getWarehousesOnce(): List<Warehouse> {
        if (cachedWarehouses.isNotEmpty()) return cachedWarehouses
        return try {
            val snapshot = firestore.collection(Warehouse.COLLECTION_NAME)
                .limit(100)
                .get(Source.CACHE)
                .await()
            val warehouses = snapshot.documents.mapNotNull { it.toObject(Warehouse::class.java)?.copy(id = it.id) }
            if (warehouses.isNotEmpty()) {
                cachedWarehouses = warehouses
                warehouses
            } else {
                val serverSnap = firestore.collection(Warehouse.COLLECTION_NAME).limit(100).get().await()
                val serverWarehouses = serverSnap.documents.mapNotNull { it.toObject(Warehouse::class.java)?.copy(id = it.id) }
                cachedWarehouses = serverWarehouses
                serverWarehouses
            }
        } catch (e: Exception) {
            try {
                val snapshot = firestore.collection(Warehouse.COLLECTION_NAME).limit(100).get().await()
                val warehouses = snapshot.documents.mapNotNull { it.toObject(Warehouse::class.java)?.copy(id = it.id) }
                cachedWarehouses = warehouses
                warehouses
            } catch (ex: Exception) {
                cachedWarehouses
            }
        }
    }

    suspend fun addWarehouse(warehouse: Warehouse): Warehouse {
        val docRef = if (warehouse.id.isNotEmpty()) firestore.collection(Warehouse.COLLECTION_NAME).document(warehouse.id)
                     else firestore.collection(Warehouse.COLLECTION_NAME).document()
        val finalWarehouse = warehouse.copy(id = docRef.id)
        docRef.set(finalWarehouse).await()
        return finalWarehouse
    }

    suspend fun getWarehouse(warehouseId: String): Warehouse? {
        return try {
            val snapshot = firestore.collection(Warehouse.COLLECTION_NAME)
                .document(warehouseId)
                .get(Source.CACHE)
                .await()
            snapshot.toObject(Warehouse::class.java)?.copy(id = snapshot.id)
        } catch (e: Exception) {
            val snapshot = firestore.collection(Warehouse.COLLECTION_NAME)
                .document(warehouseId)
                .get()
                .await()
            snapshot.toObject(Warehouse::class.java)?.copy(id = snapshot.id)
        }
    }

    suspend fun updateWarehouse(warehouse: Warehouse) {
        val warehouseRef = firestore.collection(Warehouse.COLLECTION_NAME).document(warehouse.id)
        warehouseRef.set(warehouse).await()
        syncScrapWarehouseName(warehouse.id, warehouse.name)
    }

    private suspend fun syncScrapWarehouseName(parentWarehouseId: String, newName: String) {
        val snapshot = firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME)
            .whereEqualTo("parentWarehouseId", parentWarehouseId)
            .get(Source.CACHE)
            .await()
        
        if (!snapshot.isEmpty) {
            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, "name", "سكراب - $newName")
            }
            batch.commit().await()
        }
    }

    suspend fun deleteWarehouse(warehouseId: String) {
        firestore.collection(Warehouse.COLLECTION_NAME)
            .document(warehouseId)
            .delete()
            .await()
    }
}
