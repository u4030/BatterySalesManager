package com.batterysales.data.repositories

import com.batterysales.data.models.ScrapWarehouse
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ScrapWarehouseRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getScrapWarehouses(): Flow<List<ScrapWarehouse>> = callbackFlow {
        val listenerRegistration = firestore.collection(ScrapWarehouse.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val warehouses = snapshot.documents.mapNotNull { it.toObject(ScrapWarehouse::class.java)?.copy(id = it.id) }
                    trySend(warehouses).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Listen directly to a specific warehouse's scrap document (scrap_warehouses/scrap_wh_$parentWarehouseId).
     * Live snapshot stream with Zero Quota Consumption when connected to cache.
     */
    fun getScrapWarehouseFlow(parentWarehouseId: String): Flow<ScrapWarehouse?> = callbackFlow {
        if (parentWarehouseId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val docId = "scrap_wh_$parentWarehouseId"
        val listenerRegistration = firestore.collection(ScrapWarehouse.COLLECTION_NAME)
            .document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val scrap = snapshot?.toObject(ScrapWarehouse::class.java)?.copy(id = snapshot.id)
                trySend(scrap)
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getScrapWarehouseByParentId(parentWarehouseId: String): ScrapWarehouse? {
        val snapshot = firestore.collection(ScrapWarehouse.COLLECTION_NAME)
            .whereEqualTo("parentWarehouseId", parentWarehouseId)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(ScrapWarehouse::class.java)?.copy(id = snapshot.documents.first().id)
    }

    suspend fun createScrapWarehouse(scrapWarehouse: ScrapWarehouse): String {
        val docRef = firestore.collection(ScrapWarehouse.COLLECTION_NAME).document()
        val finalWarehouse = scrapWarehouse.copy(id = docRef.id)
        docRef.set(finalWarehouse).await()
        return finalWarehouse.id
    }

    suspend fun updateScrapWarehouse(scrapWarehouse: ScrapWarehouse) {
        firestore.collection(ScrapWarehouse.COLLECTION_NAME)
            .document(scrapWarehouse.id)
            .set(scrapWarehouse)
            .await()
    }

    suspend fun syncScrapWarehouseName(parentWarehouseId: String, newName: String) {
        val existing = getScrapWarehouseByParentId(parentWarehouseId)
        if (existing != null) {
            val updatedName = "سكراب - $newName"
            firestore.collection(ScrapWarehouse.COLLECTION_NAME)
                .document(existing.id)
                .update("name", updatedName)
                .await()
        }
    }
}
 
