package com.batterysales.data.repositories

import com.batterysales.data.models.*
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject

class StockEntryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val summaryRepository: SummaryRepository,
    private val billRepository: dagger.Lazy<BillRepository>
) {

    suspend fun addStockEntry(stockEntry: StockEntry) {
        addStockEntries(listOf(stockEntry))
    }

    suspend fun addStockEntries(stockEntries: List<StockEntry>) {
        saveStockOrderBatch(entries = stockEntries)
    }

    suspend fun addStockEntriesWithBill(stockEntries: List<StockEntry>, bill: Bill? = null) {
        saveStockOrderBatch(entries = stockEntries, bill = bill)
    }

    /**
     * Purified Write-Only Command Layer for Stock Entries (Architecture Mandate: Zero Client-Side Side-Effects).
     * Writes purely the stock_entries document(s) and optional bill document.
     * Zero client-side modifications to product_variants, suppliers, or summaries.
     * Backend Cloud Function `onStockEntryCreated` processes atomic updates and alerts on the server.
     */
    suspend fun saveStockOrderBatch(
        entries: List<StockEntry>,
        variantsToUpdate: List<ProductVariant> = emptyList(),
        bill: Bill? = null
    ) {
        if (entries.isEmpty() && bill == null) return

        val batch = firestore.batch()
        val now = Date()

        entries.forEach { entry ->
            val docRef = if (entry.id.isNotEmpty()) firestore.collection(StockEntry.COLLECTION_NAME).document(entry.id)
                         else firestore.collection(StockEntry.COLLECTION_NAME).document()

            val cost = entry.getNetCost()

            val finalEntry = entry.copy(
                id = docRef.id,
                status = if (entry.status.isBlank()) "pending" else entry.status,
                remainingBalance = if (cost > 0) cost else 0.0,
                isSettled = cost <= 0
            )
            batch.set(docRef, finalEntry)
        }

        if (bill != null) {
            val billRef = if (bill.id.isNotEmpty()) firestore.collection(Bill.COLLECTION_NAME).document(bill.id)
                          else firestore.collection(Bill.COLLECTION_NAME).document()
            val finalBill = bill.copy(id = billRef.id, createdAt = now, updatedAt = now)
            batch.set(billRef, finalBill)
        }

        batch.commit().await()
    }

    suspend fun getStockEntriesPaginated(
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20,
        forceRefresh: Boolean = false
    ): Pair<List<StockEntry>, DocumentSnapshot?> {
        var query: Query = firestore.collection(StockEntry.COLLECTION_NAME)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        query = query.limit(limit)

        val source = if (!forceRefresh && lastDocument == null) com.google.firebase.firestore.Source.CACHE else com.google.firebase.firestore.Source.SERVER

        val snapshot = try {
            query.get(source).await()
        } catch (e: Exception) {
            if (source == com.google.firebase.firestore.Source.CACHE) {
                query.get(com.google.firebase.firestore.Source.SERVER).await()
            } else throw e
        }

        val entries = snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(entries, lastDoc)
    }

    @Deprecated("Use getStockEntriesPaginated to avoid continuous snapshot listeners")
    fun getAllStockEntriesFlow(limit: Long = 50): Flow<List<StockEntry>> = callbackFlow {
        val snapshot = try {
            firestore.collection(StockEntry.COLLECTION_NAME)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get(com.google.firebase.firestore.Source.CACHE)
                .await()
        } catch (e: Exception) {
            firestore.collection(StockEntry.COLLECTION_NAME)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
        }
        val entries = snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        trySend(entries).isSuccess
        close()
        awaitClose { }
    }

    suspend fun getAllStockEntries(limit: Long = 10000): List<StockEntry> {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .limit(limit)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
    }

    data class TransferItem(
        val productVariantId: String,
        val productName: String,
        val capacity: Int,
        val sourceWarehouseId: String,
        val destinationWarehouseId: String,
        val quantity: Int,
        val status: String = "approved",
        val createdBy: String = "",
        val createdByUserName: String = ""
    )

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
        transferStockBulk(
            listOf(
                TransferItem(
                    productVariantId = productVariantId,
                    productName = productName,
                    capacity = capacity,
                    sourceWarehouseId = sourceWarehouseId,
                    destinationWarehouseId = destinationWarehouseId,
                    quantity = quantity,
                    status = status,
                    createdBy = createdBy,
                    createdByUserName = createdByUserName
                )
            )
        )
    }

    suspend fun transferStockBulk(items: List<TransferItem>) {
        if (items.isEmpty()) return

        firestore.runTransaction { transaction ->
            // --- READ PHASE (CONSOLIDATED BULK READ) ---
            val variantIds = items.map { it.productVariantId }.distinct()
            val warehouseIds = items.flatMap { listOf(it.sourceWarehouseId, it.destinationWarehouseId) }.distinct()

            val variantSnapshots = variantIds.associateWith { vid ->
                transaction.get(firestore.collection(ProductVariant.COLLECTION_NAME).document(vid))
            }

            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, warehouseIds)

            // --- WRITE PHASE (ATOMIC BATCH WRITE) ---
            val transferBatchId = "transfer_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val inventoryUpdates = mutableMapOf<String, MutableMap<String, Int>>() // whId -> { vid -> qtyDelta }
            val variantsMap = mutableMapOf<String, ProductVariant?>()

            items.forEach { item ->
                val vSnap = variantSnapshots[item.productVariantId]
                val variant = vSnap?.toObject(ProductVariant::class.java)
                variantsMap[item.productVariantId] = variant

                val sourceDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
                transaction.set(
                    sourceDocRef,
                    StockEntry(
                        id = sourceDocRef.id,
                        orderId = transferBatchId,
                        productVariantId = item.productVariantId,
                        productName = item.productName,
                        capacity = item.capacity,
                        specification = variant?.specification ?: "",
                        warehouseId = item.sourceWarehouseId,
                        quantity = -item.quantity,
                        costPrice = 0.0,
                        status = item.status,
                        createdBy = item.createdBy,
                        createdByUserName = item.createdByUserName
                    )
                )

                val destDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
                transaction.set(
                    destDocRef,
                    StockEntry(
                        id = destDocRef.id,
                        orderId = transferBatchId,
                        productVariantId = item.productVariantId,
                        productName = item.productName,
                        capacity = item.capacity,
                        specification = variant?.specification ?: "",
                        warehouseId = item.destinationWarehouseId,
                        quantity = item.quantity,
                        costPrice = 0.0,
                        status = item.status,
                        createdBy = item.createdBy,
                        createdByUserName = item.createdByUserName
                    )
                )

                if (item.status == "approved" && variant != null) {
                    val srcDeltas = inventoryUpdates.getOrPut(item.sourceWarehouseId) { mutableMapOf() }
                    srcDeltas[item.productVariantId] = (srcDeltas[item.productVariantId] ?: 0) - item.quantity

                    val destDeltas = inventoryUpdates.getOrPut(item.destinationWarehouseId) { mutableMapOf() }
                    destDeltas[item.productVariantId] = (destDeltas[item.productVariantId] ?: 0) + item.quantity
                }
            }

            // Stock entries created inside transaction will trigger onStockEntryCreated Cloud Function to adjust variant stock and summaries atomically
        }.await()
    }

    suspend fun getEntriesPaginated(
        variantId: String,
        warehouseId: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<StockEntry>, DocumentSnapshot?> {
        // توحيد اسم حقل التصفية ليطابق الـ Schema الجديدة للمستندات
        var query: Query = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("variantId", variantId)

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

    suspend fun getStockEntryById(entryId: String): StockEntry? {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .document(entryId)
            .get()
            .await()
        return snapshot.toObject(StockEntry::class.java)?.copy(id = snapshot.id)
    }

    suspend fun updateStockEntry(entry: StockEntry) {
        val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entry.id)
        val cost = entry.getNetCost()

        val finalEntry = entry.copy(
            remainingBalance = if (cost > 0) cost else 0.0,
            isSettled = cost <= 0
        )
        docRef.set(finalEntry).await()
    }

    suspend fun deleteStockEntry(entryId: String) {
        val entryRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
        entryRef.delete().await()
    }

    suspend fun getPendingCount(): Int {
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "pending")
            .count()
            .get(AggregateSource.SERVER).await()
        return snapshot.count.toInt()
    }

    fun getPendingEntriesFlow(): Flow<List<StockEntry>> = callbackFlow {
        val listenerRegistration = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("StockEntryRepository", "Error in getPendingEntriesFlow listener", error)
                    trySend(emptyList())
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
        val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
        docRef.update("status", "approved").await()
    }

    suspend fun getEntriesBySuppliers(supplierIds: List<String>, supplierNames: List<String> = emptyList()): List<StockEntry> {
        if (supplierIds.isEmpty() && supplierNames.isEmpty()) return emptyList()
        val allEntries = mutableListOf<StockEntry>()
        
        if (supplierIds.isNotEmpty()) {
            supplierIds.chunked(30).forEach { chunk ->
                val snap = firestore.collection(StockEntry.COLLECTION_NAME)
                    .whereIn("supplierId", chunk)
                    .get()
                    .await()
                allEntries.addAll(snap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
                    .filter { it.status == "approved" })
            }
        }

        if (supplierNames.isNotEmpty()) {
            supplierNames.chunked(30).forEach { chunk ->
                val snap = firestore.collection(StockEntry.COLLECTION_NAME)
                    .whereIn("supplier", chunk)
                    .get()
                    .await()
                
                val existingIds = allEntries.map { it.id }.toSet()
                val legacyEntries = snap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
                    .filter { !existingIds.contains(it.id) && it.status == "approved" }
                
                allEntries.addAll(legacyEntries)
            }
        }

        return allEntries
    }

    /**
     * Write-Only Return Command Layer (Architecture Mandate: Zero Client-Side Side-Effects).
     * Creates a return command document in return_commands for Cloud Functions server engine execution.
     */
    suspend fun processReturn(
        entry: StockEntry,
        quantity: Int,
        mode: String,
        notes: String,
        totalRefundAmount: Double = 0.0,
        oldBatteriesQuantity: Int = 0,
        oldBatteriesTotalAmperes: Double = 0.0,
        oldBatteriesValue: Double = 0.0
    ) {
        val commandRef = firestore.collection("return_commands").document()
        val isPurchaseReturn = entry.supplier.isNotBlank() && entry.supplier != "Sale"
        val returnType = if (isPurchaseReturn) "PURCHASE_RETURN" else "SALES_RETURN"

        val command = mapOf(
            "id" to commandRef.id,
            "type" to returnType,
            "entryId" to entry.id,
            "invoiceId" to entry.invoiceId,
            "productVariantId" to entry.productVariantId,
            "warehouseId" to entry.warehouseId,
            "returnedQuantity" to quantity,
            "mode" to mode,
            "notes" to notes,
            "totalRefundAmount" to (if (totalRefundAmount > 0) totalRefundAmount else (quantity * entry.getUnitPrice())),
            "oldBatteriesQuantity" to oldBatteriesQuantity,
            "oldBatteriesTotalAmperes" to oldBatteriesTotalAmperes,
            "oldBatteriesValue" to oldBatteriesValue,
            "status" to "PENDING",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        commandRef.set(command).await()
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

    suspend fun getEntriesForInvoice(invoiceId: String, invoiceNumber: String = ""): List<StockEntry> {
        val snapshotById = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get()
            .await()
        
        val entries = snapshotById.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        if (entries.isNotEmpty()) return entries

        if (invoiceNumber.isNotEmpty()) {
            val snapshotByNum = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereEqualTo("invoiceNumber", invoiceNumber.trim())
                .get()
                .await()
            return snapshotByNum.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        }
        
        return emptyList()
    }

    suspend fun syncVariantStock(variantId: String, product: Product? = null) {
        val warehouses = firestore.collection("warehouses").get().await().documents.map { it.id }
        val stockMap = mutableMapOf<String, Int>()
        for (whId in warehouses) {
            val qty = getVariantQuantity(variantId, whId)
            if (qty != 0) stockMap[whId] = qty
        }
        val lastCost = getWeightedAverageCost(variantId, null)
        val updates = mutableMapOf<String, Any>("currentStock" to stockMap, "weightedAverageCost" to lastCost)
        product?.let { updates["productName"] = it.name; updates["productSpecification"] = it.specification }
        firestore.collection(ProductVariant.COLLECTION_NAME).document(variantId).update(updates).await()
    }

    suspend fun hasActivityInRange(variantId: String, start: Long, end: Long): Boolean {
        val snap = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", variantId)
            .whereGreaterThanOrEqualTo("timestamp", Date(start))
            .whereLessThanOrEqualTo("timestamp", Date(end))
            .count().get(AggregateSource.SERVER).await()
        return snap.count > 0
    }

    suspend fun migrateStockEntries(billRepository: BillRepository? = null) {
        billRepository?.migrateBills()
        val snapshot = firestore.collection(StockEntry.COLLECTION_NAME).get().await()
        val suppliersSnap = firestore.collection("suppliers").get().await()
        val suppliersMap = suppliersSnap.documents.associate { (it.getString("name") ?: "").trim().lowercase() to it.id }
        
        val variantsSnap = firestore.collection(ProductVariant.COLLECTION_NAME).get().await()
        val variantsMap = variantsSnap.documents.associate { it.id to (it.getString("specification") ?: "") }

        val documentsToUpdate = snapshot.documents.filter { doc ->
            val hasRemaining = doc.contains("remainingBalance")
            val hasSettled = doc.contains("isSettled")
            val hasInvoiceDate = doc.contains("invoiceDate")
            val hasTotalCost = doc.contains("totalCost")
            val hasSupplierId = (doc.getString("supplierId") ?: "").isNotEmpty()
            val hasSpecification = (doc.getString("specification") ?: "").isNotEmpty()
            
            !hasRemaining || !hasSettled || !hasInvoiceDate || !hasTotalCost || !hasSupplierId || !hasSpecification
        }

        if (documentsToUpdate.isEmpty()) return
        
        documentsToUpdate.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                val entry = doc.toObject(StockEntry::class.java) ?: return@forEach
                val updates = mutableMapOf<String, Any>()
                
                if (!doc.contains("invoiceDate")) updates["invoiceDate"] = entry.timestamp
                
                val cost = entry.getNetCost()
                if (!doc.contains("totalCost") || (doc.getDouble("totalCost") ?: 0.0) == 0.0) {
                    updates["totalCost"] = cost
                }
                
                if ((doc.getString("supplierId") ?: "").isEmpty()) {
                    val name = (doc.getString("supplier") ?: "").trim().lowercase()
                    if (name.isNotEmpty() && suppliersMap.containsKey(name)) {
                        updates["supplierId"] = suppliersMap[name]!!
                    }
                }

                if (!doc.contains("remainingBalance")) {
                    updates["remainingBalance"] = if (cost <= 0) 0.0 else cost
                }
                if (!doc.contains("isSettled")) {
                    updates["isSettled"] = (cost <= 0)
                }

                if ((doc.getString("specification") ?: "").isEmpty()) {
                    val vid = doc.getString("productVariantId") ?: ""
                    if (vid.isNotEmpty() && variantsMap.containsKey(vid)) {
                        updates["specification"] = variantsMap[vid]!!
                    }
                }

                if (updates.isNotEmpty()) batch.update(doc.reference, updates)
            }
            batch.commit().await()
        }
    }

    suspend fun migrateAllVariants(productRepository: ProductRepository, supplierRepository: SupplierRepository, billRepository: BillRepository) {
        val products = productRepository.getProductsOnce().associateBy { it.id }
        val variantsSnap = firestore.collection(ProductVariant.COLLECTION_NAME).get().await()
        val warehouses = firestore.collection("warehouses").get().await().documents.map { it.id }
        
        variantsSnap.documents.chunked(10).forEach { chunk -> 
            chunk.forEach { doc ->
                val vid = doc.id
                val variant = doc.toObject(ProductVariant::class.java)?.copy(id = vid) ?: return@forEach
                val product = products[variant.productId]

                val stockMap = mutableMapOf<String, Int>()
                for (whId in warehouses) {
                    val qty = getVariantQuantity(vid, whId)
                    if (qty != 0) stockMap[whId] = qty
                }

                val lastCost = getWeightedAverageCost(vid, null)

                val updates = mutableMapOf<String, Any>(
                    "currentStock" to stockMap,
                    "weightedAverageCost" to lastCost,
                    "updatedAt" to Date()
                )
                product?.let { updates["productName"] = it.name; updates["productSpecification"] = it.specification }
                doc.reference.set(updates, SetOptions.merge()).await()
            }
        }

        val suppliers = supplierRepository.getSuppliersOnce()
        suppliers.forEach { supplier ->
            val debitQuery = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereEqualTo("supplierId", supplier.id)
                .whereEqualTo("status", "approved")
            val debitSnap = debitQuery.aggregate(AggregateField.sum("totalCost")).get(AggregateSource.SERVER).await()
            val debit = debitSnap.getDouble(AggregateField.sum("totalCost")) ?: 0.0

            val creditQuery = firestore.collection(Bill.COLLECTION_NAME)
                .whereEqualTo("supplierId", supplier.id)
            val creditSnap = creditQuery.aggregate(AggregateField.sum("paidAmount")).get(AggregateSource.SERVER).await()
            val paymentCredit = creditSnap.getDouble(AggregateField.sum("paidAmount")) ?: 0.0

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

        val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)
        val variantsList = firestore.collection(ProductVariant.COLLECTION_NAME).get().await()
            .documents.mapNotNull { it.toObject(ProductVariant::class.java) }
            .filter { !it.archived }
        
        val totalInvValue = variantsList.sumOf { (it.currentStock?.values?.sum() ?: 0) * it.weightedAverageCost }
        val totalInvQty = variantsList.sumOf { it.currentStock?.values?.sum() ?: 0 }
        val totalSuppDebt = suppliers.sumOf { (it.totalDebit - it.totalCredit) }

        statsRef.set(SystemStats(
            totalSupplierDebt = totalSuppDebt,
            totalInventoryValue = totalInvValue,
            totalInventoryQuantity = totalInvQty,
            updatedAt = Date()
        ), SetOptions.merge()).await()
    }

    /**
     * Cache-First Cost Query (Architecture Mandate: Zero Quota Consumption / No SERVER reads).
     */
    suspend fun getWeightedAverageCost(variantId: String, warehouseId: String?): Double {
        return try {
            val summary = summaryRepository.getInventorySummary(warehouseId, forceRefresh = false)
            val costFromSummary = summary?.items?.get(variantId)?.weightedAverageCost
            if (costFromSummary != null && costFromSummary > 0.001) {
                return costFromSummary
            }
            val variantSnap = firestore.collection(ProductVariant.COLLECTION_NAME)
                .document(variantId)
                .get(com.google.firebase.firestore.Source.CACHE)
                .await()
            variantSnap.getDouble("weightedAverageCost") ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * Cache-First Stock Quantity Query (Architecture Mandate: Zero Quota Consumption / No SERVER reads).
     */
    suspend fun getVariantQuantity(variantId: String, warehouseId: String?): Int {
        return try {
            val summary = summaryRepository.getInventorySummary(warehouseId, forceRefresh = false)
            val stockFromSummary = summary?.items?.get(variantId)?.currentStock
            if (stockFromSummary != null) {
                return stockFromSummary
            }
            val variantSnap = firestore.collection(ProductVariant.COLLECTION_NAME)
                .document(variantId)
                .get(com.google.firebase.firestore.Source.CACHE)
                .await()
            val targetWh = warehouseId ?: ""
            if (targetWh.isNotEmpty()) {
                val stockMap = variantSnap.get("currentStock") as? Map<*, *>
                (stockMap?.get(targetWh) as? Number)?.toInt() ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getNextInvoiceNumber(supplierId: String, isOfficial: Boolean): String {
        if (supplierId.isEmpty()) return ""
        return try {
            val snap = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereEqualTo("supplierId", supplierId)
                .get().await()
            val prefix = if (isOfficial) "INV-" else "INT-"
            val entries = snap.documents.mapNotNull { it.toObject(StockEntry::class.java) }
                .map { it.invoiceNumber.trim() }
                .filter { it.startsWith(prefix, ignoreCase = true) }

            var maxSeq = 0
            for (inv in entries) {
                val numStr = inv.substringAfter(prefix).takeWhile { it.isDigit() }
                val num = numStr.toIntOrNull() ?: 0
                if (num > maxSeq) maxSeq = num
            }
            val nextSeq = maxSeq + 1
            String.format("%s%03d", prefix, nextSeq)
        } catch (e: Exception) {
            Log.e("StockEntryRepository", "Error generating next invoice number", e)
            if (isOfficial) "INV-001" else "INT-001"
        }
    }
}
