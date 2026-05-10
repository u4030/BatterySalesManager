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

            if (finalEntry.status == "approved" && variant != null) {
                val variantUpdates = mutableMapOf<String, Any>()

                // Update Stock Map
                val currentStockMap = variant.currentStock ?: emptyMap()
                val newStockMap = currentStockMap.toMutableMap()
                newStockMap[finalEntry.warehouseId] = (newStockMap[finalEntry.warehouseId] ?: 0) + (finalEntry.quantity - finalEntry.returnedQuantity)
                variantUpdates["currentStock"] = newStockMap

                // Update Weighted Average Cost (Only on purchases)
                if (finalEntry.quantity > 0) {
                    val currentTotalQty = currentStockMap.values.sum()
                    val currentTotalCost = variant.weightedAverageCost * currentTotalQty
                    val newTotalQty = currentTotalQty + finalEntry.quantity
                    if (newTotalQty > 0) {
                        val newAvgCost = (currentTotalCost + finalEntry.totalCost) / newTotalQty
                        variantUpdates["weightedAverageCost"] = newAvgCost
                    }
                }

                if (variantUpdates.isNotEmpty()) {
                    transaction.update(variantRef, variantUpdates)
                }

                // --- Update Supplier Denormalized Totals ---
                if (finalEntry.supplierId.isNotEmpty()) {
                    val supplierRef = firestore.collection("suppliers").document(finalEntry.supplierId)
                    val cost = finalEntry.getNetCost()
                    if (cost > 0) {
                        // Purchase: Increase Debit
                        transaction.update(supplierRef, "totalDebit", com.google.firebase.firestore.FieldValue.increment(cost))
                        transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(cost))
                    } else if (cost < 0) {
                        // Return: Increase Credit (represented as negative cost in entries)
                        transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(-cost))
                        transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(cost))
                    }
                }

                // --- Update Global System Stats ---
                val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
                val cost = finalEntry.getNetCost()
                val qty = finalEntry.quantity - finalEntry.returnedQuantity

                transaction.update(statsRef, mapOf(
                    "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(qty.toLong()),
                    "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(cost),
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(cost),
                    "updatedAt" to java.util.Date()
                ))
            }
        }.await()
    }

    suspend fun addStockEntries(stockEntries: List<StockEntry>) {
        if (stockEntries.isEmpty()) return
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
                val variantRef = variantRefs[variantId] ?: return@forEach

                val currentStockMap = variant.currentStock ?: emptyMap()
                val newStockMap = currentStockMap.toMutableMap()
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
            .orderBy("totalCost")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("StockEntryRepository", "Error in getPurchasesFlow", error)
                }
                if (snapshot != null) {
                    val entries = snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
                    trySend(entries).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getAllStockEntries(limit: Long = 10000): List<StockEntry> {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .limit(limit)
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
                costPrice = 0.0,
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
                costPrice = 0.0,
                status = status,
                createdBy = createdBy,
                createdByUserName = createdByUserName
            )
            transaction.set(destinationDocRef, destinationStockEntry)

            if (status == "approved" && variant != null) {
                val currentStockMap = variant.currentStock ?: emptyMap()
                val newStockMap = currentStockMap.toMutableMap()
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

            val variantRefs = variantIds.associateWith { firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(it) }
            val variantSnapshots = variantRefs.mapValues { (_, ref) -> transaction.get(ref) }

            // 2. Writes
            transaction.set(docRef, entry)

            val updatedVariantsStock = mutableMapOf<String, MutableMap<String, Int>>()

            if (oldEntry != null && oldEntry.status == "approved") {
                val variant = variantSnapshots[oldEntry.productVariantId]?.toObject(com.batterysales.data.models.ProductVariant::class.java)
                val currentStockMap = variant?.currentStock ?: emptyMap()
                val stockMap = updatedVariantsStock.getOrPut(oldEntry.productVariantId) { currentStockMap.toMutableMap() }
                val current = stockMap[oldEntry.warehouseId] ?: 0
                stockMap[oldEntry.warehouseId] = current - (oldEntry.quantity - oldEntry.returnedQuantity)
            }

            if (entry.status == "approved") {
                val initialVariant = variantSnapshots[entry.productVariantId]?.toObject(com.batterysales.data.models.ProductVariant::class.java)
                val currentStockMap = initialVariant?.currentStock ?: emptyMap()
                val stockMap = updatedVariantsStock.getOrPut(entry.productVariantId) { currentStockMap.toMutableMap() }
                val current = stockMap[entry.warehouseId] ?: 0
                stockMap[entry.warehouseId] = current + (entry.quantity - entry.returnedQuantity)
            }

            // Apply all accumulated updates
            updatedVariantsStock.forEach { (vid, newMap) ->
                transaction.update(variantRefs[vid]!!, "currentStock", newMap)
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
            transaction.delete(docRef)

            if (oldEntry != null && oldEntry.status == "approved" && variant != null && variantRef != null) {
                val currentStockMap = variant.currentStock ?: emptyMap()
                val newStockMap = currentStockMap.toMutableMap()
                newStockMap[oldEntry.warehouseId] = (newStockMap[oldEntry.warehouseId] ?: 0) - (oldEntry.quantity - oldEntry.returnedQuantity)
                transaction.update(variantRef, "currentStock", newStockMap)
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
                transaction.update(docRef, "status", "approved")
                
                if (variant != null && variantRef != null) {
                    val currentStockMap = variant.currentStock ?: emptyMap()
                    val newStockMap = currentStockMap.toMutableMap()
                    newStockMap[entry.warehouseId] = (newStockMap[entry.warehouseId] ?: 0) + (entry.quantity - entry.returnedQuantity)
                    transaction.update(variantRef, "currentStock", newStockMap)
                }
            }
        }.await()
    }

    suspend fun getPendingCount(): Int {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "pending")
            .count()
            .get(AggregateSource.SERVER).await()
        return snapshot.count.toInt()
    }

    suspend fun getVariantSummary(variantId: String, warehouseId: String? = null): Triple<Int, Double, Double> {
        val entries = getEntriesForVariants(listOf(variantId), warehouseId)
        return calculateSummary(entries[variantId] ?: emptyList())
    }

    suspend fun getEntriesForVariants(variantIds: List<String>, warehouseId: String? = null): Map<String, List<StockEntry>> {
        if (variantIds.isEmpty()) return emptyMap()
        val chunks = variantIds.chunked(30)
        val allEntries = mutableListOf<StockEntry>()
        chunks.forEach { chunk ->
            var query = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereIn("productVariantId", chunk)
                .whereEqualTo("status", "approved")
            if (warehouseId != null) query = query.whereEqualTo("warehouseId", warehouseId)
            val snap = query.get().await()
            allEntries.addAll(snap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) })
        }
        return allEntries.groupBy { it.productVariantId }
    }

    fun calculateSummary(entries: List<StockEntry>): Triple<Int, Double, Double> {
        val currentQty = entries.sumOf { it.getNetQuantity() }
        val purchaseEntries = entries.filter { it.quantity > 0 }
        val sumTotalCost = purchaseEntries.sumOf { it.totalCost }
        val grossPurchasedQty = purchaseEntries.sumOf { it.quantity }
        val averageCost = if (grossPurchasedQty > 0) sumTotalCost / grossPurchasedQty else 0.0
        return Triple(currentQty, averageCost, currentQty * averageCost)
    }

    suspend fun getRecentApprovedPurchases(limit: Long = 100): List<StockEntry> {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "approved")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
            .filter { it.totalCost > 0 }
    }

    suspend fun getEntriesBySuppliers(supplierIds: List<String>): List<StockEntry> {
        if (supplierIds.isEmpty()) return emptyList()
        val allEntries = mutableListOf<StockEntry>()
        val chunks = supplierIds.chunked(30)
        for (chunk in chunks) {
            val snap = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereIn("supplierId", chunk)
                .get()
                .await()
            allEntries.addAll(snap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) })
        }
        return allEntries
    }

    suspend fun migrateInvoiceDates() {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME).get().await()
        val suppliersSnap = firestore.collection("suppliers").get().await()
        val suppliersMap = suppliersSnap.documents.associate { (it.getString("name") ?: "").trim().lowercase() to it.id }
        val documentsToUpdate = snapshot.documents.filter { !it.contains("invoiceDate") || !it.contains("totalCost") || (it.getDouble("totalCost") ?: 0.0) == 0.0 || (it.getString("supplierId") ?: "").isEmpty() }
        if (documentsToUpdate.isEmpty()) return
        documentsToUpdate.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                val entry = doc.toObject(StockEntry::class.java) ?: return@forEach
                val updates = mutableMapOf<String, Any>()
                if (!doc.contains("invoiceDate")) updates["invoiceDate"] = entry.timestamp
                if (!doc.contains("totalCost") || (doc.getDouble("totalCost") ?: 0.0) == 0.0) updates["totalCost"] = entry.quantity * entry.costPrice
                val currentId = doc.getString("supplierId") ?: ""
                if (currentId.isEmpty()) {
                    val name = (doc.getString("supplier") ?: "").trim().lowercase()
                    if (name.isNotEmpty() && suppliersMap.containsKey(name)) updates["supplierId"] = suppliersMap[name]!!
                }
                if (updates.isNotEmpty()) batch.update(doc.reference, updates)
            }
            batch.commit().await()
        }
    }

    suspend fun migrateAllVariants(productRepository: ProductRepository, supplierRepository: SupplierRepository, billRepository: BillRepository) {
        Log.d("Migration", "Starting Optimized Migration...")
        val products = productRepository.getProductsOnce().associateBy { it.id }
        val variantsSnap = firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).get().await()
        val warehouses = firestore.collection("warehouses").get().await().documents.map { it.id }

        // 1. Migrate Variants using server-side aggregations (quota-friendly)
        variantsSnap.documents.chunked(10).forEach { chunk -> // Process in small chunks to avoid timeouts
            chunk.forEach { doc ->
                val vid = doc.id
                val variant = doc.toObject(com.batterysales.data.models.ProductVariant::class.java)?.copy(id = vid) ?: return@forEach
                val product = products[variant.productId]

                // Aggregate per warehouse
                val stockMap = mutableMapOf<String, Int>()
                for (whId in warehouses) {
                    val qty = getVariantQuantity(vid, whId)
                    if (qty != 0) stockMap[whId] = qty
                }

                // Aggregate average cost
                val avgCost = getWeightedAverageCost(vid, null)

                val updates = mutableMapOf<String, Any>(
                    "currentStock" to stockMap,
                    "weightedAverageCost" to avgCost,
                    "updatedAt" to java.util.Date()
                )
                product?.let { updates["productName"] = it.name; updates["productSpecification"] = it.specification }
                doc.reference.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            }
        }

        // 2. Migrate Suppliers
        val suppliers = supplierRepository.getSuppliersOnce()
        suppliers.forEach { supplier ->
            val debitQuery = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereEqualTo("supplierId", supplier.id)
                .whereEqualTo("status", "approved")
            val debitSnap = debitQuery.aggregate(AggregateField.sum("totalCost")).get(AggregateSource.SERVER).await()
            val debit = debitSnap.getDouble(AggregateField.sum("totalCost")) ?: 0.0

            val creditQuery = firestore.collection(com.batterysales.data.models.Bill.COLLECTION_NAME)
                .whereEqualTo("supplierId", supplier.id)
            val creditSnap = creditQuery.aggregate(AggregateField.sum("paidAmount")).get(AggregateSource.SERVER).await()
            val paymentCredit = creditSnap.getDouble(AggregateField.sum("paidAmount")) ?: 0.0

            // General Returns (negative totalCost in entries)
            val returnsQuery = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereEqualTo("supplierId", supplier.id)
                .whereEqualTo("status", "approved")
                .whereLessThan("totalCost", 0)
            val returnsSnap = try { returnsQuery.aggregate(AggregateField.sum("totalCost")).get(AggregateSource.SERVER).await() } catch(e: Exception) { null }
            val returnsCredit = -(returnsSnap?.getDouble(AggregateField.sum("totalCost")) ?: 0.0)

            val totalCredit = paymentCredit + returnsCredit

            firestore.collection("suppliers").document(supplier.id).update(mapOf(
                "totalDebit" to debit,
                "totalCredit" to totalCredit,
                "currentBalance" to (debit - totalCredit)
            )).await()
        }

        // 3. Global Stats
        val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
        val variantsList = firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).get().await()
            .documents.mapNotNull { it.toObject(com.batterysales.data.models.ProductVariant::class.java) }

        val totalInvValue = variantsList.sumOf { (it.currentStock?.values?.sum() ?: 0) * it.weightedAverageCost }
        val totalInvQty = variantsList.sumOf { it.currentStock?.values?.sum() ?: 0 }
        val totalSuppDebt = suppliers.sumOf { (it.totalDebit - it.totalCredit) }

        statsRef.set(com.batterysales.data.models.SystemStats(
            totalSupplierDebt = totalSuppDebt,
            totalInventoryValue = totalInvValue,
            totalInventoryQuantity = totalInvQty,
            updatedAt = java.util.Date()
        ), com.google.firebase.firestore.SetOptions.merge()).await()

        Log.d("Migration", "Optimized Migration Finished")
    }

    suspend fun syncVariantStock(variantId: String, product: com.batterysales.data.models.Product? = null) {
        val warehouses = firestore.collection("warehouses").get().await().documents.map { it.id }
        val stockMap = mutableMapOf<String, Int>()
        for (whId in warehouses) {
            val qty = getVariantQuantity(variantId, whId)
            if (qty != 0) stockMap[whId] = qty
        }
        val avgCost = getWeightedAverageCost(variantId, null)
        val updates = mutableMapOf<String, Any>("currentStock" to stockMap, "weightedAverageCost" to avgCost)
        product?.let { updates["productName"] = it.name; updates["productSpecification"] = it.specification }
        firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(variantId).update(updates).await()
    }

    suspend fun getWeightedAverageCost(variantId: String, warehouseId: String?): Double {
        var query = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", variantId)
            .whereGreaterThan("quantity", 0)
            .whereEqualTo("status", "approved")
        if (warehouseId != null) query = query.whereEqualTo("warehouseId", warehouseId)
        val sumSnap = query.aggregate(AggregateField.sum("totalCost"), AggregateField.sum("quantity")).get(AggregateSource.SERVER).await()
        val totalCost = sumSnap.getDouble(AggregateField.sum("totalCost")) ?: 0.0
        val qty = (sumSnap.getLong(AggregateField.sum("quantity")) ?: 0).toInt()
        return if (qty > 0) totalCost / qty else 0.0
    }

    suspend fun getVariantQuantity(variantId: String, warehouseId: String?): Int {
        var query = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", variantId)
            .whereEqualTo("status", "approved")
        if (warehouseId != null) query = query.whereEqualTo("warehouseId", warehouseId)
        val snap = query.aggregate(AggregateField.sum("quantity"), AggregateField.sum("returnedQuantity")).get(AggregateSource.SERVER).await()
        val qty = (snap.getLong(AggregateField.sum("quantity")) ?: 0).toInt()
        val ret = (snap.getLong(AggregateField.sum("returnedQuantity")) ?: 0).toInt()
        return qty - ret
    }

    suspend fun hasActivityInRange(variantId: String, start: Long, end: Long): Boolean {
        val snap = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", variantId)
            .whereGreaterThanOrEqualTo("timestamp", java.util.Date(start))
            .whereLessThanOrEqualTo("timestamp", java.util.Date(end))
            .count().get(AggregateSource.SERVER).await()
        return snap.count > 0
    }
}
