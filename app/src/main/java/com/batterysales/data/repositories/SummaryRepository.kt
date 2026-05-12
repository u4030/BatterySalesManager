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

    /**
     * Updates the inventory summary for a specific warehouse and the global summary.
     * This is called within a transaction to ensure atomicity.
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

        // 1. Fetch current summaries (Reads must come first)
        val whSnap = transaction.get(warehouseSummaryRef)
        val whSummary = whSnap.toObject(InventorySummary::class.java) ?: InventorySummary(id = "inventory_wh_$warehouseId", warehouseId = warehouseId)

        val globalSnap = transaction.get(globalSummaryRef)
        val globalSummary = globalSnap.toObject(InventorySummary::class.java) ?: InventorySummary(id = "inventory_global")

        // 2. Prepare Updates
        val updatedItemsWh = whSummary.items.toMutableMap()
        val currentItemWh = updatedItemsWh[variantId] ?: InventorySummaryItem(
            variantId = variantId,
            productId = variant.productId,
            productName = variant.productName ?: "Unknown",
            capacity = variant.capacity,
            barcode = variant.barcode,
            sellingPrice = variant.sellingPrice
        )

        val newQtyWh = currentItemWh.currentStock + qtyChange
        updatedItemsWh[variantId] = currentItemWh.copy(
            currentStock = newQtyWh,
            weightedAverageCost = variant.weightedAverageCost,
            updatedAt = Date()
        )

        val updatedItemsGlobal = globalSummary.items.toMutableMap()
        val currentItemGlobal = updatedItemsGlobal[variantId] ?: InventorySummaryItem(
            variantId = variantId,
            productId = variant.productId,
            productName = variant.productName ?: "Unknown",
            capacity = variant.capacity,
            barcode = variant.barcode,
            sellingPrice = variant.sellingPrice
        )
        val newQtyGlobal = currentItemGlobal.currentStock + qtyChange
        updatedItemsGlobal[variantId] = currentItemGlobal.copy(
            currentStock = newQtyGlobal,
            weightedAverageCost = variant.weightedAverageCost,
            updatedAt = Date()
        )

        // 3. Write updates
        transaction.set(warehouseSummaryRef, whSummary.copy(
            items = updatedItemsWh,
            lastUpdated = Date(),
            totalValue = whSummary.totalValue + costChange
        ))

        transaction.set(globalSummaryRef, globalSummary.copy(
            items = updatedItemsGlobal,
            lastUpdated = Date(),
            totalValue = globalSummary.totalValue + costChange
        ))
    }

    /**
     * Updates the supplier overview summary.
     */
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

        updatedSuppliers[supplierId] = current.copy(
            totalDebit = newDebit,
            totalCredit = newCredit,
            currentBalance = newDebit - newCredit,
            updatedAt = Date()
        )

        transaction.set(overviewRef, overview.copy(
            suppliers = updatedSuppliers,
            lastUpdated = Date(),
            totalSupplierDebt = overview.totalSupplierDebt + (debitChange - creditChange)
        ))
    }

    /**
     * Stores the pre-calculated FIFO report results for a supplier.
     */
    fun updateSupplierReportCache(
        transaction: Transaction,
        supplierId: String,
        reportItem: SupplierReportItem
    ) {
        val cacheRef = firestore.collection("suppliers").document(supplierId).collection("cache").document("report")

        val cache = SupplierReportCache(
            supplierId = supplierId,
            balance = reportItem.balance,
            totalDebit = reportItem.totalDebit,
            totalCredit = reportItem.totalCredit,
            // Convert domain items to simple maps for Firestore storage
            regularOrders = reportItem.regularOrders.map { it.toMap() },
            obligatedOrders = reportItem.obligatedOrders.map { it.toMap() },
            lastCalculated = Date()
        )

        transaction.set(cacheRef, cache)
    }

    // Helper to serialize PurchaseOrderItem (simplified for the example)
    private fun PurchaseOrderItem.toMap(): Map<String, Any> = mapOf(
        "id" to entry.id,
        "totalCost" to entry.totalCost,
        "remainingBalance" to remainingBalance,
        "referenceNumbers" to referenceNumbers,
        "invoiceNumber" to entry.invoiceNumber,
        "timestamp" to entry.timestamp
    )

    suspend fun getSupplierReportCache(supplierId: String): SupplierReportCache? {
        val snap = firestore.collection("suppliers").document(supplierId).collection("cache").document("report").get().await()
        return snap.toObject(SupplierReportCache::class.java)
    }

    suspend fun getInventorySummary(warehouseId: String? = null): InventorySummary? {
        val docId = if (warehouseId != null) "inventory_wh_$warehouseId" else "inventory_global"
        val snap = summariesCollection.document(docId).get().await()
        return snap.toObject(InventorySummary::class.java)
    }

    suspend fun getSuppliersOverview(): SuppliersOverview? {
        val snap = summariesCollection.document("suppliers_overview").get().await()
        return snap.toObject(SuppliersOverview::class.java)
    }
}
