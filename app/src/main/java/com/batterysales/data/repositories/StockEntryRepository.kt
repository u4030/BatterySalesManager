package com.batterysales.data.repositories

import com.batterysales.data.models.StockEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class StockEntryRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun addStockEntry(stockEntry: StockEntry) {
        firestore.collection(StockEntry.COLLECTION_NAME)
            .add(stockEntry)
            .await()
    }

    suspend fun addStockEntries(stockEntries: List<StockEntry>) {
        val batch = firestore.batch()
        stockEntries.forEach { entry ->
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
            batch.set(docRef, entry)
        }
        batch.commit().await()
    }

    fun getAllStockEntriesFlow(): Flow<List<StockEntry>> = callbackFlow {
        val listenerRegistration = firestore.collection(StockEntry.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val entries = snapshot.toObjects(StockEntry::class.java)
                    trySend(entries).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getAllStockEntries(): List<StockEntry> {
        return firestore.collection(StockEntry.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(StockEntry::class.java)
    }

    suspend fun transferStock(
        productVariantId: String,
        sourceWarehouseId: String,
        destinationWarehouseId: String,
        quantity: Int,
        status: String = "approved",
        createdBy: String = ""
    ) {
        val batch = firestore.batch()

        // Create a negative stock entry for the source warehouse
        val sourceStockEntry = StockEntry(
            productVariantId = productVariantId,
            warehouseId = sourceWarehouseId,
            quantity = -quantity,
            costPrice = 0.0, // Cost is already accounted for
            status = status,
            createdBy = createdBy
        )
        val sourceDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        batch.set(sourceDocRef, sourceStockEntry)

        // Create a positive stock entry for the destination warehouse
        val destinationStockEntry = StockEntry(
            productVariantId = productVariantId,
            warehouseId = destinationWarehouseId,
            quantity = quantity,
            costPrice = 0.0, // Cost is already accounted for
            status = status,
            createdBy = createdBy
        )
        val destinationDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        batch.set(destinationDocRef, destinationStockEntry)

        batch.commit().await()
    }

    fun getEntriesForVariant(productVariantId: String): Flow<List<StockEntry>> = callbackFlow {
        val listenerRegistration = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", productVariantId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val entries = snapshot.toObjects(StockEntry::class.java)
                    trySend(entries).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }


    suspend fun getStockEntryById(entryId: String): StockEntry? {
        return firestore.collection(StockEntry.COLLECTION_NAME)
            .document(entryId)
            .get()
            .await()
            .toObject(StockEntry::class.java)
    }

    suspend fun updateStockEntry(entry: StockEntry) {
        firestore.collection(StockEntry.COLLECTION_NAME)
            .document(entry.id)
            .set(entry)
            .await()
    }

    suspend fun deleteStockEntry(entryId: String) {
        firestore.collection(StockEntry.COLLECTION_NAME)
            .document(entryId)
            .delete()
            .await()
    }

    suspend fun getEntriesForInvoice(invoiceId: String): List<StockEntry> {
        return firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get()
            .await()
            .toObjects(StockEntry::class.java)
    }

    fun getPendingEntriesFlow(): Flow<List<StockEntry>> = callbackFlow {
        val listenerRegistration = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "pending")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val entries = snapshot.toObjects(StockEntry::class.java)
                    trySend(entries).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun approveEntry(entryId: String) {
        firestore.collection(StockEntry.COLLECTION_NAME)
            .document(entryId)
            .update("status", "approved")
            .await()
    }
}
