package com.batterysales.data.repositories

import com.batterysales.data.models.ScrapWarehouse
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
}
