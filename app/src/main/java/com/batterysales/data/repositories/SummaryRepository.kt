package com.batterysales.data.repositories

import com.batterysales.data.models.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
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
     * Updates the inventory summary for a specific warehouse and the global summary.
     */
    fun updateInventorySummary(
        transaction: Transaction,
        warehouseId: String,
        variantId: String,
        variant: ProductVariant,
        qtyChange: Int,
        costChange: Double = 0.0
    ) {
        val warehouseSummaryRef = summariesCollection.document("inventory_wh_$warehouseId")
        val globalSummaryRef = summariesCollection.document("inventory_global")

        val whSnap = transaction.get(warehouseSummaryRef)
        val whSummary = whSnap.toObject(InventorySummary::class.java) ?: InventorySummary(id = "inventory_wh_$warehouseId", warehouseId = warehouseId)

        val globalSnap = transaction.get(globalSummaryRef)
        val globalSummary = globalSnap.toObject(InventorySummary::class.java) ?: InventorySummary(id = "inventory_global")

        val updatedItemsWh = whSummary.items.toMutableMap()
        val currentItemWh = updatedItemsWh[variantId] ?: InventorySummaryItem(
            variantId = variantId, productId = variant.productId, productName = variant.productName ?: "Unknown",
            capacity = variant.capacity, barcode = variant.barcode, sellingPrice = variant.sellingPrice
        )
        updatedItemsWh[variantId] = currentItemWh.copy(currentStock = currentItemWh.currentStock + qtyChange, weightedAverageCost = variant.weightedAverageCost, updatedAt = Date())

        val updatedItemsGlobal = globalSummary.items.toMutableMap()
        val currentItemGlobal = updatedItemsGlobal[variantId] ?: InventorySummaryItem(
            variantId = variantId, productId = variant.productId, productName = variant.productName ?: "Unknown",
            capacity = variant.capacity, barcode = variant.barcode, sellingPrice = variant.sellingPrice
        )
        updatedItemsGlobal[variantId] = currentItemGlobal.copy(currentStock = currentItemGlobal.currentStock + qtyChange, weightedAverageCost = variant.weightedAverageCost, updatedAt = Date())

        transaction.set(warehouseSummaryRef, whSummary.copy(items = updatedItemsWh, lastUpdated = Date(), totalValue = whSummary.totalValue + costChange))
        transaction.set(globalSummaryRef, globalSummary.copy(items = updatedItemsGlobal, lastUpdated = Date(), totalValue = globalSummary.totalValue + costChange))

        incrementSyncVersion(transaction, "inventory")
    }

    fun updateSupplierOverview(
        transaction: Transaction,
        supplierId: String,
        name: String,
        debitChange: Double = 0.0,
        creditChange: Double = 0.0
    ) {
        val overviewRef = summariesCollection.document("suppliers_overview")
        val overviewSnap = transaction.get(overviewRef)
        val overview = overviewSnap.toObject(SuppliersOverview::class.java) ?: SuppliersOverview()

        val updatedSuppliers = overview.suppliers.toMutableMap()
        val current = updatedSuppliers[supplierId] ?: SupplierSummaryItem(supplierId = supplierId, name = name)

        val newDebit = current.totalDebit + debitChange
        val newCredit = current.totalCredit + creditChange

        updatedSuppliers[supplierId] = current.copy(totalDebit = newDebit, totalCredit = newCredit, currentBalance = newDebit - newCredit, updatedAt = Date())

        transaction.set(overviewRef, overview.copy(suppliers = updatedSuppliers, lastUpdated = Date(), totalSupplierDebt = overview.totalSupplierDebt + (debitChange - creditChange)))

        incrementSyncVersion(transaction, "suppliers")
    }

    fun updateFinancialStatus(
        transaction: Transaction,
        warehouseId: String,
        cashChange: Double = 0.0,
        bankChange: Double = 0.0,
        pendingCollectionChange: Double = 0.0,
        billChange: Double = 0.0,
        checkChange: Double = 0.0
    ) {
        val statusRef = summariesCollection.document("financial_status")
        val statusSnap = transaction.get(statusRef)
        val status = statusSnap.toObject(FinancialStatus::class.java) ?: FinancialStatus()

        val updatedWarehouses = status.warehouseBalances.toMutableMap()
        val currentWh = updatedWarehouses[warehouseId] ?: WarehouseBalance(warehouseId = warehouseId)

        updatedWarehouses[warehouseId] = currentWh.copy(
            cashBalance = currentWh.cashBalance + cashChange,
            bankBalance = currentWh.bankBalance + bankChange,
            pendingCollection = currentWh.pendingCollection + pendingCollectionChange
        )

        transaction.set(statusRef, status.copy(
            warehouseBalances = updatedWarehouses,
            globalCashBalance = status.globalCashBalance + cashChange,
            globalBankBalance = status.globalBankBalance + bankChange,
            totalUnpaidBills = status.totalUnpaidBills + billChange,
            totalUnpaidChecks = status.totalUnpaidChecks + checkChange,
            lastUpdated = Date()
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

    private fun com.batterysales.data.models.PurchaseOrderItem.toMap(): Map<String, Any> = mapOf(
        "id" to entry.id, "totalCost" to entry.totalCost, "remainingBalance" to remainingBalance,
        "referenceNumbers" to referenceNumbers, "invoiceNumber" to entry.invoiceNumber, "timestamp" to entry.timestamp
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
        transaction.update(registryRef, field, com.google.firebase.firestore.FieldValue.increment(1))
        transaction.update(registryRef, "lastModified", Date())
    }

    suspend fun getSyncRegistry(): SyncRegistry? = fetchSyncRegistry()
}
