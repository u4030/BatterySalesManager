package com.batterysales.data.repositories

import com.batterysales.data.models.StockEntry
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import android.util.Log
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
        firestore.runTransaction { transaction ->
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
            val finalEntry = stockEntry.copy(id = docRef.id)
            transaction.set(docRef, finalEntry)

            if (finalEntry.status == "approved") {
                updateVariantStock(transaction, finalEntry.productVariantId, finalEntry.warehouseId, finalEntry.quantity)
            }
        }.await()
    }

    suspend fun addStockEntries(stockEntries: List<StockEntry>) {
        firestore.runTransaction { transaction ->
            stockEntries.forEach { entry ->
                val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
                val finalEntry = entry.copy(id = docRef.id)
                transaction.set(docRef, finalEntry)

                if (finalEntry.status == "approved") {
                    updateVariantStock(transaction, finalEntry.productVariantId, finalEntry.warehouseId, finalEntry.quantity)
                }
            }
        }.await()
    }

    private fun updateVariantStock(transaction: com.google.firebase.firestore.Transaction, variantId: String, warehouseId: String, quantityChange: Int) {
        val variantRef = firestore.collection("product_variants").document(variantId)
        val variantSnap = transaction.get(variantRef)
        val variant = variantSnap.toObject(com.batterysales.data.models.ProductVariant::class.java)
        if (variant != null) {
            val newStockMap = variant.currentStock.toMutableMap()
            val currentQty = newStockMap[warehouseId] ?: 0
            newStockMap[warehouseId] = currentQty + quantityChange
            transaction.update(variantRef, "currentStock", newStockMap)
        }
    }

    fun getAllStockEntriesFlow(limit: Long = 5000): Flow<List<StockEntry>> = callbackFlow {
        val listenerRegistration = firestore.collection(StockEntry.COLLECTION_NAME)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
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

    fun getStockEntriesByWarehouseFlow(warehouseId: String, limit: Long = 5000): Flow<List<StockEntry>> = callbackFlow {
        val listenerRegistration = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("warehouseId", warehouseId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
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

    fun getPurchasesFlow(): Flow<List<StockEntry>> = callbackFlow {
        val listenerRegistration = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereGreaterThan("totalCost", 0)
            .orderBy("totalCost") // Firestore requirement for inequality
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fallback if index missing or complex order required
                    Log.e("StockEntryRepository", "Error in getPurchasesFlow, trying fallback", error)
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
        firestore.runTransaction { transaction ->
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
            transaction.set(sourceDocRef, sourceStockEntry)

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
            transaction.set(destinationDocRef, destinationStockEntry)

            if (status == "approved") {
                updateVariantStock(transaction, productVariantId, sourceWarehouseId, -quantity)
                updateVariantStock(transaction, productVariantId, destinationWarehouseId, quantity)
            }
        }.await()
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
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .document(entryId)
            .get()
            .await()
        return snapshot.toObject(StockEntry::class.java)?.copy(id = snapshot.id)
    }

    suspend fun updateStockEntry(entry: StockEntry) {
        firestore.runTransaction { transaction ->
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entry.id)
            val oldEntrySnap = transaction.get(docRef)
            val oldEntry = oldEntrySnap.toObject(StockEntry::class.java)

            transaction.set(docRef, entry)

            if (oldEntry != null && oldEntry.status == "approved") {
                // Reverse old stock
                updateVariantStock(transaction, oldEntry.productVariantId, oldEntry.warehouseId, -(oldEntry.quantity))
            }

            if (entry.status == "approved") {
                // Apply new stock
                updateVariantStock(transaction, entry.productVariantId, entry.warehouseId, entry.quantity)
            }
        }.await()
    }

    suspend fun deleteStockEntry(entryId: String) {
        firestore.runTransaction { transaction ->
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
            val oldEntrySnap = transaction.get(docRef)
            val oldEntry = oldEntrySnap.toObject(StockEntry::class.java)

            transaction.delete(docRef)

            if (oldEntry != null && oldEntry.status == "approved") {
                updateVariantStock(transaction, oldEntry.productVariantId, oldEntry.warehouseId, -(oldEntry.quantity))
            }
        }.await()
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
        firestore.runTransaction { transaction ->
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
            val entrySnap = transaction.get(docRef)
            val entry = entrySnap.toObject(StockEntry::class.java)

            if (entry != null && entry.status != "approved") {
                transaction.update(docRef, "status", "approved")
                updateVariantStock(transaction, entry.productVariantId, entry.warehouseId, entry.quantity)
            }
        }.await()
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
        // Fallback or specific lookup
        val entries = getEntriesForVariants(listOf(variantId), warehouseId)
        return calculateSummary(entries[variantId] ?: emptyList())
    }

    suspend fun getEntriesForVariants(variantIds: List<String>, warehouseId: String? = null): Map<String, List<StockEntry>> {
        if (variantIds.isEmpty()) return emptyMap()
        
        // Firestore 'in' limit is 30
        val chunks = variantIds.chunked(30)
        val allEntries = mutableListOf<StockEntry>()
        
        chunks.forEach { chunk ->
            var query = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereIn("productVariantId", chunk)
                .whereEqualTo("status", "approved")
            
            if (warehouseId != null) {
                query = query.whereEqualTo("warehouseId", warehouseId)
            }
            
            val snap = query.get().await()
            allEntries.addAll(snap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) })
        }
        
        return allEntries.groupBy { it.productVariantId }
    }

    fun calculateSummary(entries: List<StockEntry>): Triple<Int, Double, Double> {
        val totalQty = entries.sumOf { it.quantity }
        val totalRet = entries.sumOf { it.returnedQuantity }
        val currentQty = totalQty - totalRet

        val purchaseEntries = entries.filter { it.quantity > 0 }
        val sumTotalCost = purchaseEntries.sumOf { it.totalCost }
        val netPurchasedQty = purchaseEntries.sumOf { it.quantity - it.returnedQuantity }
        
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
