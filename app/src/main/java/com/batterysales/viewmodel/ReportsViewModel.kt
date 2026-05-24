package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject


@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val supplierRepository: SupplierRepository,
    private val summaryRepository: SummaryRepository,
    private val billRepository: BillRepository,
    private val oldBatteryRepository: OldBatteryRepository,
    private val userRepository: UserRepository,
    private val settingsManager: com.batterysales.utils.SettingsManager,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _isInventoryLoading = MutableStateFlow(false)
    val isInventoryLoading = _isInventoryLoading.asStateFlow()

    private val _isSupplierLoading = MutableStateFlow(false)
    val isSupplierLoading = _isSupplierLoading.asStateFlow()

    private val _isScrapLoading = MutableStateFlow(false)
    val isScrapLoading = _isScrapLoading.asStateFlow()

    val isLoading = combine(_isInventoryLoading, _isSupplierLoading, _isScrapLoading) { i, s, sc ->
        i || s || sc
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _barcodeFilter = MutableStateFlow<String?>(null)
    val barcodeFilter = _barcodeFilter.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    val startDate = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Long?>(null)
    val endDate = _endDate.asStateFlow()

    private val _inventoryStartDate = MutableStateFlow<Long?>(null)
    val inventoryStartDate = _inventoryStartDate.asStateFlow()

    private val _inventoryEndDate = MutableStateFlow<Long?>(null)
    val inventoryEndDate = _inventoryEndDate.asStateFlow()

    private val _isSeller = MutableStateFlow(false)
    val isSeller = _isSeller.asStateFlow()

    private val _supplierSearchQuery = MutableStateFlow("")
    val supplierSearchQuery = _supplierSearchQuery.asStateFlow()

    private val _selectedSupplierId = MutableStateFlow<String?>(null)
    val selectedSupplierId = _selectedSupplierId.asStateFlow()

    private val _selectedSupplierReport = MutableStateFlow<SupplierReportItem?>(null)
    val selectedSupplierReport = _selectedSupplierReport.asStateFlow()

    private val _suppliersOverviewList = MutableStateFlow<List<SupplierSummaryItem>>(emptyList())
    val suppliersOverviewList = _suppliersOverviewList.asStateFlow()

    // --- NUCLEAR STRATEGY: List instead of Pager ---
    private val _inventoryReportItems = MutableStateFlow<List<InventoryReportItem>>(emptyList())
    val inventoryReportItems = _inventoryReportItems.asStateFlow()

    private val _grandTotalInventoryQuantity = MutableStateFlow(0)
    val grandTotalInventoryQuantity = _grandTotalInventoryQuantity.asStateFlow()

    private val _grandTotalInventoryValue = MutableStateFlow(0.0)
    val grandTotalInventoryValue = _grandTotalInventoryValue.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _oldBatterySummary = MutableStateFlow<Pair<Int, Double>>(Pair(0, 0.0))
    val oldBatterySummary = _oldBatterySummary.asStateFlow()

    private val _scrapWarehouses = MutableStateFlow<List<ScrapWarehouse>>(emptyList())
    val scrapWarehouses = _scrapWarehouses.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)

    // For Sidebar Navigation
    private val _allInventoryItemNames = MutableStateFlow<List<String>>(emptyList())
    val allInventoryItemNames = _allInventoryItemNames.asStateFlow()

    val filteredWarehouses: Flow<List<Warehouse>> = flow {
        emit(warehouseRepository.getWarehousesOnce())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _isSeller.value = user?.role == com.batterysales.data.models.User.ROLE_SELLER
            
            // Sync Tab Indices: 0=Inventory, 1=Scrap, 2=Suppliers
            when(_selectedTab.value) {
                0 -> loadInventoryReport(reset = true)
                1 -> loadScrapReport()
                2 -> loadSuppliersOverview()
            }
        }

        _selectedTab.onEach { tab ->
            when(tab) {
                0 -> if (_inventoryReportItems.value.isEmpty()) loadInventoryReport(reset = true)
                1 -> if (_scrapWarehouses.value.isEmpty()) loadScrapReport()
                2 -> if (_suppliersOverviewList.value.isEmpty()) loadSuppliersOverview()
            }
        }.launchIn(viewModelScope)
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun refreshAll() {
        refreshTrigger.value += 1
        viewModelScope.launch {
            when(_selectedTab.value) {
                0 -> loadInventoryReport(reset = true)
                1 -> loadScrapReport()
                2 -> loadSuppliersOverview()
            }
        }
    }

    fun triggerAutoLink(supplierId: String) {
        viewModelScope.launch {
            billRepository.autoLinkBillsForSupplier(supplierId)
            loadDetailedSupplierReport(supplierId)
        }
    }

    fun onBarcodeScanned(barcode: String?) {
        _barcodeFilter.value = barcode
        loadInventoryReport(reset = false) // Just filter locally
    }

    fun onInventoryDateRangeSelected(start: Long?, end: Long?) {
        _inventoryStartDate.value = start
        _inventoryEndDate.value = end
        loadInventoryReport(reset = true) // Date filter needs a fresh scan or special logic
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
        _selectedSupplierId.value?.let { loadDetailedSupplierReport(it) }
    }

    fun onSupplierSearchQueryChanged(query: String) {
        _supplierSearchQuery.value = query
        loadSuppliersOverview()
    }

    fun syncSupplier(supplierId: String) {
        viewModelScope.launch {
            _isSupplierLoading.value = true
            try {
                billRepository.syncSupplierFinancials(supplierId)
                loadDetailedSupplierReport(supplierId)
            } finally {
                _isSupplierLoading.value = false
            }
        }
    }

    // --- NUCLEAR STRATEGY: Load ENTIRE inventory from ONE document with FALLBACK ---
    fun loadInventoryReport(reset: Boolean = false) {
        viewModelScope.launch {
            try {
                _isInventoryLoading.value = true
                val user = userRepository.getCurrentUser()
                val seller = user?.role == "seller"
                val whId = if (seller) user?.warehouseId else null

                val summary = summaryRepository.getInventorySummary(whId)
                
                val query = _barcodeFilter.value
                val items = if (summary != null) {
                    summary.items.values.asSequence()
                        .filter { if (query.isNullOrBlank()) true else it.productName.contains(query, ignoreCase = true) || it.barcode == query }
                        .filter { if (seller) it.currentStock > 0 else true }
                        .map { item ->
                            InventoryReportItem(
                                product = Product(id = item.productId, name = item.productName),
                                variant = ProductVariant(id = item.variantId, productId = item.productId, capacity = item.capacity, barcode = item.barcode, weightedAverageCost = item.weightedAverageCost, sellingPrice = item.sellingPrice, specification = item.specification),
                                warehouseQuantities = if (whId != null) mapOf(whId to item.currentStock) else emptyMap(),
                                totalQuantity = item.currentStock,
                                averageCost = item.weightedAverageCost,
                                totalCostValue = item.currentStock * item.weightedAverageCost
                            )
                        }.toList()
                } else {
                    // Fallback to heavy collection scan if summary is missing
                    val variants = productVariantRepository.getAllVariants()
                    variants.asSequence()
                        .filter { !it.archived }
                        .filter { if (query.isNullOrBlank()) true else (it.productName?.contains(query, ignoreCase = true) ?: false) || it.barcode == query }
                        .map { v ->
                            val qty = if (whId != null) v.currentStock?.get(whId) ?: 0 else v.currentStock?.values?.sum() ?: 0
                            InventoryReportItem(
                                product = Product(id = v.productId, name = v.productName ?: "Unknown"),
                                variant = v,
                                warehouseQuantities = if (whId != null) mapOf(whId to qty) else v.currentStock ?: emptyMap(),
                                totalQuantity = qty,
                                averageCost = v.weightedAverageCost,
                                totalCostValue = qty * v.weightedAverageCost
                            )
                        }
                        .filter { if (seller) it.totalQuantity > 0 else true }
                        .toList()
                }

                val finalItems = items.sortedWith(compareByDescending<InventoryReportItem> { it.product.name }.thenByDescending { it.variant.capacity })
                _inventoryReportItems.value = finalItems
                _grandTotalInventoryQuantity.value = finalItems.sumOf { it.totalQuantity }
                _grandTotalInventoryValue.value = finalItems.sumOf { it.totalCostValue }
                _allInventoryItemNames.value = finalItems.map { it.product.name }.distinct()

            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error loading nuclear inventory", e)
            } finally {
                _isInventoryLoading.value = false
            }
        }
    }

    fun loadSuppliersOverview() {
        viewModelScope.launch {
            try {
                _isSupplierLoading.value = true
                val query = _supplierSearchQuery.value
                val overview = summaryRepository.getSuppliersOverview()
                val suppliersMap = overview?.suppliers ?: emptyMap()
                
                var filtered = if (query.isNotBlank()) {
                    suppliersMap.values.filter { it.name.contains(query, ignoreCase = true) }
                } else {
                    suppliersMap.values.toList()
                }.sortedBy { it.name }

                if (filtered.isEmpty()) {
                    val fallback = supplierRepository.getSuppliersOnce(query)
                    _suppliersOverviewList.value = fallback.map { s ->
                        SupplierSummaryItem(s.id, s.name, s.currentBalance, s.totalDebit, s.totalCredit)
                    }
                } else {
                    _suppliersOverviewList.value = filtered
                }
            } finally {
                _isSupplierLoading.value = false
            }
        }
    }

    fun onSupplierSelected(supplierId: String?) {
        _selectedSupplierId.value = supplierId
        if (supplierId != null) {
            loadDetailedSupplierReport(supplierId)
        } else {
            _selectedSupplierReport.value = null
        }
    }

    private var supplierJob: kotlinx.coroutines.Job? = null
    private fun loadDetailedSupplierReport(supplierId: String) {
        supplierJob?.cancel()
        supplierJob = viewModelScope.launch {
            try {
                _isSupplierLoading.value = true
                
                val start = _startDate.value
                val end = _endDate.value
                
                // 1. Prioritize Cache for speed and lower quota usage
                if (start == null && end == null) {
                    val cachedReport = summaryRepository.getSupplierReportCache(supplierId)
                    val supplier = supplierRepository.getSupplier(supplierId)
                    if (cachedReport != null && supplier != null) {
                        _selectedSupplierReport.value = SupplierReportItem(
                            supplier = supplier,
                            totalDebit = cachedReport.totalDebit,
                            totalCredit = cachedReport.totalCredit,
                            balance = cachedReport.balance,
                            targetProgress = if (supplier.yearlyTarget > 0) cachedReport.totalDebit / supplier.yearlyTarget else 0.0,
                            regularOrders = cachedReport.regularOrders.map { it.toOrderItem() },
                            obligatedOrders = cachedReport.obligatedOrders.map { it.toOrderItem() }
                        )
                        _isSupplierLoading.value = false
                        
                        // Background incremental sync only if there is unallocated credit
                        if (supplier.unallocatedCredit > 0.001) {
                            viewModelScope.launch { 
                                try { billRepository.autoLinkBillsForSupplier(supplierId) } catch (e: Exception) {} 
                            }
                        }
                        return@launch
                    }
                }

                val supplier = supplierRepository.getSupplier(supplierId) ?: return@launch
                val adjustedStart = start?.let { com.batterysales.utils.DateUtils.getStartOfDay(it) }
                val adjustedEnd = end?.let { com.batterysales.utils.DateUtils.getEndOfDay(it) }

                // If no cache, perform incremental sync ONLY if pool exists
                if (supplier.unallocatedCredit > 0.001) {
                    try { billRepository.autoLinkBillsForSupplier(supplierId) } catch (e: Exception) {}
                }

                val allEntries = stockEntryRepository.getEntriesBySuppliers(listOf(supplierId), listOf(supplier.name))
                val allBills = billRepository.getBillsBySuppliers(listOf(supplierId))
                
                // Fetch specifications for ALL entries to ensure consistency (Legacy data support)
                val allVariantIds = allEntries.map { it.productVariantId }.distinct()
                val variantsCache = if (allVariantIds.isEmpty()) emptyMap() else {
                    productVariantRepository.getAllVariants().associateBy { it.id }
                }

                val supplierEntries: List<StockEntry> = allEntries.map { entry ->
                    val spec = if (entry.specification.isBlank()) {
                        variantsCache[entry.productVariantId]?.specification ?: ""
                    } else entry.specification
                    entry.copy(specification = spec)
                }.filter { entry ->
                    entry.status == "approved" &&
                            (adjustedStart == null || !entry.getEffectiveDate().before(Date(adjustedStart))) &&
                            (adjustedEnd == null || !entry.getEffectiveDate().after(Date(adjustedEnd))) &&
                            (supplier.resetDate == null || !entry.getEffectiveDate().before(supplier.resetDate))
                }

                val supplierBills: List<Bill> = allBills.filter { bill ->
                    (adjustedStart == null || !bill.createdAt.before(Date(adjustedStart))) &&
                            (adjustedEnd == null || !bill.createdAt.after(Date(adjustedEnd))) &&
                            (supplier.resetDate == null || !bill.createdAt.before(supplier.resetDate))
                }

                val groupedEntries: Map<String, List<StockEntry>> = supplierEntries
                    .groupBy { it.invoiceNumber.trim().ifEmpty { it.orderId.trim().ifEmpty { it.id } } }

                val purchaseOrders: List<PurchaseOrderItem> = groupedEntries.map { (_, group) ->
                    val sortedGroup = group.sortedBy { it.timestamp }
                    val representative = sortedGroup.first()
                    
                    // Correctly calculate total group cost and remaining balance from DB state
                    val totalOrderCost = group.sumOf { it.getNetCost() }
                    val effectiveBalance = group.sumOf { item ->
                        if (item.isSettled) 0.0 else (item.remainingBalance ?: item.getNetCost())
                    }

                    // Aggregate allocations across the whole order group for accurate coverage reporting
                    val combinedAllocations = mutableMapOf<String, Double>()
                    group.forEach { entry ->
                        entry.linkedAllocations.forEach { (id, amount) ->
                            combinedAllocations[id] = (combinedAllocations[id] ?: 0.0) + amount
                        }
                    }

                    var clearedAmount = 0.0
                    val coverageTypes = mutableSetOf<String>()
                    combinedAllocations.forEach { (id, amount) ->
                        if (amount <= 0.001) return@forEach
                        
                        val bill = allBills.find { it.id == id }
                        if (bill != null) {
                            if (bill.status == BillStatus.PAID || bill.billType == BillType.CASH || bill.billType == BillType.TRANSFER) {
                                clearedAmount += amount
                            }
                            coverageTypes.add(when(bill.billType) {
                                BillType.CHECK -> "شيك"
                                BillType.BILL -> "كمبيالة"
                                else -> "دفعة"
                            })
                        } else {
                            val ret = allEntries.find { it.id == id && it.totalCost < 0 }
                            if (ret != null) {
                                clearedAmount += amount
                                coverageTypes.add("مرتجع مواد")
                            }
                        }
                    }

                    val totalCovered = totalOrderCost - effectiveBalance
                    val coverageSummary = if (totalCovered > 0.001) {
                        if (effectiveBalance <= 0.001) {
                            if (clearedAmount >= totalOrderCost - 0.001) "مسددة بالكامل"
                            else "مغطاة بالكامل"
                        } else {
                            val amountStr = " (JD ${String.format("%.3f", totalCovered)})"
                            if (clearedAmount > 0.001) "مسددة جزئياً$amountStr" else "مغطاة جزئياً$amountStr"
                        }
                    } else "غير مغطاة"

                    val dbNotes = group.flatMap { it.settlementNotes }.distinct().filter { it.isNotBlank() }
                    val aggregatedNotes = if (dbNotes.isNotEmpty()) dbNotes else if (totalOrderCost > 0.001) {
                        listOf(coverageSummary)
                    } else emptyList()

                    PurchaseOrderItem(
                        entry = representative.copy(
                            totalCost = totalOrderCost,
                            remainingBalance = effectiveBalance,
                            isSettled = effectiveBalance <= 0.001
                        ),
                        linkedPaidAmount = clearedAmount,
                        autoLinkedAmount = 0.0,
                        remainingBalance = effectiveBalance,
                        items = sortedGroup.map { item ->
                            val itemSpec = if (item.specification.isBlank()) {
                                variantsCache[item.productVariantId]?.specification ?: ""
                            } else item.specification
                            item.copy(
                                productName = item.productName.trim().ifEmpty { representative.productName.trim().ifEmpty { "منتج غير معروف" } },
                                capacity = if (item.capacity == 0) representative.capacity else item.capacity,
                                specification = itemSpec
                            )
                        },
                        referenceNumbers = aggregatedNotes,
                        hasManualLink = aggregatedNotes.any { it.contains("ارتباط يدوي") },
                        totalActualPaid = clearedAmount,
                        totalLinkedAmount = totalCovered,
                        coverageSummary = coverageSummary,
                        isCleared = clearedAmount >= totalOrderCost - 0.001
                    )
                }

                val positiveOrders = purchaseOrders.filter { it.entry.totalCost > 0 }
                    .sortedWith(compareByDescending<PurchaseOrderItem> { it.entry.getEffectiveDate() }.thenByDescending { it.entry.timestamp })

                val totalDebit = if (start == null && end == null) supplier.totalDebit else positiveOrders.sumOf { it.entry.totalCost }
                val totalCredit = if (start == null && end == null) supplier.totalCredit else (
                    supplierBills.sumOf { b -> if (b.billType == BillType.CHECK || b.billType == BillType.BILL) b.amount else b.paidAmount }
                    + purchaseOrders.filter { it.entry.totalCost < 0 }.sumOf { -it.entry.totalCost }
                )
                val balance = if (start == null && end == null) supplier.currentBalance else (totalDebit - totalCredit)

                val finalOrdersForDisplay = positiveOrders

                val partitionedResult = finalOrdersForDisplay.partition { po ->
                    po.totalLinkedAmount >= po.entry.totalCost - 0.001
                }

                val finalReportItem = SupplierReportItem(
                    supplier = supplier,
                    totalDebit = totalDebit,
                    totalCredit = totalCredit,
                    balance = balance,
                    targetProgress = if (supplier.yearlyTarget > 0) totalDebit / supplier.yearlyTarget else 0.0,
                    regularOrders = partitionedResult.second,
                    obligatedOrders = partitionedResult.first
                )

                if (start == null && end == null) {
                    firestore.runTransaction { transaction ->
                        summaryRepository.updateSupplierReportCache(transaction, supplier.id, finalReportItem)
                    }.await()
                }

                _selectedSupplierReport.value = finalReportItem
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error loading supplier report", e)
            } finally {
                _isSupplierLoading.value = false
            }
        }
    }

    fun loadScrapReport() {
        viewModelScope.launch {
            _isScrapLoading.value = true
            try {
                val summary = oldBatteryRepository.getStockSummary()
                _oldBatterySummary.value = summary

                val user = userRepository.getCurrentUser()
                val seller = user?.role == "seller"

                val scrapWhRef = firestore.collection(ScrapWarehouse.COLLECTION_NAME)
                val snapshot = scrapWhRef.get().await()
                val allScrapWh = snapshot.documents.mapNotNull { it.toObject(ScrapWarehouse::class.java)?.copy(id = it.id) }
                    .filter { it.isActive }

                if (seller) {
                    val myScrapWh = allScrapWh.find { it.parentWarehouseId == user?.warehouseId }
                    if (myScrapWh != null) {
                        _scrapWarehouses.value = listOf(myScrapWh)
                    } else {
                        _scrapWarehouses.value = emptyList()
                    }
                } else {
                    _scrapWarehouses.value = allScrapWh.sortedBy { it.name }
                }
            } finally {
                _isScrapLoading.value = false
            }
        }
    }
}

private fun Map<String, Any>.toOrderItem(): PurchaseOrderItem {
    val timestamp = when (val t = this["timestamp"]) {
        is com.google.firebase.Timestamp -> t.toDate()
        is Date -> t
        else -> Date()
    }
    val invoiceDate = when (val d = this["invoiceDate"]) {
        is com.google.firebase.Timestamp -> d.toDate()
        is Date -> d
        else -> null
    }

    val itemsRaw = this["items"] as? List<Map<String, Any>> ?: emptyList()
    val orderItems = itemsRaw.map { itemMap ->
        var itemSpec = itemMap["specification"] as? String ?: ""
        // Fallback for cache generated without specifications
        if (itemSpec.isBlank()) {
            val name = itemMap["productName"] as? String ?: ""
            if (name.contains("(")) {
                itemSpec = name.substringAfter("(").substringBefore(")")
            }
        }
        StockEntry(
            id = itemMap["id"] as? String ?: "",
            productVariantId = itemMap["productVariantId"] as? String ?: "",
            productName = itemMap["productName"] as? String ?: "",
            capacity = (itemMap["capacity"] as? Number)?.toInt() ?: 0,
            specification = itemSpec,
            quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 0,
            totalCost = (itemMap["totalCost"] as? Number)?.toDouble() ?: 0.0,
            linkedAllocations = (itemMap["linkedAllocations"] as? Map<String, Double>) ?: emptyMap()
        )
    }

    val totalCost = (this["totalCost"] as? Number)?.toDouble() ?: 0.0
    val remainingBalance = (this["remainingBalance"] as? Number)?.toDouble() ?: 0.0
    val totalActualPaid = (this["totalActualPaid"] as? Number)?.toDouble() ?: 0.0
    val isCleared = (this["isCleared"] as? Boolean) ?: (totalActualPaid >= totalCost - 0.001)
    
    // Recalculate label even from cache to ensure no old technical strings are shown
    val totalCovered = totalCost - remainingBalance
    val label = if (totalCovered > 0.001) {
        if (remainingBalance <= 0.001) {
            if (isCleared) "مسددة بالكامل" else "مغطاة بالكامل"
        } else {
            if (totalActualPaid > 0.001) "مسددة جزئياً" else "مغطاة جزئياً"
        }
    } else "غير مغطاة"

    val dbNotes = (this["settlementNotes"] as? List<String>) ?: emptyList()
    val finalNotes = if (dbNotes.isNotEmpty()) dbNotes else listOf(label)

    return PurchaseOrderItem(
        entry = StockEntry(
            id = this["id"] as? String ?: "",
            totalCost = totalCost,
            invoiceNumber = this["invoiceNumber"] as? String ?: "",
            timestamp = timestamp,
            invoiceDate = invoiceDate,
            specification = this["specification"] as? String ?: "",
            settlementNotes = finalNotes
        ),
        linkedPaidAmount = totalActualPaid,
        remainingBalance = remainingBalance,
        referenceNumbers = finalNotes,
        items = orderItems,
        totalLinkedAmount = totalCovered,
        totalActualPaid = totalActualPaid,
        coverageSummary = label,
        isCleared = isCleared
    )
}
