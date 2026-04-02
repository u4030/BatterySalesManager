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
            // 1. Reads
            val variantRef = firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(stockEntry.productVariantId)
            val variant = transaction.get(variantRef).toObject(com.batterysales.data.models.ProductVariant::class.java)

            // 2. Writes
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
            val finalEntry = stockEntry.copy(id = docRef.id)
            transaction.set(docRef, finalEntry)

            if (finalEntry.status == "approved" && variant != null && variant.currentStock != null) {
                val newStockMap = variant.currentStock.toMutableMap()
                newStockMap[finalEntry.warehouseId] = (newStockMap[finalEntry.warehouseId] ?: 0) + (finalEntry.quantity - finalEntry.returnedQuantity)
                transaction.update(variantRef, "currentStock", newStockMap)
            }
        }.await()
    }

    suspend fun addStockEntries(stockEntries: List<StockEntry>) {
        firestore.runTransaction { transaction ->
            // 1. All Reads must happen before all writes
            val variantIds = stockEntries.map { it.productVariantId }.distinct()
            val variantRefs = variantIds.associateWith { firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(it) }
            val variantsMap = variantRefs.mapValues { (_, ref) ->
                transaction.get(ref).toObject(com.batterysales.data.models.ProductVariant::class.java)
            }

            // Keep track of stock updates locally for this transaction
            val stockUpdates = mutableMapOf<String, MutableMap<String, Int>>() // variantId -> (warehouseId -> change)

            // 2. All Writes
            stockEntries.forEach { entry ->
                val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
                val finalEntry = entry.copy(id = docRef.id)
                transaction.set(docRef, finalEntry)

                if (finalEntry.status == "approved") {
                    val warehouseUpdates = stockUpdates.getOrPut(entry.productVariantId) { mutableMapOf() }
                    warehouseUpdates[entry.warehouseId] = (warehouseUpdates[entry.warehouseId] ?: 0) + (entry.quantity - entry.returnedQuantity)
                }
            }

            // Apply stock updates to variants
            stockUpdates.forEach { (variantId, updates) ->
                val variant = variantsMap[variantId] ?: return@forEach
                if (variant.currentStock == null) return@forEach // Only update if initialized

                val variantRef = variantRefs[variantId] ?: return@forEach
                val newStockMap = variant.currentStock.toMutableMap()
                updates.forEach { (warehouseId, change) ->
                    newStockMap[warehouseId] = (newStockMap[warehouseId] ?: 0) + change
                }
                transaction.update(variantRef, "currentStock", newStockMap)
            }
        }.await()
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
            // 1. Reads
            val variantRef = firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(productVariantId)
            val variant = transaction.get(variantRef).toObject(com.batterysales.data.models.ProductVariant::class.java)

            // 2. Writes
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

            if (status == "approved" && variant != null && variant.currentStock != null) {
                val newStockMap = variant.currentStock.toMutableMap()
                newStockMap[sourceWarehouseId] = (newStockMap[sourceWarehouseId] ?: 0) - quantity
                newStockMap[destinationWarehouseId] = (newStockMap[destinationWarehouseId] ?: 0) + quantity
                transaction.update(variantRef, "currentStock", newStockMap)
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
            // 1. Reads
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entry.id)
            val oldEntry = transaction.get(docRef).toObject(StockEntry::class.java)

            val variantIds = mutableSetOf<String>()
            oldEntry?.let { variantIds.add(it.productVariantId) }
            variantIds.add(entry.productVariantId)

            val variantSnapshots = variantIds.associateWith { id ->
                transaction.get(firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(id))
            }

            // 2. Writes
            transaction.set(docRef, entry)

            val updatedVariantsStock = mutableMapOf<String, MutableMap<String, Int>>()

            if (oldEntry != null && oldEntry.status == "approved") {
                val variant = variantSnapshots[oldEntry.productVariantId]?.toObject(com.batterysales.data.models.ProductVariant::class.java)
                if (variant != null && variant.currentStock != null) {
                    val stockMap = updatedVariantsStock.getOrPut(oldEntry.productVariantId) { variant.currentStock.toMutableMap() }
                    val current = stockMap[oldEntry.warehouseId] ?: 0
                    stockMap[oldEntry.warehouseId] = current - (oldEntry.quantity - oldEntry.returnedQuantity)
                }
            }

            if (entry.status == "approved") {
                // Determine source for stock map (either from initial read or previous update in this transaction)
                val initialVariant = variantSnapshots[entry.productVariantId]?.toObject(com.batterysales.data.models.ProductVariant::class.java)
                if (initialVariant != null && initialVariant.currentStock != null) {
                    val stockMap = updatedVariantsStock.getOrPut(entry.productVariantId) { initialVariant.currentStock.toMutableMap() }
                    val current = stockMap[entry.warehouseId] ?: 0
                    stockMap[entry.warehouseId] = current + (entry.quantity - entry.returnedQuantity)
                }
            }

            // Apply all accumulated updates
            updatedVariantsStock.forEach { (vid, newMap) ->
                transaction.update(firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(vid), "currentStock", newMap)
            }
        }.await()
    }

    suspend fun deleteStockEntry(entryId: String) {
        firestore.runTransaction { transaction ->
            // 1. Reads
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
            val oldEntry = transaction.get(docRef).toObject(StockEntry::class.java)

            val variantRef = oldEntry?.let { firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(it.productVariantId) }
            val variant = variantRef?.let { transaction.get(it).toObject(com.batterysales.data.models.ProductVariant::class.java) }

            // 2. Writes
            val updatesNeeded = oldEntry != null && oldEntry.status == "approved" && variant != null && variantRef != null && variant.currentStock != null

            transaction.delete(docRef)

            if (updatesNeeded) {
                val newStockMap = variant!!.currentStock!!.toMutableMap()
                newStockMap[oldEntry!!.warehouseId] = (newStockMap[oldEntry.warehouseId] ?: 0) - (oldEntry.quantity - oldEntry.returnedQuantity)
                transaction.update(variantRef!!, "currentStock", newStockMap)
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
            // 1. Reads
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
            val entry = transaction.get(docRef).toObject(StockEntry::class.java)

            val variantRef = entry?.let { firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(it.productVariantId) }
            val variant = variantRef?.let { transaction.get(it).toObject(com.batterysales.data.models.ProductVariant::class.java) }

            // 2. Writes
            if (entry != null && entry.status != "approved") {
                val shouldUpdateStock = variant != null && variantRef != null && variant.currentStock != null

                transaction.update(docRef, "status", "approved")

                if (shouldUpdateStock) {
                    val newStockMap = variant!!.currentStock!!.toMutableMap()
                    newStockMap[entry.warehouseId] = (newStockMap[entry.warehouseId] ?: 0) + (entry.quantity - entry.returnedQuantity)
                    transaction.update(variantRef!!, "currentStock", newStockMap)
                }
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

    /**
     * Recalculates the current stock for a specific variant from all historical stock entries
     * and updates the ProductVariant's currentStock map.
     */
    suspend fun syncVariantStock(variantId: String) {
        val entries = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", variantId)
            .whereEqualTo("status", "approved")
            .get()
            .await()
            .documents.mapNotNull { it.toObject(StockEntry::class.java) }

        val stockMap = mutableMapOf<String, Int>()
        entries.forEach { entry ->
            val current = stockMap[entry.warehouseId] ?: 0
            stockMap[entry.warehouseId] = current + (entry.quantity - entry.returnedQuantity)
        }

        firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME)
            .document(variantId)
            .update("currentStock", stockMap)
            .await()
    }
}
