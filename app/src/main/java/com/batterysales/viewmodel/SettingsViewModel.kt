package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.utils.SettingsManager
import com.batterysales.data.repositories.*
import com.batterysales.data.models.*
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val billRepository: BillRepository
) : ViewModel() {

    private val _migrationStatus = MutableStateFlow<String?>(null)
    val migrationStatus = _migrationStatus.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating = _isMigrating.asStateFlow()

    private val _summaryStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val summaryStatus = _summaryStatus.asStateFlow()

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

                stockEntryRepository.migrateStockEntries()
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

    private suspend fun rebuildAllSummaries() {
        // This is a heavy operation to initialize the new Summary-First system
        val products = productRepository.getProductsOnce()
        val variants = productVariantRepository.getAllVariants()
        val warehouses = warehouseRepository.getWarehousesOnce()
        val suppliers = supplierRepository.getSuppliersOnce()

        // 1. Rebuild Inventory Summaries
        val globalItems = mutableMapOf<String, InventorySummaryItem>()
        val warehouseItems = mutableMapOf<String, MutableMap<String, InventorySummaryItem>>()

        variants.forEach { variant: ProductVariant ->
            val productName = products.find { it.id == variant.productId }?.name ?: "Unknown"

            val globalItem = InventorySummaryItem(
                variantId = variant.id,
                productId = variant.productId,
                productName = productName,
                capacity = variant.capacity,
                barcode = variant.barcode,
                currentStock = variant.currentStock?.values?.sum() ?: 0,
                weightedAverageCost = variant.weightedAverageCost,
                sellingPrice = variant.sellingPrice
            )
            globalItems[variant.id] = globalItem

            variant.currentStock?.forEach { entry: Map.Entry<String, Int> ->
                val whId = entry.key
                val qty = entry.value
                val whMap = warehouseItems.getOrPut(whId) { mutableMapOf() }
                whMap[variant.id] = globalItem.copy(currentStock = qty)
            }
        }

        // Save Global
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("summaries").document("inventory_global")
            .set(InventorySummary(id = "inventory_global", items = globalItems)).await()

        // Save Warehouses
        warehouseItems.forEach { entry: Map.Entry<String, MutableMap<String, InventorySummaryItem>> ->
            val whId = entry.key
            val items = entry.value
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("summaries").document("inventory_wh_$whId")
                .set(InventorySummary(id = "inventory_wh_$whId", warehouseId = whId, items = items)).await()
        }

        // 2. Rebuild Suppliers Overview
        val supplierItems = suppliers.associate { s ->
            s.id to SupplierSummaryItem(
                supplierId = s.id,
                name = s.name,
                currentBalance = s.currentBalance,
                totalDebit = s.totalDebit,
                totalCredit = s.totalCredit
            )
        }
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("summaries").document("suppliers_overview")
            .set(SuppliersOverview(suppliers = supplierItems)).await()
    }
}
