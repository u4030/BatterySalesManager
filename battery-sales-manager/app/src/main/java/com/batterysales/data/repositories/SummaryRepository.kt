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
        val inventoryWh: InventorySummary?,
        val inventoryGlobal: InventorySummary?,
        val suppliersOverview: SuppliersOverview?,
        val financialStatus: FinancialStatus?,
        val syncRegistry: SyncRegistry?
    )

    fun getSummarySnapshots(transaction: Transaction, warehouseId: String? = null): SummarySnapshots {
        val whRef = if (warehouseId != null) summariesCollection.document("inventory_wh_$warehouseId") else null
        val globalRef = summariesCollection.document("inventory_global")
        val supplierRef = summariesCollection.document("suppliers_overview")
        val financialRef = summariesCollection.document("financial_status")
        val registryRef = summariesCollection.document("sync_registry")

        return SummarySnapshots(
            inventoryWh = whRef?.let { transaction.get(it).toObject(InventorySummary::class.java) ?: InventorySummary(id = it.id, warehouseId = warehouseId) },
            inventoryGlobal = transaction.get(globalRef).toObject(InventorySummary::class.java) ?: InventorySummary(id = "inventory_global"),
            suppliersOverview = transaction.get(supplierRef).toObject(SuppliersOverview::class.java) ?: SuppliersOverview(),
            financialStatus = transaction.get(financialRef).toObject(FinancialStatus::class.java) ?: FinancialStatus(),
            syncRegistry = transaction.get(registryRef).toObject(SyncRegistry::class.java) ?: SyncRegistry()
        )
    }

    fun applyInventoryUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        warehouseId: String,
        variantId: String,
        variant: ProductVariant,
        qtyChange: Int,
        costChange: Double = 0.0 // Deprecated, will recalculate from item values
    ) {
        val whSummary = snapshots.inventoryWh ?: InventorySummary(id = "inventory_wh_$warehouseId", warehouseId = warehouseId)
        val globalSummary = snapshots.inventoryGlobal ?: InventorySummary(id = "inventory_global")

        // Update Warehouse Summary
        val updatedItemsWh = whSummary.items.toMutableMap()
        val oldItemWh = updatedItemsWh[variantId]
        val newItemWh = (oldItemWh ?: InventorySummaryItem(
            variantId = variantId, productId = variant.productId, productName = variant.productName ?: "Unknown",
            capacity = variant.capacity, barcode = variant.barcode, sellingPrice = variant.sellingPrice,
            specification = variant.specification, isDiscontinued = variant.isDiscontinued
        )).copy(
            currentStock = (oldItemWh?.currentStock ?: 0) + qtyChange,
            weightedAverageCost = variant.weightedAverageCost,
            specification = variant.specification,
            isDiscontinued = variant.isDiscontinued,
            updatedAt = Date()
        )
        updatedItemsWh[variantId] = newItemWh

        // Update Global Summary
        val updatedItemsGlobal = globalSummary.items.toMutableMap()
        val oldItemGlobal = updatedItemsGlobal[variantId]
        val newItemGlobal = (oldItemGlobal ?: InventorySummaryItem(
            variantId = variantId, productId = variant.productId, productName = variant.productName ?: "Unknown",
            capacity = variant.capacity, barcode = variant.barcode, sellingPrice = variant.sellingPrice,
            specification = variant.specification, isDiscontinued = variant.isDiscontinued
        )).copy(
            currentStock = (oldItemGlobal?.currentStock ?: 0) + qtyChange,
            weightedAverageCost = variant.weightedAverageCost,
            specification = variant.specification,
            isDiscontinued = variant.isDiscontinued,
            updatedAt = Date()
        )
        updatedItemsGlobal[variantId] = newItemGlobal

        // Recalculate Total Values precisely to match Report formula (Stock * WAC)
        val whOldItemVal = (oldItemWh?.currentStock ?: 0) * (oldItemWh?.weightedAverageCost ?: 0.0)
        val whNewItemVal = newItemWh.currentStock * newItemWh.weightedAverageCost
        val newWhTotalValue = whSummary.totalValue - whOldItemVal + whNewItemVal

        val globalOldItemVal = (oldItemGlobal?.currentStock ?: 0) * (oldItemGlobal?.weightedAverageCost ?: 0.0)
        val globalNewItemVal = newItemGlobal.currentStock * newItemGlobal.weightedAverageCost
        val newGlobalTotalValue = globalSummary.totalValue - globalOldItemVal + globalNewItemVal

        transaction.set(summariesCollection.document("inventory_wh_$warehouseId"), whSummary.copy(items = updatedItemsWh, lastUpdated = Date(), totalValue = newWhTotalValue, version = whSummary.version + 1))
        transaction.set(summariesCollection.document("inventory_global"), globalSummary.copy(items = updatedItemsGlobal, lastUpdated = Date(), totalValue = newGlobalTotalValue, version = globalSummary.version + 1))
        
        incrementSyncVersion(transaction, "inventory")
    }

    fun applyBulkInventoryUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        warehouseId: String,
        variantsMap: Map<String, ProductVariant?>,
        qtyChanges: Map<String, Int>
    ) {
        val whSummary = snapshots.inventoryWh ?: InventorySummary(id = "inventory_wh_$warehouseId", warehouseId = warehouseId)
        val globalSummary = snapshots.inventoryGlobal ?: InventorySummary(id = "inventory_global")

        val updatedItemsWh = whSummary.items.toMutableMap()
        val updatedItemsGlobal = globalSummary.items.toMutableMap()
        
        var whValueDelta = 0.0
        var globalValueDelta = 0.0

        qtyChanges.forEach { (variantId, qtyChange) ->
            val variant = variantsMap[variantId] ?: return@forEach
            
            // Warehouse Map
            val oldItemWh = updatedItemsWh[variantId]
            val newItemWh = (oldItemWh ?: InventorySummaryItem(
                variantId = variantId, productId = variant.productId, productName = variant.productName ?: "Unknown",
                capacity = variant.capacity, barcode = variant.barcode, sellingPrice = variant.sellingPrice,
                specification = variant.specification, isDiscontinued = variant.isDiscontinued
            )).copy(
                currentStock = (oldItemWh?.currentStock ?: 0) + qtyChange,
                weightedAverageCost = variant.weightedAverageCost,
                specification = variant.specification,
                isDiscontinued = variant.isDiscontinued,
                updatedAt = Date()
            )
            updatedItemsWh[variantId] = newItemWh
            whValueDelta += (newItemWh.currentStock * newItemWh.weightedAverageCost) - ((oldItemWh?.currentStock ?: 0) * (oldItemWh?.weightedAverageCost ?: 0.0))

            // Global Map
            val oldItemGlobal = updatedItemsGlobal[variantId]
            val newItemGlobal = (oldItemGlobal ?: InventorySummaryItem(
                variantId = variantId, productId = variant.productId, productName = variant.productName ?: "Unknown",
                capacity = variant.capacity, barcode = variant.barcode, sellingPrice = variant.sellingPrice,
                specification = variant.specification, isDiscontinued = variant.isDiscontinued
            )).copy(
                currentStock = (oldItemGlobal?.currentStock ?: 0) + qtyChange,
                weightedAverageCost = variant.weightedAverageCost,
                specification = variant.specification,
                isDiscontinued = variant.isDiscontinued,
                updatedAt = Date()
            )
            updatedItemsGlobal[variantId] = newItemGlobal
            globalValueDelta += (newItemGlobal.currentStock * newItemGlobal.weightedAverageCost) - ((oldItemGlobal?.currentStock ?: 0) * (oldItemGlobal?.weightedAverageCost ?: 0.0))
        }

        transaction.set(summariesCollection.document("inventory_wh_$warehouseId"), whSummary.copy(items = updatedItemsWh, lastUpdated = Date(), totalValue = whSummary.totalValue + whValueDelta, version = whSummary.version + 1))
        transaction.set(summariesCollection.document("inventory_global"), globalSummary.copy(items = updatedItemsGlobal, lastUpdated = Date(), totalValue = globalSummary.totalValue + globalValueDelta, version = globalSummary.version + 1))
        
        incrementSyncVersion(transaction, "inventory")
    }

    fun applySupplierUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        supplierId: String,
        name: String,
        debitChange: Double = 0.0,
        creditChange: Double = 0.0
    ) {
        val overview = snapshots.suppliersOverview ?: SuppliersOverview()
        val updatedSuppliers = overview.suppliers.toMutableMap()
        val current = updatedSuppliers[supplierId] ?: SupplierSummaryItem(supplierId = supplierId, name = name)
        
        val newDebit = current.totalDebit + debitChange
        val newCredit = current.totalCredit + creditChange
        
        updatedSuppliers[supplierId] = current.copy(totalDebit = newDebit, totalCredit = newCredit, currentBalance = newDebit - newCredit, updatedAt = Date())

        transaction.set(summariesCollection.document("suppliers_overview"), overview.copy(suppliers = updatedSuppliers, lastUpdated = Date(), totalSupplierDebt = overview.totalSupplierDebt + (debitChange - creditChange), version = overview.version + 1))
        
        incrementSyncVersion(transaction, "suppliers")
    }

    fun applyFinancialUpdate(
        transaction: Transaction,
        snapshots: SummarySnapshots,
        warehouseId: String,
        cashChange: Double = 0.0,
        bankChange: Double = 0.0,
        pendingCollectionChange: Double = 0.0,
        billChange: Double = 0.0,
        checkChange: Double = 0.0
    ) {
        val status = snapshots.financialStatus ?: FinancialStatus()
        val updatedWarehouses = status.warehouseBalances.toMutableMap()
        val currentWh = updatedWarehouses[warehouseId] ?: WarehouseBalance(warehouseId = warehouseId)
        
        updatedWarehouses[warehouseId] = currentWh.copy(
            cashBalance = currentWh.cashBalance + cashChange,
            bankBalance = currentWh.bankBalance + bankChange,
            pendingCollection = currentWh.pendingCollection + pendingCollectionChange
        )

        transaction.set(summariesCollection.document("financial_status"), status.copy(
            warehouseBalances = updatedWarehouses,
            globalCashBalance = status.globalCashBalance + cashChange,
            globalBankBalance = status.globalBankBalance + bankChange,
            totalUnpaidBills = status.totalUnpaidBills + billChange,
            totalUnpaidChecks = status.totalUnpaidChecks + checkChange,
            lastUpdated = Date(),
            version = status.version + 1
        ))
        
        incrementSyncVersion(transaction, "financial")
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
 
