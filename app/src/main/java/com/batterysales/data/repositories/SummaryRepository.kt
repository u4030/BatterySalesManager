package com.batterysales.data.repositories

import com.batterysales.data.models.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val summariesCollection = firestore.collection("summaries")

    // --- IN-MEMORY CACHE (ELITE STRATEGY) ---
    private var cachedInventorySummary: Map<String, InventorySummary> = emptyMap() // Key: warehouseId or "global"
    private var cachedSuppliersOverview: SuppliersOverview? = null
    private var cachedFinancialStatus: FinancialStatus? = null
    private var cachedSyncRegistry: SyncRegistry? = null

    /**
     * NUCLEAR STRATEGY: Transaction-safe summary updates.
     * All reads are performed at once to comply with Firestore rules.
     */
    data class SummarySnapshots(
        val warehouseSummaries: Map<String, InventorySummary>, // warehouseId to summary
        val inventoryGlobal: InventorySummary?,
        val suppliersOverview: SuppliersOverview?,
        val financialStatus: FinancialStatus?,
        val syncRegistry: SyncRegistry?,
        val scrapSnapshots: Map<String, ScrapWarehouse> = emptyMap() // parentWarehouseId to scrap
    )

    fun getSummarySnapshots(transaction: Transaction, warehouseIds: List<String> = emptyList(), includeScrap: Boolean = false): SummarySnapshots {
        val whSummaries = warehouseIds.associateWith { whId ->
            val ref = summariesCollection.document("inventory_wh_$whId")
            transaction.get(ref).toObject(InventorySummary::class.java) ?: InventorySummary(id = ref.id, warehouseId = whId)
        }

        val scrapMap = mutableMapOf<String, ScrapWarehouse>()
        if (includeScrap && warehouseIds.isNotEmpty()) {
            warehouseIds.forEach { whId ->
                val ref = firestore.collection(ScrapWarehouse.COLLECTION_NAME).document("scrap_wh_$whId")
                val scrap = transaction.get(ref).toObject(ScrapWarehouse::class.java)
                            ?: ScrapWarehouse(id = "scrap_wh_$whId", parentWarehouseId = whId, name = "سكراب - $whId")
                scrapMap[whId] = scrap
            }
        }

        val globalRef = summariesCollection.document("inventory_global")
        val supplierRef = summariesCollection.document("suppliers_overview")
        val financialRef = summariesCollection.document("financial_status")
        val registryRef = summariesCollection.document("sync_registry")

        return SummarySnapshots(
            warehouseSummaries = whSummaries,
            inventoryGlobal = transaction.get(globalRef).toObject(InventorySummary::class.java) ?: InventorySummary(id = "inventory_global"),
            suppliersOverview = transaction.get(supplierRef).toObject(SuppliersOverview::class.java) ?: SuppliersOverview(),
            financialStatus = transaction.get(financialRef).toObject(FinancialStatus::class.java) ?: FinancialStatus(),
            syncRegistry = transaction.get(registryRef).toObject(SyncRegistry::class.java) ?: SyncRegistry(),
            scrapSnapshots = scrapMap
        )
    }

    /**
     * Refined architectural update: Updates a specific warehouse and the global summary.
     */
    fun applyInventoryUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        warehouseId: String,
        variantId: String,
        variant: ProductVariant,
        qtyChange: Int,
        costChange: Double = 0.0 // Deprecated
    ) {
        applyInventoryUpdates(transaction, snapshots, mapOf(warehouseId to mapOf(variantId to qtyChange)), mapOf(variantId to variant))
    }

    /**
     * NUCLEAR STRATEGY: Applies inventory updates across multiple warehouses and global in a single transaction.
     * Prevents document overwrites when multiple warehouses or items are modified.
     */
    fun applyInventoryUpdates(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        updates: Map<String, Map<String, Int>>, // warehouseId -> { variantId -> qtyChange }
        variantsMap: Map<String, ProductVariant?>
    ) {
        val globalSummary = snapshots.inventoryGlobal ?: InventorySummary(id = "inventory_global")
        val updatedItemsGlobal = globalSummary.items.toMutableMap()
        var globalValueDelta = 0.0

        val globalQtyChanges = mutableMapOf<String, Int>()

        updates.forEach { (whId, vChanges) ->
            val whSummary = snapshots.warehouseSummaries[whId] ?: InventorySummary(id = "inventory_wh_$whId", warehouseId = whId)
            val updatedItemsWh = whSummary.items.toMutableMap()
            var whValueDelta = 0.0

            vChanges.forEach { (vid, qtyChange) ->
                val variant = variantsMap[vid] ?: return@forEach
                val oldItemWh = updatedItemsWh[vid]
                val oldItemGlobal = updatedItemsGlobal[vid]

                globalQtyChanges[vid] = (globalQtyChanges[vid] ?: 0) + qtyChange

                val targetCost = when {
                    variant.weightedAverageCost > 0.001 -> variant.weightedAverageCost
                    oldItemGlobal != null && oldItemGlobal.weightedAverageCost > 0.001 -> oldItemGlobal.weightedAverageCost
                    oldItemWh != null && oldItemWh.weightedAverageCost > 0.001 -> oldItemWh.weightedAverageCost
                    else -> variant.weightedAverageCost
                }

                val newItemWh = (oldItemWh ?: InventorySummaryItem(
                    variantId = vid, productId = variant.productId, productName = variant.productName ?: "Unknown",
                    capacity = variant.capacity, barcode = variant.barcode, sellingPrice = variant.sellingPrice,
                    specification = variant.specification, isDiscontinued = variant.isDiscontinued
                )).copy(
                    currentStock = (oldItemWh?.currentStock ?: 0) + qtyChange,
                    weightedAverageCost = targetCost,
                    capacity = variant.capacity,
                    productName = variant.productName ?: "Unknown",
                    barcode = variant.barcode,
                    sellingPrice = variant.sellingPrice,
                    specification = variant.specification,
                    isDiscontinued = variant.isDiscontinued,
                    updatedAt = Date()
                )
                updatedItemsWh[vid] = newItemWh
                whValueDelta += (newItemWh.currentStock * newItemWh.weightedAverageCost) - ((oldItemWh?.currentStock ?: 0) * (oldItemWh?.weightedAverageCost ?: 0.0))
            }

            transaction.set(summariesCollection.document("inventory_wh_$whId"), whSummary.copy(
                items = updatedItemsWh,
                lastUpdated = Date(),
                totalValue = (whSummary.totalValue + whValueDelta).coerceAtLeast(0.0),
                version = whSummary.version + 1
            ))
        }

        // Apply Global Updates
        globalQtyChanges.forEach { (vid, totalQtyChange) ->
            val variant = variantsMap[vid] ?: return@forEach
            val oldItemGlobal = updatedItemsGlobal[vid]

            val targetCost = when {
                variant.weightedAverageCost > 0.001 -> variant.weightedAverageCost
                oldItemGlobal != null && oldItemGlobal.weightedAverageCost > 0.001 -> oldItemGlobal.weightedAverageCost
                else -> variant.weightedAverageCost
            }

            val newItemGlobal = (oldItemGlobal ?: InventorySummaryItem(
                variantId = vid, productId = variant.productId, productName = variant.productName ?: "Unknown",
                capacity = variant.capacity, barcode = variant.barcode, sellingPrice = variant.sellingPrice,
                specification = variant.specification, isDiscontinued = variant.isDiscontinued
            )).copy(
                currentStock = (oldItemGlobal?.currentStock ?: 0) + totalQtyChange,
                weightedAverageCost = targetCost,
                capacity = variant.capacity,
                productName = variant.productName ?: "Unknown",
                barcode = variant.barcode,
                sellingPrice = variant.sellingPrice,
                specification = variant.specification,
                isDiscontinued = variant.isDiscontinued,
                updatedAt = Date()
            )
            updatedItemsGlobal[vid] = newItemGlobal
            globalValueDelta += (newItemGlobal.currentStock * newItemGlobal.weightedAverageCost) - ((oldItemGlobal?.currentStock ?: 0) * (oldItemGlobal?.weightedAverageCost ?: 0.0))
        }

        transaction.set(summariesCollection.document("inventory_global"), globalSummary.copy(
            items = updatedItemsGlobal,
            lastUpdated = Date(),
            totalValue = (globalSummary.totalValue + globalValueDelta).coerceAtLeast(0.0),
            version = globalSummary.version + 1
        ))

        incrementSyncVersion(transaction, "inventory")
    }

    fun removeInventoryItem(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        warehouseIds: List<String>,
        variantId: String
    ) {
        val globalSummary = snapshots.inventoryGlobal ?: return
        val updatedItemsGlobal = globalSummary.items.toMutableMap()
        val removedItemGlobal = updatedItemsGlobal.remove(variantId)

        val globalValueReduction = (removedItemGlobal?.currentStock ?: 0) * (removedItemGlobal?.weightedAverageCost ?: 0.0)

        transaction.set(summariesCollection.document("inventory_global"), globalSummary.copy(
            items = updatedItemsGlobal,
            totalValue = (globalSummary.totalValue - globalValueReduction).coerceAtLeast(0.0),
            lastUpdated = Date(),
            version = globalSummary.version + 1
        ))

        warehouseIds.forEach { whId ->
            val whSummary = snapshots.warehouseSummaries[whId] ?: return@forEach
            val updatedItemsWh = whSummary.items.toMutableMap()
            val removedItemWh = updatedItemsWh.remove(variantId)

            val whValueReduction = (removedItemWh?.currentStock ?: 0) * (removedItemWh?.weightedAverageCost ?: 0.0)

            transaction.set(summariesCollection.document("inventory_wh_$whId"), whSummary.copy(
                items = updatedItemsWh,
                totalValue = (whSummary.totalValue - whValueReduction).coerceAtLeast(0.0),
                lastUpdated = Date(),
                version = whSummary.version + 1
            ))
        }

        incrementSyncVersion(transaction, "inventory")
    }

    /**
     * Refined bulk update: Group by warehouse to prevent document collisions.
     */
    fun applyBulkInventoryUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        warehouseId: String,
        variantsMap: Map<String, ProductVariant?>,
        qtyChanges: Map<String, Int>
    ) {
        applyInventoryUpdates(transaction, snapshots, mapOf(warehouseId to qtyChanges), variantsMap)
    }

    data class SupplierDelta(val name: String, val debitChange: Double = 0.0, val creditChange: Double = 0.0)

    fun applySupplierUpdates(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        deltas: Map<String, SupplierDelta>
    ) {
        val overview = snapshots.suppliersOverview ?: SuppliersOverview()
        val updatedSuppliers = overview.suppliers.toMutableMap()
        var totalDebtDelta = 0.0

        deltas.forEach { (supplierId, delta) ->
            val current = updatedSuppliers[supplierId] ?: SupplierSummaryItem(supplierId = supplierId, name = delta.name)
            val newDebit = current.totalDebit + delta.debitChange
            val newCredit = current.totalCredit + delta.creditChange

            updatedSuppliers[supplierId] = current.copy(
                totalDebit = newDebit,
                totalCredit = newCredit,
                currentBalance = newDebit - newCredit,
                updatedAt = Date()
            )
            totalDebtDelta += (delta.debitChange - delta.creditChange)
        }

        transaction.set(summariesCollection.document("suppliers_overview"), overview.copy(
            suppliers = updatedSuppliers,
            lastUpdated = Date(),
            totalSupplierDebt = overview.totalSupplierDebt + totalDebtDelta,
            version = overview.version + 1
        ))
        
        incrementSyncVersion(transaction, "suppliers")
    }

    fun applySupplierUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        supplierId: String,
        name: String,
        debitChange: Double = 0.0,
        creditChange: Double = 0.0
    ) {
        applySupplierUpdates(transaction, snapshots, mapOf(supplierId to SupplierDelta(name, debitChange, creditChange)))
    }

    data class ScrapDelta(val qtyChange: Int, val ampereChange: Double)

    fun applyScrapUpdates(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        deltas: Map<String, ScrapDelta>
    ) {
        deltas.forEach { (whId, delta) ->
            val scrap = snapshots.scrapSnapshots[whId] ?: ScrapWarehouse(id = "scrap_wh_$whId", parentWarehouseId = whId, name = "سكراب - $whId")
            val docRef = firestore.collection(ScrapWarehouse.COLLECTION_NAME).document("scrap_wh_$whId")

            transaction.set(docRef, scrap.copy(
                totalQuantity = (scrap.totalQuantity + delta.qtyChange).coerceAtLeast(0),
                totalAmperes = (scrap.totalAmperes + delta.ampereChange).coerceAtLeast(0.0)
            ))
        }
        incrementSyncVersion(transaction, "inventory")
    }

    fun applyScrapUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        warehouseId: String,
        qtyChange: Int,
        ampereChange: Double
    ) {
        applyScrapUpdates(transaction, snapshots, mapOf(warehouseId to ScrapDelta(qtyChange, ampereChange)))
    }

    data class FinancialDelta(
        val cashChange: Double = 0.0,
        val bankChange: Double = 0.0,
        val pendingCollectionChange: Double = 0.0,
        val billChange: Double = 0.0,
        val checkChange: Double = 0.0,
        val todayCollectionChange: Double = 0.0,
        val todayCollectionCountChange: Int = 0
    )

    fun applyFinancialUpdates(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        deltas: Map<String, FinancialDelta> // warehouseId -> delta
    ) {
        val status = snapshots.financialStatus ?: FinancialStatus()
        val isNewDay = !com.batterysales.utils.DateUtils.isSameDay(status.lastUpdated, Date())

        val baseStatus = if (isNewDay) {
            status.copy(
                todayCollection = 0.0,
                todayCollectionCount = 0,
                warehouseBalances = status.warehouseBalances.mapValues { (_, v) ->
                    v.copy(todayCollection = 0.0, todayCollectionCount = 0)
                }
            )
        } else status

        val updatedWarehouses = baseStatus.warehouseBalances.toMutableMap()

        var globalCash = baseStatus.globalCashBalance
        var globalBank = baseStatus.globalBankBalance
        var globalBills = baseStatus.totalUnpaidBills
        var globalChecks = baseStatus.totalUnpaidChecks
        var globalTodayColl = baseStatus.todayCollection
        var globalTodayCount = baseStatus.todayCollectionCount

        deltas.forEach { (whId, delta) ->
            val targetWhId = whId.ifBlank { "unassigned" }
            val currentWh = updatedWarehouses[targetWhId] ?: WarehouseBalance(warehouseId = targetWhId)

            updatedWarehouses[targetWhId] = currentWh.copy(
                cashBalance = currentWh.cashBalance + delta.cashChange,
                bankBalance = currentWh.bankBalance + delta.bankChange,
                pendingCollection = currentWh.pendingCollection + delta.pendingCollectionChange,
                todayCollection = (currentWh.todayCollection + delta.todayCollectionChange).coerceAtLeast(0.0),
                todayCollectionCount = (currentWh.todayCollectionCount + delta.todayCollectionCountChange).coerceAtLeast(0)
            )

            globalCash += delta.cashChange
            globalBank += delta.bankChange
            globalBills += delta.billChange
            globalChecks += delta.checkChange
            globalTodayColl += delta.todayCollectionChange
            globalTodayCount += delta.todayCollectionCountChange
        }

        transaction.set(summariesCollection.document("financial_status"), baseStatus.copy(
            warehouseBalances = updatedWarehouses,
            globalCashBalance = globalCash,
            globalBankBalance = globalBank,
            totalUnpaidBills = globalBills,
            totalUnpaidChecks = globalChecks,
            todayCollection = globalTodayColl.coerceAtLeast(0.0),
            todayCollectionCount = globalTodayCount.coerceAtLeast(0),
            lastUpdated = Date(),
            version = baseStatus.version + 1
        ))
        
        incrementSyncVersion(transaction, "financial")
    }

    fun applyFinancialUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        warehouseId: String,
        cashChange: Double = 0.0,
        bankChange: Double = 0.0,
        pendingCollectionChange: Double = 0.0,
        billChange: Double = 0.0,
        checkChange: Double = 0.0,
        todayCollectionChange: Double = 0.0,
        todayCollectionCountChange: Int = 0
    ) {
        applyFinancialUpdates(transaction, snapshots, mapOf(warehouseId to FinancialDelta(
            cashChange, bankChange, pendingCollectionChange, billChange, checkChange, todayCollectionChange, todayCollectionCountChange
        )))
    }

    fun updateSupplierReportCache(transaction: com.google.firebase.firestore.Transaction, supplierId: String, reportItem: com.batterysales.data.models.SupplierReportItem) {
        val cacheRef = firestore.collection("suppliers").document(supplierId).collection("cache").document("report")
        val cache = SupplierReportCache(
            supplierId = supplierId, balance = reportItem.balance, totalDebit = reportItem.totalDebit, totalCredit = reportItem.totalCredit,
            regularOrders = reportItem.regularOrders.map { it.toMap() }, obligatedOrders = reportItem.obligatedOrders.map { it.toMap() }, lastCalculated = Date()
        )
        transaction.set(cacheRef, cache)
    }

    fun invalidateSupplierReportCache(transaction: com.google.firebase.firestore.Transaction, supplierId: String) {
        val cacheRef = firestore.collection("suppliers").document(supplierId).collection("cache").document("report")
        transaction.delete(cacheRef)
    }

    suspend fun invalidateAllSupplierCaches() {
        val suppliers = firestore.collection("suppliers").get().await()
        suppliers.documents.forEach { supplierDoc ->
            firestore.collection("suppliers").document(supplierDoc.id)
                .collection("cache").document("report").delete().await()
        }
    }

    private fun com.batterysales.data.models.PurchaseOrderItem.toMap(): Map<String, Any> = mapOf(
        "id" to entry.id, 
        "totalCost" to entry.totalCost, 
        "remainingBalance" to remainingBalance,
        "referenceNumbers" to referenceNumbers, 
        "invoiceNumber" to entry.invoiceNumber, 
        "timestamp" to entry.timestamp, 
        "invoiceDate" to (entry.invoiceDate ?: entry.timestamp),
        "specification" to entry.specification,
        "settlementNotes" to entry.settlementNotes,
        "totalActualPaid" to totalActualPaid,
        "isCleared" to isCleared,
        "items" to items.map { item ->
            mapOf(
                "id" to item.id,
                "productVariantId" to item.productVariantId,
                "productName" to item.productName,
                "capacity" to item.capacity,
                "specification" to item.specification,
                "quantity" to item.quantity,
                "totalCost" to item.totalCost,
                "linkedAllocations" to item.linkedAllocations
            )
        }
    )

    suspend fun getSupplierReportCache(supplierId: String): SupplierReportCache? {
        val snap = firestore.collection("suppliers").document(supplierId).collection("cache").document("report").get().await()
        return snap.toObject(SupplierReportCache::class.java)
    }

    /**
     * NUCLEAR STRATEGY: Get Inventory from Cache OR fetch if SyncRegistry changed.
     */
    suspend fun getInventorySummary(warehouseId: String?, forceRefresh: Boolean = false): InventorySummary? {
        val key = warehouseId ?: "global"
        if (!forceRefresh && shouldSkipFetch("inventory")) {
            return cachedInventorySummary[key]
        }
        
        val docId = if (warehouseId != null) "inventory_wh_$warehouseId" else "inventory_global"
        val snap = summariesCollection.document(docId).get().await()
        val summary = snap.toObject(InventorySummary::class.java)
        
        if (summary != null) {
            val newCache = cachedInventorySummary.toMutableMap()
            newCache[key] = summary
            cachedInventorySummary = newCache
        }
        return summary
    }

    fun getInventorySummaryFlow(warehouseId: String?): Flow<InventorySummary> = callbackFlow {
        val docId = if (warehouseId != null) "inventory_wh_$warehouseId" else "inventory_global"
        val listener = summariesCollection.document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val summary = snapshot?.toObject(InventorySummary::class.java) ?: InventorySummary(id = docId, warehouseId = warehouseId)

                val key = warehouseId ?: "global"
                val newCache = cachedInventorySummary.toMutableMap()
                newCache[key] = summary
                cachedInventorySummary = newCache

                trySend(summary)
            }
        awaitClose { listener.remove() }
    }

    fun getSuppliersOverviewFlow(): Flow<SuppliersOverview> = callbackFlow {
        val listener = summariesCollection.document("suppliers_overview")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val overview = snapshot?.toObject(SuppliersOverview::class.java) ?: SuppliersOverview()
                cachedSuppliersOverview = overview
                trySend(overview)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getSuppliersOverview(forceRefresh: Boolean = false): SuppliersOverview? {
        if (!forceRefresh && shouldSkipFetch("suppliers")) {
            return cachedSuppliersOverview
        }
        
        val snap = summariesCollection.document("suppliers_overview").get().await()
        val overview = snap.toObject(SuppliersOverview::class.java)
        cachedSuppliersOverview = overview
        return overview
    }

    suspend fun getFinancialStatus(forceRefresh: Boolean = false): FinancialStatus? {
        if (!forceRefresh && shouldSkipFetch("financial")) {
            return cachedFinancialStatus
        }
        
        val snap = summariesCollection.document("financial_status").get().await()
        val status = snap.toObject(FinancialStatus::class.java)
        cachedFinancialStatus = status
        return status
    }

    fun getFinancialStatusFlow(): Flow<FinancialStatus> = callbackFlow {
        val listener = summariesCollection.document("financial_status")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val status = snapshot?.toObject(FinancialStatus::class.java) ?: FinancialStatus()
                cachedFinancialStatus = status
                trySend(status)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun shouldSkipFetch(type: String): Boolean {
        val registry = fetchSyncRegistry() ?: return false
        val cachedReg = cachedSyncRegistry ?: return false
        
        return when (type) {
            "inventory" -> registry.inventoryVersion <= cachedReg.inventoryVersion && cachedInventorySummary.isNotEmpty()
            "suppliers" -> registry.suppliersVersion <= cachedReg.suppliersVersion && cachedSuppliersOverview != null
            "financial" -> registry.financialVersion <= cachedReg.financialVersion && cachedFinancialStatus != null
            else -> false
        }
    }

    private suspend fun fetchSyncRegistry(): SyncRegistry? {
        return try {
            val snap = summariesCollection.document("sync_registry").get().await()
            val registry = snap.toObject(SyncRegistry::class.java)
            if (registry != null) cachedSyncRegistry = registry
            registry
        } catch (e: Exception) { null }
    }

    fun incrementSyncVersion(transaction: Transaction, type: String) {
        val registryRef = summariesCollection.document("sync_registry")
        val field = when (type) {
            "inventory" -> "inventoryVersion"
            "suppliers" -> "suppliersVersion"
            "financial" -> "financialVersion"
            else -> return
        }
        
        // Use Set with Merge to ensure document creation if it doesn't exist
        transaction.set(registryRef, mapOf(
            field to com.google.firebase.firestore.FieldValue.increment(1),
            "lastModified" to Date()
        ), com.google.firebase.firestore.SetOptions.merge())
    }

    suspend fun getSyncRegistry(): SyncRegistry? = fetchSyncRegistry()
}
 
