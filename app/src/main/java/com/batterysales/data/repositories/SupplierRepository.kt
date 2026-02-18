package com.batterysales.data.repositories

import com.batterysales.data.models.Supplier
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplierRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getSuppliers(): Flow<List<Supplier>> = callbackFlow {
        val listenerRegistration = firestore.collection(Supplier.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val suppliers = snapshot.documents.mapNotNull { 
                        it.toObject(Supplier::class.java)?.copy(id = it.id) 
                    }
                    trySend(suppliers).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getSuppliersOnce(): List<Supplier> {
        val snapshot = firestore.collection(Supplier.COLLECTION_NAME).get().await()
        return snapshot.documents.mapNotNull { it.toObject(Supplier::class.java)?.copy(id = it.id) }
    }

    suspend fun getSupplier(supplierId: String): Supplier? {
        val snapshot = firestore.collection(Supplier.COLLECTION_NAME)
            .document(supplierId)
            .get()
            .await()
        return snapshot.toObject(Supplier::class.java)?.copy(id = snapshot.id)
    }

    suspend fun addSupplier(supplier: Supplier) {
        val docRef = firestore.collection(Supplier.COLLECTION_NAME).document()
        val finalSupplier = supplier.copy(id = docRef.id)
        docRef.set(finalSupplier).await()
    }

    suspend fun updateSupplier(supplier: Supplier) {
        firestore.collection(Supplier.COLLECTION_NAME)
            .document(supplier.id)
            .set(supplier)
            .await()
    }

    suspend fun deleteSupplier(supplierId: String) {
        firestore.collection(Supplier.COLLECTION_NAME)
            .document(supplierId)
            .delete()
            .await()
    }
}
