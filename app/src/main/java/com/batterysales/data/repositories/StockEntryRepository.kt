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

    suspend fun getStockEntries(): List<StockEntry> {
        return firestore.collection(StockEntry.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(StockEntry::class.java)
    }

    suspend fun transferStock(
        productId: String,
        sourceWarehouseId: String,
        destinationWarehouseId: String,
        quantity: Int
    ) {
        val batch = firestore.batch()

        // Create a negative stock entry for the source warehouse
        val sourceStockEntry = StockEntry(
            productId = productId,
            warehouseId = sourceWarehouseId,
            quantity = -quantity,
            costPrice = 0.0 // Cost is already accounted for
        )
        val sourceDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        batch.set(sourceDocRef, sourceStockEntry)

        // Create a positive stock entry for the destination warehouse
        val destinationStockEntry = StockEntry(
            productId = productId,
            warehouseId = destinationWarehouseId,
            quantity = quantity,
            costPrice = 0.0 // Cost is already accounted for
        )
        val destinationDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        batch.set(destinationDocRef, destinationStockEntry)

        batch.commit().await()
    }
}
