package com.batterysales.data.repositories

import com.batterysales.data.models.StockEntry
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
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
        val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        val finalEntry = stockEntry.copy(id = docRef.id)
        docRef.set(finalEntry).await()
    }

    suspend fun addStockEntries(stockEntries: List<StockEntry>) {
        val batch = firestore.batch()
        stockEntries.forEach { entry ->
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
            val finalEntry = entry.copy(id = docRef.id)
            batch.set(docRef, finalEntry)
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
                    val entries = snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
                    trySend(entries).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getAllStockEntries(): List<StockEntry> {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
    }

    suspend fun transferStock(
        productVariantId: String,
        productName: String,
        capacity: Int,
        sourceWarehouseId: String,
        destinationWarehouseId: String,
        quantity: Int,
        status: String = "approved",
        createdBy: String = "",
        createdByUserName: String = ""
    ) {
        val batch = firestore.batch()

        // Create a negative stock entry for the source warehouse
        val sourceDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        val sourceStockEntry = StockEntry(
            id = sourceDocRef.id,
            productVariantId = productVariantId,
            productName = productName,
            capacity = capacity,
            warehouseId = sourceWarehouseId,
            quantity = -quantity,
            costPrice = 0.0, // Cost is already accounted for
            status = status,
            createdBy = createdBy,
            createdByUserName = createdByUserName
        )
        batch.set(sourceDocRef, sourceStockEntry)

        // Create a positive stock entry for the destination warehouse
        val destinationDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
        val destinationStockEntry = StockEntry(
            id = destinationDocRef.id,
            productVariantId = productVariantId,
            productName = productName,
            capacity = capacity,
            warehouseId = destinationWarehouseId,
            quantity = quantity,
            costPrice = 0.0, // Cost is already accounted for
            status = status,
            createdBy = createdBy,
            createdByUserName = createdByUserName
        )
        batch.set(destinationDocRef, destinationStockEntry)

        batch.commit().await()
    }

    suspend fun getEntriesPaginated(
        productVariantId: String,
        warehouseId: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<StockEntry>, DocumentSnapshot?> {
        var query: Query = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", productVariantId)

        if (warehouseId != null) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }

        query = query.orderBy("timestamp", Query.Direction.DESCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.limit(limit).get().await()
        val entries = snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(entries, lastDoc)
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
                    val entries = snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
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
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
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
                    val entries = snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
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

    suspend fun getPendingCount(): Int {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "pending")
            .count()
            .get(AggregateSource.SERVER)
            .await()
        return snapshot.count.toInt()
    }

    suspend fun getVariantSummary(variantId: String, warehouseId: String? = null): Triple<Int, Double, Double> {
        var baseQuery = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", variantId)
            .whereEqualTo("status", "approved")

        if (warehouseId != null) {
            baseQuery = baseQuery.whereEqualTo("warehouseId", warehouseId)
        }

        // 1. Current Quantity (Sum all quantity - sum all returnedQuantity)
        val qtySnap = baseQuery.aggregate(
            AggregateField.sum("quantity"),
            AggregateField.sum("returnedQuantity")
        ).get(AggregateSource.SERVER).await()

        val totalQty = (qtySnap.getLong(AggregateField.sum("quantity")) ?: 0).toInt()
        val totalRet = (qtySnap.getLong(AggregateField.sum("returnedQuantity")) ?: 0).toInt()
        val currentQty = totalQty - totalRet

        // 2. Average Cost (Sum of totalCost / sum of (quantity - returnedQuantity) where quantity > 0)
        val purchaseQuery = baseQuery.whereGreaterThan("quantity", 0)
        val costSnap = purchaseQuery.aggregate(
            AggregateField.sum("totalCost"),
            AggregateField.sum("quantity"),
            AggregateField.sum("returnedQuantity")
        ).get(AggregateSource.SERVER).await()

        val sumTotalCost = costSnap.getDouble(AggregateField.sum("totalCost")) ?: 0.0
        val sumPurchasedQty = (costSnap.getLong(AggregateField.sum("quantity")) ?: 0).toInt()
        val sumReturnedPurchasedQty = (costSnap.getLong(AggregateField.sum("returnedQuantity")) ?: 0).toInt()

        val netPurchasedQty = sumPurchasedQty - sumReturnedPurchasedQty
        val averageCost = if (netPurchasedQty > 0) sumTotalCost / netPurchasedQty else 0.0

        return Triple(currentQty, averageCost, currentQty * averageCost)
    }

    suspend fun getRecentApprovedPurchases(limit: Long = 100): List<StockEntry> {
        // We can't easily filter by totalCost > 0 and orderBy timestamp without a composite index.
        // But since most entries are approved, we just fetch last 100 approved ones and filter in memory.
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "approved")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
            .filter { it.totalCost > 0 }
    }

    suspend fun getSupplierDebit(supplierId: String, resetDate: java.util.Date? = null, startDate: Long? = null, endDate: Long? = null): Double {
        var query = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
            .whereEqualTo("status", "approved")

        resetDate?.let { query = query.whereGreaterThan("timestamp", it) }
        startDate?.let { query = query.whereGreaterThanOrEqualTo("timestamp", java.util.Date(it)) }
        endDate?.let { query = query.whereLessThanOrEqualTo("timestamp", java.util.Date(it + 86400000)) }

        val snapshot = query.aggregate(AggregateField.sum("totalCost")).get(AggregateSource.SERVER).await()
        return snapshot.getDouble(AggregateField.sum("totalCost")) ?: 0.0
    }
}
