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
                _migrationStatus.value = "جاري فحص صحة البيانات..."
                
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
                val suppliers = supplierRepository.getSuppliersOnce()
                val suppliersOverview = summaryRepository.getSuppliersOverview(forceRefresh = true)
                suppliers.forEach { s ->
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
        
        // This is a heavy operation to initialize the new Summary-First system
        val products = productRepository.getProductsOnce()
        val variants = productVariantRepository.getAllVariants()
        val warehouses = warehouseRepository.getWarehousesOnce()
        val suppliers = supplierRepository.getSuppliersOnce()
        
        // --- 1. Clear existing alerts ---
        val alertsSnap = firestore.collection(SystemAlert.COLLECTION_NAME).get().await()
        val alertBatch = firestore.batch()
        alertsSnap.documents.forEach { alertBatch.delete(it.reference) }
        alertBatch.commit().await()

        // 2. Rebuild Inventory Summaries
        val globalItems = mutableMapOf<String, InventorySummaryItem>()
        val warehouseItems = mutableMapOf<String, MutableMap<String, InventorySummaryItem>>()

        variants.forEach { variant: ProductVariant ->
            val productName = products.find { it.id == variant.productId }?.name ?: "Unknown"
            val totalQty = variant.currentStock?.values?.sum() ?: 0
            
            val globalItem = InventorySummaryItem(
                variantId = variant.id,
                productId = variant.productId,
                productName = productName,
                capacity = variant.capacity,
                specification = variant.specification,
                barcode = variant.barcode,
                currentStock = totalQty,
                weightedAverageCost = variant.weightedAverageCost,
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

        // 3. Rebuild Suppliers Overview
        val supplierItems = suppliers.associate { s ->
            s.id to SupplierSummaryItem(
                supplierId = s.id,
                name = s.name,
                currentBalance = s.currentBalance,
                totalDebit = s.totalDebit,
                totalCredit = s.totalCredit
            )
        }
        firestore.collection("summaries").document("suppliers_overview")
            .set(SuppliersOverview(suppliers = supplierItems)).await()

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
        
        firestore.collection("summaries").document("financial_status")
            .set(FinancialStatus(
                warehouseBalances = warehouseBalances,
                globalCashBalance = globalCash,
                globalBankBalance = globalBank,
                lastUpdated = Date()
            )).await()

        // 4. Reset Sync Registry
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("summaries").document("sync_registry")
            .set(SyncRegistry(lastModified = Date())).await()
    }
}
