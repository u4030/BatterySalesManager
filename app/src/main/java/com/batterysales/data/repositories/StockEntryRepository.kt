package com.batterysales.data.repositories

import com.batterysales.data.models.StockEntry
import com.google.firebase.firestore.FirebaseFirestore
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
        quantity: Int
    ) {
        val batch = firestore.batch()

        // Create a negative stock entry for the source warehouse
        val sourceStockEntry = StockEntry(
            productVariantId = productVariantId,
            warehouseId = sourceWarehouseId,
            quantity = -quantity,
            costPrice = 0.0 // Cost is already accounted for
        )
        val sourceDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        batch.set(sourceDocRef, sourceStockEntry)

        // Create a positive stock entry for the destination warehouse
        val destinationStockEntry = StockEntry(
            productVariantId = productVariantId,
            warehouseId = destinationWarehouseId,
            quantity = quantity,
            costPrice = 0.0 // Cost is already accounted for
        )
        val destinationDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        batch.set(destinationDocRef, destinationStockEntry)

        batch.commit().await()
    }

    suspend fun getEntriesForVariant(productVariantId: String): List<StockEntry> {
        return firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", productVariantId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(StockEntry::class.java)
    }
}
