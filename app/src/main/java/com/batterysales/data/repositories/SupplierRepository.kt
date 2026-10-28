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
    private val firestore: FirebaseFirestore,
    private val summaryRepository: SummaryRepository
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

    suspend fun getSuppliersOnce(query: String = ""): List<Supplier> {
        var baseQuery: com.google.firebase.firestore.Query = firestore.collection(Supplier.COLLECTION_NAME)
        
        if (query.isNotBlank()) {
            baseQuery = baseQuery.whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + "\uf8ff")
                .orderBy("name")
        } else {
            baseQuery = baseQuery.orderBy("name")
        }
        
        val snapshot = baseQuery.get().await()
        return snapshot.documents.mapNotNull { it.toObject(Supplier::class.java)?.copy(id = it.id) }
    }

    fun getSupplierFlow(supplierId: String): Flow<Supplier?> = callbackFlow {
        val listenerRegistration = firestore.collection(Supplier.COLLECTION_NAME)
            .document(supplierId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val supplier = snapshot.toObject(Supplier::class.java)?.copy(id = snapshot.id)
                    trySend(supplier).isSuccess
                } else {
                    trySend(null).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getSupplier(supplierId: String): Supplier? {
        if (supplierId.isEmpty()) return null
        return try {
            val snapshot = firestore.collection(Supplier.COLLECTION_NAME)
                .document(supplierId)
                .get(com.google.firebase.firestore.Source.CACHE)
                .await()
            val supplier = snapshot.toObject(Supplier::class.java)?.copy(id = snapshot.id)
            if (supplier != null) return supplier

            val overview = summaryRepository.getSuppliersOverview(forceRefresh = false)
            val summaryItem = overview?.suppliers?.get(supplierId)
            summaryItem?.let {
                Supplier(
                    id = it.supplierId,
                    name = it.name,
                    totalDebit = it.totalDebit,
                    totalCredit = it.totalCredit,
                    currentBalance = it.currentBalance
                )
            }
        } catch (e: Exception) {
            val overview = summaryRepository.getSuppliersOverview(forceRefresh = false)
            val summaryItem = overview?.suppliers?.get(supplierId)
            summaryItem?.let {
                Supplier(
                    id = it.supplierId,
                    name = it.name,
                    totalDebit = it.totalDebit,
                    totalCredit = it.totalCredit,
                    currentBalance = it.currentBalance
                )
            }
        }
    }

    suspend fun addSupplier(supplier: Supplier): Supplier {
        val docRef = if (supplier.id.isNotEmpty()) firestore.collection(Supplier.COLLECTION_NAME).document(supplier.id)
                     else firestore.collection(Supplier.COLLECTION_NAME).document()
        val finalSupplier = supplier.copy(id = docRef.id)
        docRef.set(finalSupplier).await()
        return finalSupplier
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
 
