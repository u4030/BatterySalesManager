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
        firestore.runTransaction { transaction ->
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
            val finalEntry = stockEntry.copy(id = docRef.id)
            transaction.set(docRef, finalEntry)

            updateVariantStock(transaction, finalEntry.productVariantId, finalEntry.warehouseId, finalEntry.quantity - finalEntry.returnedQuantity)

            if (finalEntry.status == StockEntry.STATUS_APPROVED && finalEntry.supplierId.isNotEmpty()) {
                updateSupplierBalance(transaction, finalEntry.supplierId, debitDelta = finalEntry.totalCost, creditDelta = 0.0)
            }
        }.await()
    }

    suspend fun addStockEntries(stockEntries: List<StockEntry>) {
        firestore.runTransaction { transaction ->
            stockEntries.forEach { entry ->
                val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
                val finalEntry = entry.copy(id = docRef.id)
                transaction.set(docRef, finalEntry)

                updateVariantStock(transaction, finalEntry.productVariantId, finalEntry.warehouseId, finalEntry.quantity - finalEntry.returnedQuantity)

                if (finalEntry.status == StockEntry.STATUS_APPROVED && finalEntry.supplierId.isNotEmpty()) {
                    updateSupplierBalance(transaction, finalEntry.supplierId, debitDelta = finalEntry.totalCost, creditDelta = 0.0)
                }
            }
        }.await()
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
            updateVariantStock(transaction, productVariantId, sourceWarehouseId, -quantity)

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
            updateVariantStock(transaction, productVariantId, destinationWarehouseId, quantity)
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
        return firestore.collection(StockEntry.COLLECTION_NAME)
            .document(entryId)
            .get()
            .await()
            .toObject(StockEntry::class.java)
    }

    suspend fun updateStockEntry(entry: StockEntry) {
        firestore.runTransaction { transaction ->
            val entryRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entry.id)
            val oldEntry = transaction.get(entryRef).toObject(StockEntry::class.java) ?: return@runTransaction

            // Reverse old impact
            updateVariantStock(transaction, oldEntry.productVariantId, oldEntry.warehouseId, -(oldEntry.quantity - oldEntry.returnedQuantity))
            if (oldEntry.status == StockEntry.STATUS_APPROVED && oldEntry.supplierId.isNotEmpty()) {
                updateSupplierBalance(transaction, oldEntry.supplierId, debitDelta = -oldEntry.totalCost, creditDelta = 0.0)
            }

            // Apply new impact
            transaction.set(entryRef, entry)
            updateVariantStock(transaction, entry.productVariantId, entry.warehouseId, entry.quantity - entry.returnedQuantity)
            if (entry.status == StockEntry.STATUS_APPROVED && entry.supplierId.isNotEmpty()) {
                updateSupplierBalance(transaction, entry.supplierId, debitDelta = entry.totalCost, creditDelta = 0.0)
            }
        }.await()
    }

    suspend fun deleteStockEntry(entryId: String) {
        firestore.runTransaction { transaction ->
            val entryRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
            val entry = transaction.get(entryRef).toObject(StockEntry::class.java) ?: return@runTransaction

            // Reverse impact
            updateVariantStock(transaction, entry.productVariantId, entry.warehouseId, -(entry.quantity - entry.returnedQuantity))
            if (entry.status == StockEntry.STATUS_APPROVED && entry.supplierId.isNotEmpty()) {
                updateSupplierBalance(transaction, entry.supplierId, debitDelta = -entry.totalCost, creditDelta = 0.0)
            }

            transaction.delete(entryRef)
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
            val entryRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
            val entry = transaction.get(entryRef).toObject(StockEntry::class.java) ?: return@runTransaction

            if (entry.status != StockEntry.STATUS_APPROVED) {
                transaction.update(entryRef, "status", StockEntry.STATUS_APPROVED)

                // When approved, if it has a supplier, update supplier debit
                if (entry.supplierId.isNotEmpty()) {
                    updateSupplierBalance(transaction, entry.supplierId, debitDelta = entry.totalCost, creditDelta = 0.0)
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

    private fun updateVariantStock(transaction: com.google.firebase.firestore.Transaction, variantId: String, warehouseId: String, delta: Int) {
        if (variantId.isEmpty()) return
        val variantRef = firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(variantId)
        val variant = transaction.get(variantRef).toObject(com.batterysales.data.models.ProductVariant::class.java)
        if (variant != null) {
            val stockLevels = variant.stockLevels.toMutableMap()
            val currentStock = stockLevels[warehouseId] ?: 0
            stockLevels[warehouseId] = currentStock + delta
            transaction.update(variantRef, "stockLevels", stockLevels)
        }
    }

    private fun updateSupplierBalance(transaction: com.google.firebase.firestore.Transaction, supplierId: String, debitDelta: Double, creditDelta: Double) {
        if (supplierId.isEmpty()) return
        val supplierRef = firestore.collection(com.batterysales.data.models.Supplier.COLLECTION_NAME).document(supplierId)
        val supplier = transaction.get(supplierRef).toObject(com.batterysales.data.models.Supplier::class.java)
        if (supplier != null) {
            val updates = mutableMapOf<String, Any>()
            if (debitDelta != 0.0) updates["totalDebit"] = supplier.totalDebit + debitDelta
            if (creditDelta != 0.0) updates["totalCredit"] = supplier.totalCredit + creditDelta
            if (updates.isNotEmpty()) transaction.update(supplierRef, updates)
        }
    }
}
