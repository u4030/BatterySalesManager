package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.utils.SettingsManager
import com.batterysales.data.repositories.*
import com.batterysales.data.models.*
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val stockEntryRepository: StockEntryRepository,
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val supplierRepository: SupplierRepository,
    private val billRepository: BillRepository,
    private val summaryRepository: SummaryRepository,
    private val accountingRepository: AccountingRepository,
    private val bankRepository: BankRepository,
    private val invoiceRepository: InvoiceRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _migrationStatus = MutableStateFlow<String?>(null)
    val migrationStatus = _migrationStatus.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating = _isMigrating.asStateFlow()

    private val _summaryStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val summaryStatus = _summaryStatus.asStateFlow()

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val fontSizeScale: StateFlow<Float> = settingsManager.fontSizeScale
    val isBold: StateFlow<Boolean> = settingsManager.isBold
    val scaleInputText: StateFlow<Boolean> = settingsManager.scaleInputText

    fun setFontSizeScale(scale: Float) {
        viewModelScope.launch {
            settingsManager.setFontSizeScale(scale)
        }
    }

    fun setIsBold(bold: Boolean) {
        viewModelScope.launch {
            settingsManager.setIsBold(bold)
        }
    }

    fun setScaleInputText(scale: Boolean) {
        viewModelScope.launch {
            settingsManager.setScaleInputText(scale)
        }
    }

    fun startDataMigration() {
        if (_isMigrating.value) return
        viewModelScope.launch {
            try {
                _isMigrating.value = true
                _migrationStatus.value = "جاري ترحيل البيانات وإعادة بناء الملخصات... يرجى عدم إغلاق التطبيق"
                
                invoiceRepository.migrateInvoices()
                stockEntryRepository.migrateStockEntries(billRepository)

                // --- NEW: Fix Product-Supplier association for legacy data ---
                _migrationStatus.value = "جاري تحديث روابط الموردين للمنتجات القديمة..."
                migrateProductSuppliers()

                stockEntryRepository.migrateAllVariants(productRepository, supplierRepository, billRepository)
                
                // Rebuild Summaries from scratch
                rebuildAllSummaries()

                settingsManager.setMigrationDone(true)
                _migrationStatus.value = "تم ترحيل البيانات بنجاح"
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Migration error", e)
                _migrationStatus.value = "فشل الترحيل: ${e.message}"
            } finally {
                _isMigrating.value = false
            }
        }
    }

    init {
        checkSummariesStatus()
    }

    private fun checkSummariesStatus() {
        viewModelScope.launch {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val summaries = listOf("inventory_global", "suppliers_overview")
                val status = mutableMapOf<String, Boolean>()
                
                summaries.forEach { id ->
                    val snap = firestore.collection("summaries").document(id).get().await()
                    status[id] = snap.exists()
                }
                _summaryStatus.value = status
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error checking summary status", e)
            }
        }
    }

    fun clearMigrationStatus() {
        _migrationStatus.value = null
    }

    fun performHealthCheck() {
        if (_isMigrating.value) return
        viewModelScope.launch {
            try {
                _isMigrating.value = true
                _migrationStatus.value = "جاري فحص صحة البيانات وتنظيف التكرارات..."
                
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                com.batterysales.utils.DataSanitizer.sanitizeVariants(firestore)

                // --- NEW: Reset all FIFO links to ensure pure logic ---
                _migrationStatus.value = "جاري تصفير الارتباطات اليدوية وإعادة بناء الـ FIFO..."
                com.batterysales.utils.DataSanitizer.clearAllManualLinks(firestore)
                
                // Trigger full sync for all suppliers to rebuild pools
                val suppliersList = supplierRepository.getSuppliersOnce()
                suppliersList.forEach { s ->
                    try { billRepository.syncSupplierFinancials(s.id) } catch (e: Exception) {}
                }

                // 1. Audit Inventory
                val variants = productVariantRepository.getAllVariants()
                val globalSummary = summaryRepository.getInventorySummary(null, forceRefresh = true)
                
                var issuesFound = 0
                variants.forEach { v ->
                    val expectedQty = v.currentStock?.values?.sum() ?: 0
                    val actualQty = globalSummary?.items?.get(v.id)?.currentStock ?: 0
                    if (expectedQty != actualQty) {
                        issuesFound++
                        Log.w("HealthCheck", "Inventory mismatch for ${v.id}: Variant has $expectedQty, Summary has $actualQty")
                    }
                }

                // 2. Audit Suppliers
                val suppliersOverview = summaryRepository.getSuppliersOverview(forceRefresh = true)
                suppliersList.forEach { s ->
                    val expectedBal = s.currentBalance
                    val actualBal = suppliersOverview?.suppliers?.get(s.id)?.currentBalance ?: 0.0
                    if (Math.abs(expectedBal - actualBal) > 0.001) {
                        issuesFound++
                        Log.w("HealthCheck", "Supplier mismatch for ${s.id}: Supplier has $expectedBal, Summary has $actualBal")
                    }
                }

                if (issuesFound > 0) {
                    _migrationStatus.value = "تم العثور على $issuesFound تعارضات في البيانات. جاري إعادة بناء الملخصات وتصحيح الارتباطات..."
                    
                    // Fix Manual Allocations on Bills before rebuilding
                    fixBillAllocations()
                    
                    rebuildAllSummaries()
                    _migrationStatus.value = "تمت معالجة التعارضات وإعادة بناء الملخصات بنجاح ✅"
                } else {
                    _migrationStatus.value = "حالة البيانات: سليمة ومطابقة 100% ✅"
                }

            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Health check error", e)
                _migrationStatus.value = "فشل الفحص: ${e.message}"
            } finally {
                _isMigrating.value = false
            }
        }
    }

    private suspend fun fixBillAllocations() {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val billsSnap = firestore.collection(Bill.COLLECTION_NAME).get().await()
        val entriesSnap = firestore.collection(StockEntry.COLLECTION_NAME).get().await()
        
        val entries = entriesSnap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        
        val batch = firestore.batch()
        var updates = 0
        
        billsSnap.documents.forEach { doc ->
            val bill = doc.toObject(Bill::class.java) ?: return@forEach
            val actualAllocated = entries.sumOf { it.linkedAllocations[doc.id] ?: 0.0 }
            
            if (Math.abs(actualAllocated - bill.manualAllocation) > 0.001) {
                batch.update(doc.reference, "manualAllocation", actualAllocated)
                updates++
            }
        }
        
        if (updates > 0) batch.commit().await()
    }

    private suspend fun rebuildAllSummaries() {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        // --- 0. PURGE OLD SUMMARIES ---
        val oldSummaries = firestore.collection("summaries").get().await()
        if (!oldSummaries.isEmpty) {
            val batch = firestore.batch()
            oldSummaries.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        // This is a heavy operation to initialize the new Summary-First system
        val products = productRepository.getProductsOnce()
        val warehouses = warehouseRepository.getWarehousesOnce()
        val suppliers = supplierRepository.getSuppliersOnce()
        
        // --- 0. Backfill Specifications in StockEntries ---
        val variants = productVariantRepository.getAllVariants()
        val variantsMap = variants.associate { it.id to it.specification }
        val entriesSnap = firestore.collection(StockEntry.COLLECTION_NAME).get().await()
        entriesSnap.documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            var count = 0
            chunk.forEach { doc ->
                val vid = doc.getString("productVariantId") ?: ""
                val currentSpec = doc.getString("specification") ?: ""
                if (vid.isNotEmpty() && currentSpec.isEmpty() && variantsMap.containsKey(vid)) {
                    batch.update(doc.reference, "specification", variantsMap[vid])
                    count++
                }
            }
            if (count > 0) batch.commit().await()
        }

        // --- 1. Clear existing alerts ---
        val alertsSnap = firestore.collection(SystemAlert.COLLECTION_NAME).get().await()
        val alertBatch = firestore.batch()
        alertsSnap.documents.forEach { alertBatch.delete(it.reference) }
        alertBatch.commit().await()

        // 2. Rebuild Inventory Summaries
        val globalItems = mutableMapOf<String, InventorySummaryItem>()
        val warehouseItems = mutableMapOf<String, MutableMap<String, InventorySummaryItem>>()

        variants.forEach { variant: ProductVariant ->
            val totalQty = variant.currentStock?.values?.sum() ?: 0
            
            // Strict Exclusion: Never include archived variants in the summaries
            if (variant.archived) return@forEach

            val productName = products.find { it.id == variant.productId }?.name ?: "Unknown"
            
            // Nuclear Cost Correction: Re-calculate actual Last Purchase Cost from history
            val actualLastCost = try {
                stockEntryRepository.getWeightedAverageCost(variant.id, null)
            } catch (e: Exception) {
                variant.weightedAverageCost
            }

            // Permanently correct the variant document if there is drift
            if (Math.abs(variant.weightedAverageCost - actualLastCost) > 0.001) {
                firestore.collection(ProductVariant.COLLECTION_NAME).document(variant.id)
                    .update("weightedAverageCost", actualLastCost)
            }

            val globalItem = InventorySummaryItem(
                variantId = variant.id,
                productId = variant.productId,
                productName = productName,
                capacity = variant.capacity,
                specification = variant.specification,
                barcode = variant.barcode,
                currentStock = totalQty,
                weightedAverageCost = actualLastCost,
                sellingPrice = variant.sellingPrice,
                isDiscontinued = variant.isDiscontinued
            )
            globalItems[variant.id] = globalItem

            variant.currentStock?.forEach { (whId, qty) ->
                val whMap = warehouseItems.getOrPut(whId) { mutableMapOf() }
                whMap[variant.id] = globalItem.copy(currentStock = qty)

                // --- GENERATE ALERTS DURING REBUILD ---
                val threshold = variant.minQuantities[whId] ?: variant.minQuantity
                if (!variant.isDiscontinued && threshold > 0 && qty <= threshold) {
                    val alertRef = firestore.collection(SystemAlert.COLLECTION_NAME).document("low_stock_${variant.id}_$whId")
                    firestore.collection(SystemAlert.COLLECTION_NAME).document(alertRef.id).set(SystemAlert(
                        id = alertRef.id,
                        type = SystemAlert.TYPE_LOW_STOCK,
                        title = "مخزون منخفض: $productName",
                        message = "${variant.capacity}A | الكمية الحالية: $qty (الحد: $threshold)",
                        relatedId = variant.id,
                        warehouseId = whId,
                        timestamp = Date()
                    ))
                }
            }
        }

        // Save Global
        firestore.collection("summaries").document("inventory_global")
            .set(InventorySummary(id = "inventory_global", items = globalItems)).await()

        // Save Warehouses
        warehouseItems.forEach { (whId, items) ->
            firestore.collection("summaries").document("inventory_wh_$whId")
                .set(InventorySummary(id = "inventory_wh_$whId", warehouseId = whId, items = items)).await()
        }

        // 3. Rebuild Suppliers Overview (Deep Audit Strategy)
        val activeVariantIds = variants.filter { !it.archived }.map { it.id }.toSet()

        val approvedEntries = entriesSnap.documents.mapNotNull { it.toObject(StockEntry::class.java) }
            .filter { it.status == "approved" && activeVariantIds.contains(it.productVariantId) }

        val billsSnap = firestore.collection(Bill.COLLECTION_NAME).get().await()
        val allBills = billsSnap.documents.mapNotNull { it.toObject(Bill::class.java) }

        val supplierItems = suppliers.associate { s ->
            // Recalculate everything from raw records to purge archived/broken data
            val debit = approvedEntries.filter { it.supplierId == s.id }.sumOf { it.getNetCost() }
            val credit = allBills.filter { it.supplierId == s.id }.sumOf { it.paidAmount }

            // Permanently sync the Supplier document itself
            firestore.collection("suppliers").document(s.id).update(mapOf(
                "totalDebit" to debit,
                "totalCredit" to credit,
                "currentBalance" to (debit - credit)
            ))

            s.id to SupplierSummaryItem(
                supplierId = s.id,
                name = s.name,
                currentBalance = debit - credit,
                totalDebit = debit,
                totalCredit = credit,
                updatedAt = Date()
            )
        }

        firestore.collection("summaries").document("suppliers_overview")
            .set(SuppliersOverview(
                suppliers = supplierItems,
                totalSupplierDebt = supplierItems.values.sumOf { it.currentBalance },
                lastUpdated = Date()
            )).await()

        // 4. Rebuild Financial Status (High Precision Calculation)
        val warehouseBalances = warehouses.associate { wh ->
            val cash = accountingRepository.getCurrentBalance(wh.id, "cash")
            val bank = accountingRepository.getCurrentBalance(wh.id, "bank")
            val debt = invoiceRepository.getTotalDebtForWarehouse(wh.id)
            
            wh.id to WarehouseBalance(
                warehouseId = wh.id,
                cashBalance = cash,
                bankBalance = bank,
                pendingCollection = debt
            )
        }
        
        // Calculate Globals via raw aggregation to be 100% sure
        val globalCash = accountingRepository.getCurrentBalance(null, "cash")
        val globalBank = bankRepository.getCurrentBalance()
        
        val globalUnpaidBills = allBills.filter { it.billType == BillType.BILL && it.status != BillStatus.PAID }.sumOf { it.amount - it.paidAmount }
        val globalUnpaidChecks = allBills.filter { it.billType == BillType.CHECK && it.status != BillStatus.PAID }.sumOf { it.amount - it.paidAmount }

        firestore.collection("summaries").document("financial_status")
            .set(FinancialStatus(
                warehouseBalances = warehouseBalances,
                globalCashBalance = globalCash,
                globalBankBalance = globalBank,
                totalUnpaidBills = globalUnpaidBills,
                totalUnpaidChecks = globalUnpaidChecks,
                lastUpdated = Date()
            )).await()

        // Update SystemStats Document
        val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)
        statsRef.set(SystemStats(
            totalSupplierDebt = supplierItems.values.sumOf { it.currentBalance },
            totalCustomerDebt = warehouseBalances.values.sumOf { it.pendingCollection },
            totalInventoryValue = globalItems.values.sumOf { it.currentStock * it.weightedAverageCost },
            totalInventoryQuantity = globalItems.values.sumOf { it.currentStock },
            totalCashBalance = globalCash,
            totalBankBalance = globalBank,
            totalUnpaidBills = globalUnpaidBills,
            totalUnpaidChecks = globalUnpaidChecks,
            updatedAt = Date()
        )).await()

        // 4. Reset Sync Registry
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("summaries").document("sync_registry")
            .set(SyncRegistry(lastModified = Date())).await()
    }

    private suspend fun migrateProductSuppliers() {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val products = productRepository.getAllProducts()
        val productsMap = products.associateBy { it.id }

        // --- PROPAGATE ARCHIVE ---
        // 1. Mark variants with archived OR missing products as archived
        val allVariants = productVariantRepository.getAllVariants()
        val variantsToArchive = allVariants.filter { v ->
            val parent = productsMap[v.productId]
            v.archived != true && (parent == null || parent.archived)
        }

        variantsToArchive.chunked(50).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { v -> batch.update(firestore.collection(ProductVariant.COLLECTION_NAME).document(v.id), "archived", true) }
            batch.commit().await()
        }

        // 2. Mark stock entries for archived variants as archived
        val archivedVariantIds = allVariants.filter { it.archived || variantsToArchive.any { va -> va.id == it.id } }.map { it.id }.toSet()
        val entriesSnap = firestore.collection(StockEntry.COLLECTION_NAME).get().await()
        entriesSnap.documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            var count = 0
            chunk.forEach { doc ->
                val vid = doc.getString("productVariantId") ?: ""
                val status = doc.getString("status") ?: ""
                if (archivedVariantIds.contains(vid) && status != "archived") {
                    batch.update(doc.reference, "status", "archived")
                    count++
                }
            }
            if (count > 0) batch.commit().await()
        }

        val approvedEntriesSnap = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "approved")
            .get().await()

        val entries = approvedEntriesSnap.documents.mapNotNull { it.toObject(StockEntry::class.java) }

        products.filter { it.supplierId.isEmpty() }.chunked(50).forEach { chunk ->
            val batch = firestore.batch()
            var count = 0
            chunk.forEach { product ->
                // Find any approved stock entry for any variant of this product to find the supplier
                val variants = productVariantRepository.getVariantsForProduct(product.id)
                val variantIds = variants.map { it.id }.toSet()

                val relatedEntry = entries.find { it.productVariantId in variantIds && it.supplierId.isNotEmpty() }
                if (relatedEntry != null) {
                    batch.update(firestore.collection(Product.COLLECTION_NAME).document(product.id), "supplierId", relatedEntry.supplierId)
                    count++
                }
            }
            if (count > 0) batch.commit().await()
        }
    }
}
 
