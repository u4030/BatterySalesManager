package com.batterysales.data.repositories

import com.batterysales.data.models.*
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
import java.util.*
import javax.inject.Inject

class StockEntryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val summaryRepository: SummaryRepository,
    private val billRepository: dagger.Lazy<BillRepository>
) {

    suspend fun addStockEntry(stockEntry: StockEntry) {
        firestore.runTransaction { transaction ->
            // 1. Reads
            val variantRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(stockEntry.productVariantId)
            val variantSnap = transaction.get(variantRef)
            val variant = variantSnap.toObject(ProductVariant::class.java)?.copy(id = variantSnap.id)
            val snapshots = summaryRepository.getSummarySnapshots(transaction, stockEntry.warehouseId)

            // 2. Writes
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
            val finalEntry = stockEntry.copy(
                id = docRef.id,
                productName = variant?.productName ?: stockEntry.productName,
                capacity = variant?.capacity ?: stockEntry.capacity,
                specification = variant?.specification ?: stockEntry.specification
            )
            transaction.set(docRef, finalEntry)

            if (finalEntry.status == "approved" && variant != null) {
                val variantUpdates = mutableMapOf<String, Any>()
                
                // Update Stock Map
                val currentStockMap = variant.currentStock ?: emptyMap()
                val newStockMap = currentStockMap.toMutableMap()
                val newQty = (newStockMap[finalEntry.warehouseId] ?: 0) + (finalEntry.quantity - finalEntry.returnedQuantity)
                newStockMap[finalEntry.warehouseId] = newQty
                variantUpdates["currentStock"] = newStockMap

                // --- Low Stock Check (Event-Driven) ---
                val threshold = variant.minQuantities[finalEntry.warehouseId] ?: variant.minQuantity
                val alertRef = firestore.collection(SystemAlert.COLLECTION_NAME).document("low_stock_${variant.id}_${finalEntry.warehouseId}")

                if (!variant.isDiscontinued && threshold > 0 && newQty <= threshold) {
                    transaction.set(alertRef, SystemAlert(
                        id = alertRef.id,
                        type = SystemAlert.TYPE_LOW_STOCK,
                        title = "مخزون منخفض: ${variant.productName ?: ""}",
                        message = "${variant.capacity}A | الكمية الحالية: $newQty (الحد: $threshold)",
                        relatedId = variant.id,
                        warehouseId = finalEntry.warehouseId,
                        timestamp = Date(),
                        data = mapOf("capacity" to variant.capacity, "currentStock" to newQty, "threshold" to threshold)
                    ))
                } else {
                    // Delete alert if stock is above threshold OR if variant is discontinued
                    transaction.delete(alertRef)
                }

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

                // --- Update Summaries ---
                summaryRepository.applyInventoryUpdate(
                    transaction = transaction,
                    snapshots = snapshots,
                    warehouseId = finalEntry.warehouseId,
                    variantId = finalEntry.productVariantId,
                    variant = variant.copy(
                        weightedAverageCost = variantUpdates["weightedAverageCost"] as? Double ?: variant.weightedAverageCost
                    ),
                    qtyChange = finalEntry.quantity - finalEntry.returnedQuantity,
                    costChange = finalEntry.getNetCost()
                )

                // --- Update Supplier Denormalized Totals ---
                if (finalEntry.supplierId.isNotEmpty()) {
                    summaryRepository.invalidateSupplierReportCache(transaction, finalEntry.supplierId)
                    val supplierRef = firestore.collection("suppliers").document(finalEntry.supplierId)
                    val cost = finalEntry.getNetCost()
                    if (cost > 0.001) {
                        transaction.update(supplierRef, "totalDebit", com.google.firebase.firestore.FieldValue.increment(cost))
                        transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(cost))
                        transaction.update(docRef, "remainingBalance", cost)
                        summaryRepository.applySupplierUpdate(transaction, snapshots, finalEntry.supplierId, variant.productName ?: "", debitChange = cost)
                    } else if (cost < -0.001) {
                        // This is a return/credit note
                        transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(-cost))
                        transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(cost))
                        transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(-cost))
                        
                        val returnNotes = listOf("مرتجع مواد: JD ${String.format("%.3f", -cost)}")
                        transaction.update(docRef, mapOf(
                            "isSettled" to true,
                            "remainingBalance" to 0.0,
                            "settlementNotes" to returnNotes
                        ))
                        summaryRepository.applySupplierUpdate(transaction, snapshots, finalEntry.supplierId, variant.productName ?: "", creditChange = -cost)
                    }
                }

                // --- Update Global System Stats ---
                val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)
                val cost = finalEntry.getNetCost()
                val qty = finalEntry.quantity - finalEntry.returnedQuantity
                
                transaction.update(statsRef, mapOf(
                    "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(qty.toLong()),
                    "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(cost),
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(cost),
                    "updatedAt" to Date()
                ))
            }
        }.await()

        if (stockEntry.status == "approved" && stockEntry.supplierId.isNotEmpty()) {
            billRepository.get().autoLinkBillsForSupplier(stockEntry.supplierId)
        }
    }

    suspend fun addStockEntries(stockEntries: List<StockEntry>) {
        if (stockEntries.isEmpty()) return
        firestore.runTransaction { transaction ->
            val variantIds = stockEntries.map { it.productVariantId }.distinct()
            val variantRefs = variantIds.associateWith { firestore.collection(ProductVariant.COLLECTION_NAME).document(it) }
            val variantsMap = variantRefs.mapValues { (_, ref) -> 
                val snap = transaction.get(ref)
                snap.toObject(ProductVariant::class.java)?.copy(id = snap.id)
            }
            
            val supplierIds = stockEntries.map { it.supplierId }.filter { it.isNotEmpty() }.distinct()
            val supplierRefs = supplierIds.associateWith { firestore.collection("suppliers").document(it) }

            val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)
            
            // Fetch snapshots for ALL involved warehouses
            val warehouseIds = stockEntries.map { it.warehouseId }.distinct()
            val snapshotsMap = warehouseIds.associateWith { summaryRepository.getSummarySnapshots(transaction, it) }

            val stockUpdates = mutableMapOf<String, MutableMap<String, Int>>() 
            var totalCostChange = 0.0
            var totalQtyChange = 0L
            val supplierDebitChanges = mutableMapOf<String, Double>()
            val supplierCreditChanges = mutableMapOf<String, Double>()

            stockEntries.forEach { entry ->
                val variant = variantsMap[entry.productVariantId]
                val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
                val cost = entry.getNetCost()
                val qty = entry.quantity - entry.returnedQuantity
                
                val remainingBalance = if (cost > 0) cost else 0.0
                val isSettled = cost < 0

                val finalEntry = entry.copy(
                    id = docRef.id,
                    productName = variant?.productName ?: entry.productName,
                    capacity = variant?.capacity ?: entry.capacity,
                    remainingBalance = remainingBalance,
                    isSettled = isSettled,
                    specification = variant?.specification ?: entry.specification
                )
                transaction.set(docRef, finalEntry)

                if (finalEntry.status == "approved") {
                    val warehouseUpdates = stockUpdates.getOrPut(entry.productVariantId) { mutableMapOf() }
                    warehouseUpdates[entry.warehouseId] = (warehouseUpdates[entry.warehouseId] ?: 0) + qty
                    
                    totalCostChange += cost
                    totalQtyChange += qty.toLong()

                    if (finalEntry.supplierId.isNotEmpty()) {
                        if (cost > 0) {
                            supplierDebitChanges[finalEntry.supplierId] = (supplierDebitChanges[finalEntry.supplierId] ?: 0.0) + cost
                        } else if (cost < 0) {
                            supplierCreditChanges[finalEntry.supplierId] = (supplierCreditChanges[finalEntry.supplierId] ?: 0.0) - cost
                        }
                    }
                }
            }

            // Group updates by warehouse to minimize summary writes
            val warehouseQtyChanges = mutableMapOf<String, MutableMap<String, Int>>() // whId -> {vid -> qty}
            val warehouseCostChanges = mutableMapOf<String, Double>() // whId -> totalCost

            stockUpdates.forEach { (variantId, updates) ->
                val variant = variantsMap[variantId] ?: return@forEach
                val variantRef = variantRefs[variantId] ?: return@forEach
                
                val currentStockMap = variant.currentStock ?: emptyMap()
                val newStockMap = currentStockMap.toMutableMap()
                updates.forEach { (warehouseId, change) ->
                    val newQty = (newStockMap[warehouseId] ?: 0) + change
                    newStockMap[warehouseId] = newQty

                    // Low Stock Check
                    val threshold = variant.minQuantities[warehouseId] ?: variant.minQuantity
                    val alertRef = firestore.collection(SystemAlert.COLLECTION_NAME).document("low_stock_${variant.id}_$warehouseId")

                    if (!variant.isDiscontinued && threshold > 0 && newQty <= threshold) {
                        transaction.set(alertRef, SystemAlert(
                            id = alertRef.id,
                            type = SystemAlert.TYPE_LOW_STOCK,
                            title = "مخزون منخفض: ${variant.productName ?: ""}",
                            message = "${variant.capacity}A | الكمية الحالية: $newQty (الحد: $threshold)",
                            relatedId = variant.id,
                            warehouseId = warehouseId,
                            timestamp = Date(),
                            data = mapOf("capacity" to variant.capacity, "currentStock" to newQty, "threshold" to threshold)
                        ))
                    } else {
                        transaction.delete(alertRef)
                    }
                    
                    // Track for summary update
                    val whQtyMap = warehouseQtyChanges.getOrPut(warehouseId) { mutableMapOf() }
                    whQtyMap[variantId] = (whQtyMap[variantId] ?: 0) + change
                }
                transaction.update(variantRef, "currentStock", newStockMap)
            }

            // Apply Summary Updates (ONE WRITE PER WAREHOUSE)
            warehouseQtyChanges.forEach { (whId, vChanges) ->
                val snapshots = snapshotsMap[whId] ?: return@forEach
                summaryRepository.applyBulkInventoryUpdate(
                    transaction = transaction,
                    snapshots = snapshots,
                    warehouseId = whId,
                    variantsMap = variantsMap,
                    qtyChanges = vChanges
                )
            }

            val allSupplierIds = (supplierDebitChanges.keys + supplierCreditChanges.keys).distinct()
            allSupplierIds.forEach { sid ->
                summaryRepository.invalidateSupplierReportCache(transaction, sid)
                val ref = supplierRefs[sid] ?: return@forEach
                val debit = supplierDebitChanges[sid] ?: 0.0
                val credit = supplierCreditChanges[sid] ?: 0.0
                
                if (debit != 0.0) {
                    transaction.update(ref, "totalDebit", com.google.firebase.firestore.FieldValue.increment(debit))
                    transaction.update(ref, "currentBalance", com.google.firebase.firestore.FieldValue.increment(debit))
                }
                if (credit != 0.0) {
                    transaction.update(ref, "totalCredit", com.google.firebase.firestore.FieldValue.increment(credit))
                    transaction.update(ref, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-credit))
                    transaction.update(ref, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(credit))
                }
            }

            if (totalCostChange != 0.0 || totalQtyChange != 0L) {
                transaction.update(statsRef, mapOf(
                    "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(totalQtyChange),
                    "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(totalCostChange),
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(totalCostChange),
                    "updatedAt" to Date()
                ))
            }
        }.await()

        val suppliersToLink = stockEntries.filter { it.status == "approved" && it.supplierId.isNotEmpty() }
            .map { it.supplierId }.distinct()
        suppliersToLink.forEach { billRepository.get().autoLinkBillsForSupplier(it) }
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
            val variantRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(productVariantId)
            val variant = transaction.get(variantRef).toObject(ProductVariant::class.java)

            val sourceDocRef = firestore.collection(StockEntry.COLLECTION_NAME).document()
            val sourceStockEntry = StockEntry(
                id = sourceDocRef.id,
                productVariantId = productVariantId,
                productName = productName,
                capacity = capacity,
                specification = variant?.specification ?: "",
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
                specification = variant?.specification ?: "",
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
                val sourceNewQty = (newStockMap[sourceWarehouseId] ?: 0) - quantity
                val destNewQty = (newStockMap[destinationWarehouseId] ?: 0) + quantity
                newStockMap[sourceWarehouseId] = sourceNewQty
                newStockMap[destinationWarehouseId] = destNewQty
                transaction.update(variantRef, "currentStock", newStockMap)

                // Low Stock Check for Source
                val threshold = variant.minQuantities[sourceWarehouseId] ?: variant.minQuantity
                val alertRef = firestore.collection(SystemAlert.COLLECTION_NAME).document("low_stock_${variant.id}_$sourceWarehouseId")

                if (!variant.isDiscontinued && threshold > 0 && sourceNewQty <= threshold) {
                    transaction.set(alertRef, SystemAlert(
                        id = alertRef.id,
                        type = SystemAlert.TYPE_LOW_STOCK,
                        title = "مخزون منخفض: ${variant.productName ?: ""}",
                        message = "${variant.capacity}A | الكمية الحالية: $sourceNewQty (الحد: $threshold)",
                        relatedId = variant.id,
                        warehouseId = sourceWarehouseId,
                        timestamp = Date()
                    ))
                } else {
                    transaction.delete(alertRef)
                }
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
            val oldSnap = transaction.get(docRef)
            val oldEntry = oldSnap.toObject(StockEntry::class.java)?.copy(id = oldSnap.id)

            val variantIds = mutableSetOf<String>()
            oldEntry?.let { variantIds.add(it.productVariantId) }
            variantIds.add(entry.productVariantId)

            val variantRefs = variantIds.associateWith { firestore.collection(ProductVariant.COLLECTION_NAME).document(it) }
            val variantSnapshots = variantRefs.mapValues { (_, ref) -> transaction.get(ref) }
            
            val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)

            transaction.set(docRef, entry)

            val updatedVariantsStock = mutableMapOf<String, MutableMap<String, Int>>()

            if (oldEntry != null && oldEntry.status == "approved") {
                val vSnap = variantSnapshots[oldEntry.productVariantId]
                val variant = vSnap?.toObject(ProductVariant::class.java)?.copy(id = vSnap.id)
                val currentStockMap = variant?.currentStock ?: emptyMap()
                val stockMap = updatedVariantsStock.getOrPut(oldEntry.productVariantId) { currentStockMap.toMutableMap() }
                val current = stockMap[oldEntry.warehouseId] ?: 0
                val netQty = (oldEntry.quantity - oldEntry.returnedQuantity)
                stockMap[oldEntry.warehouseId] = current - netQty
                
                val cost = oldEntry.getNetCost()
                transaction.update(statsRef, mapOf(
                    "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(-netQty.toLong()),
                    "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(-cost),
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(-cost)
                ))
            }

            if (entry.status == "approved") {
                val vSnap = variantSnapshots[entry.productVariantId]
                val initialVariant = vSnap?.toObject(ProductVariant::class.java)?.copy(id = vSnap.id)
                val currentStockMap = initialVariant?.currentStock ?: emptyMap()
                val stockMap = updatedVariantsStock.getOrPut(entry.productVariantId) { currentStockMap.toMutableMap() }
                val current = stockMap[entry.warehouseId] ?: 0
                val netQty = (entry.quantity - entry.returnedQuantity)
                stockMap[entry.warehouseId] = current + netQty
                
                val cost = entry.getNetCost()
                transaction.update(statsRef, mapOf(
                    "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(netQty.toLong()),
                    "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(cost),
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(cost)
                ))
            }

            updatedVariantsStock.forEach { (vid, newMap) ->
                transaction.update(variantRefs[vid]!!, "currentStock", newMap)
            }
        }.await()
    }

    suspend fun deleteStockEntry(entryId: String) {
        // Find documents outside transaction to avoid coroutine suspension inside runTransaction
        val entryRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entryId)
        val oldEntry = entryRef.get().await().toObject(StockEntry::class.java)?.copy(id = entryId)

        val linkedBills = if (oldEntry != null) {
            firestore.collection(Bill.COLLECTION_NAME)
                .whereEqualTo("referenceNumber", oldEntry.invoiceNumber)
                .whereEqualTo("supplierId", oldEntry.supplierId)
                .get().await().documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
        } else emptyList()

        val billIds = linkedBills.map { it.id }
        val bankTransactions = if (billIds.isNotEmpty()) {
            firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME)
                .whereIn("billId", billIds).get().await().documents
        } else emptyList()

        val treasuryTransactions = if (billIds.isNotEmpty()) {
            firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
                .whereIn("relatedId", billIds).get().await().documents
        } else emptyList()

        firestore.runTransaction { transaction ->
            val oldSnap = transaction.get(entryRef)
            val entry = oldSnap.toObject(StockEntry::class.java)?.copy(id = oldSnap.id)

            val variantRef = entry?.let { firestore.collection(ProductVariant.COLLECTION_NAME).document(it.productVariantId) }
            val variant = variantRef?.let { 
                val vSnap = transaction.get(it)
                vSnap.toObject(ProductVariant::class.java)?.copy(id = vSnap.id)
            }

            transaction.delete(entryRef)

            if (entry != null && entry.status == "approved" && variant != null && variantRef != null) {
                val currentStockMap = variant.currentStock ?: emptyMap()
                val newStockMap = currentStockMap.toMutableMap()
                newStockMap[entry.warehouseId] = (newStockMap[entry.warehouseId] ?: 0) - (entry.quantity - entry.returnedQuantity)
                transaction.update(variantRef, "currentStock", newStockMap)

                val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)
                val cost = entry.getNetCost()
                val qty = entry.quantity - entry.returnedQuantity
                
                val statsUpdates = mutableMapOf<String, Any>(
                    "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(-qty.toLong()),
                    "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(-cost),
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(-cost),
                    "updatedAt" to Date()
                )

                // Process linked bills collected outside the transaction
                linkedBills.forEach { bill ->
                    transaction.delete(firestore.collection(Bill.COLLECTION_NAME).document(bill.id))

                    if (bill.paidAmount > 0.001) {
                        if (bill.billType == BillType.CASH) statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(bill.paidAmount)
                        else if (bill.billType == BillType.TRANSFER) statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(bill.paidAmount)
                    }
                }

                bankTransactions.forEach { transaction.delete(it.reference) }
                treasuryTransactions.forEach { transaction.delete(it.reference) }

                transaction.update(statsRef, statsUpdates)

                if (oldEntry != null && oldEntry.supplierId.isNotEmpty()) {
                    summaryRepository.invalidateSupplierReportCache(transaction, oldEntry.supplierId)
                    val supplierRef = firestore.collection("suppliers").document(oldEntry.supplierId)
                    if (cost > 0) {
                        transaction.update(supplierRef, "totalDebit", com.google.firebase.firestore.FieldValue.increment(-cost))
                        transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-cost))
                    } else if (cost < 0) {
                        transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(cost))
                        transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-cost))
                        transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(cost))
                    }
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
            val entry = entrySnap.toObject(StockEntry::class.java)?.copy(id = entrySnap.id) ?: return@runTransaction

            if (entry.status == "approved") return@runTransaction

            val variantRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(entry.productVariantId)
            val variantSnap = transaction.get(variantRef)
            val variant = variantSnap.toObject(ProductVariant::class.java)?.copy(id = variantSnap.id) ?: return@runTransaction

            // Update Entry Status
            transaction.update(docRef, "status", "approved")

            // Update Stock Map
            val currentStockMap = variant.currentStock ?: emptyMap()
            val newStockMap = currentStockMap.toMutableMap()
            val newQty = (newStockMap[entry.warehouseId] ?: 0) + (entry.quantity - entry.returnedQuantity)
            newStockMap[entry.warehouseId] = newQty
            
            // Update Weighted Average Cost (Only on purchases)
            var newAvgCost = variant.weightedAverageCost
            if (entry.quantity > 0) {
                val currentTotalQty = currentStockMap.values.sum()
                val currentTotalCost = variant.weightedAverageCost * currentTotalQty
                val newTotalQty = currentTotalQty + entry.quantity
                if (newTotalQty > 0) {
                    newAvgCost = (currentTotalCost + entry.totalCost) / newTotalQty
                }
            }

            transaction.update(variantRef, mapOf(
                "currentStock" to newStockMap,
                "weightedAverageCost" to newAvgCost
            ))

            // Low Stock Check
            val threshold = variant.minQuantities[entry.warehouseId] ?: variant.minQuantity
            val alertRef = firestore.collection(SystemAlert.COLLECTION_NAME).document("low_stock_${variant.id}_${entry.warehouseId}")

            if (!variant.isDiscontinued && threshold > 0 && newQty <= threshold) {
                transaction.set(alertRef, SystemAlert(
                    id = alertRef.id,
                    type = SystemAlert.TYPE_LOW_STOCK,
                    title = "مخزون منخفض: ${variant.productName ?: ""}",
                    message = "${variant.capacity}A | الكمية الحالية: $newQty (الحد: $threshold)",
                    relatedId = variant.id,
                    warehouseId = entry.warehouseId,
                    timestamp = Date(),
                    data = mapOf("capacity" to variant.capacity, "currentStock" to newQty, "threshold" to threshold)
                ))
            } else {
                transaction.delete(alertRef)
            }

            // Update Supplier Denormalized Totals
            if (entry.supplierId.isNotEmpty()) {
                summaryRepository.invalidateSupplierReportCache(transaction, entry.supplierId)
                val supplierRef = firestore.collection("suppliers").document(entry.supplierId)
                val cost = entry.getNetCost()
                if (cost > 0) {
                    transaction.update(supplierRef, "totalDebit", com.google.firebase.firestore.FieldValue.increment(cost))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(cost))
                    transaction.update(docRef, "remainingBalance", cost)
                } else if (cost < 0) {
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(-cost))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(cost))
                    transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(-cost))
                    transaction.update(docRef, "isSettled", true)
                    transaction.update(docRef, "remainingBalance", 0.0)
                }
            }

            // Update Global System Stats
            val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)
            val cost = entry.getNetCost()
            val qty = entry.quantity - entry.returnedQuantity
            transaction.update(statsRef, mapOf(
                "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(qty.toLong()),
                "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(cost),
                "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(cost),
                "updatedAt" to Date()
            ))
        }.await()
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
                allEntries.addAll(snap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) })
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
                    .filter { !existingIds.contains(it.id) }
                
                allEntries.addAll(legacyEntries)
            }
        }

        return allEntries
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
        Log.d("Migration", "Starting Optimized Migration...")
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

                val avgCost = getWeightedAverageCost(vid, null)

                val updates = mutableMapOf<String, Any>(
                    "currentStock" to stockMap,
                    "weightedAverageCost" to avgCost,
                    "updatedAt" to Date()
                )
                product?.let { updates["productName"] = it.name; updates["productSpecification"] = it.specification }
                doc.reference.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
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
        
        val totalInvValue = variantsList.sumOf { (it.currentStock?.values?.sum() ?: 0) * it.weightedAverageCost }
        val totalInvQty = variantsList.sumOf { it.currentStock?.values?.sum() ?: 0 }
        val totalSuppDebt = suppliers.sumOf { (it.totalDebit - it.totalCredit) }

        statsRef.set(SystemStats(
            totalSupplierDebt = totalSuppDebt,
            totalInventoryValue = totalInvValue,
            totalInventoryQuantity = totalInvQty,
            updatedAt = Date()
        ), com.google.firebase.firestore.SetOptions.merge()).await()
        
        Log.d("Migration", "Optimized Migration Finished")
    }

    suspend fun syncVariantStock(variantId: String, product: Product? = null) {
        val warehouses = firestore.collection("warehouses").get().await().documents.map { it.id }
        val stockMap = mutableMapOf<String, Int>()
        for (whId in warehouses) {
            val qty = getVariantQuantity(variantId, whId)
            if (qty != 0) stockMap[whId] = qty
        }
        val avgCost = getWeightedAverageCost(variantId, null)
        val updates = mutableMapOf<String, Any>("currentStock" to stockMap, "weightedAverageCost" to avgCost)
        product?.let { updates["productName"] = it.name; updates["productSpecification"] = it.specification }
        firestore.collection(ProductVariant.COLLECTION_NAME).document(variantId).update(updates).await()
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
            .whereGreaterThanOrEqualTo("timestamp", Date(start))
            .whereLessThanOrEqualTo("timestamp", Date(end))
            .count().get(AggregateSource.SERVER).await()
        return snap.count > 0
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

    suspend fun getEntriesForInvoice(invoiceId: String, invoiceNumber: String = ""): List<StockEntry> {
        // Try searching by invoiceId first (Modern)
        val snapshotById = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get()
            .await()
        
        val entries = snapshotById.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        if (entries.isNotEmpty()) return entries

        // Fallback: Try searching by invoiceNumber (Legacy)
        if (invoiceNumber.isNotEmpty()) {
            val snapshotByNum = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereEqualTo("invoiceNumber", invoiceNumber.trim())
                .get()
                .await()
            return snapshotByNum.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        }
        
        return emptyList()
    }

    suspend fun processReturn(entry: StockEntry, quantity: Int, mode: String, notes: String) {
        firestore.runTransaction { transaction ->
            // 1. Reads
            val docRef = firestore.collection(StockEntry.COLLECTION_NAME).document(entry.id)
            val variantRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(entry.productVariantId)
            
            val freshSnap = transaction.get(docRef)
            val freshEntry = freshSnap.toObject(StockEntry::class.java)?.copy(id = freshSnap.id) ?: return@runTransaction
            val variantSnap = transaction.get(variantRef)
            val variant = variantSnap.toObject(ProductVariant::class.java)?.copy(id = variantSnap.id)
            val snapshots = summaryRepository.getSummarySnapshots(transaction, entry.warehouseId)

            // 2. Writes
            val newReturnedQty = freshEntry.returnedQuantity + quantity
            transaction.update(docRef, mapOf(
                "returnedQuantity" to newReturnedQty,
                "returnDate" to Date()
            ))

            // Update Variant Stock
            if (variant != null) {
                val newStockMap = variant.currentStock?.toMutableMap() ?: mutableMapOf()
                newStockMap[entry.warehouseId] = (newStockMap[entry.warehouseId] ?: 0) - quantity
                transaction.update(variantRef, "currentStock", newStockMap)
                
                // Update Summary
                summaryRepository.applyInventoryUpdate(transaction, snapshots, entry.warehouseId, entry.productVariantId, variant, -quantity)
            }

            // Financial Adjustment
            val costToReturn = quantity * entry.costPrice
            if (mode == "supplier_balance" && entry.supplierId.isNotEmpty()) {
                summaryRepository.invalidateSupplierReportCache(transaction, entry.supplierId)
                val supplierRef = firestore.collection("suppliers").document(entry.supplierId)
                transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-costToReturn))
                transaction.update(supplierRef, "totalDebit", com.google.firebase.firestore.FieldValue.increment(-costToReturn))
                summaryRepository.applySupplierUpdate(transaction, snapshots, entry.supplierId, entry.productName, debitChange = -costToReturn)
            }
        }.await()
    }
}
