package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import com.batterysales.data.paging.InventoryPagingSource
import com.batterysales.utils.Sextuple
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

data class InventoryReportItem(
    val product: Product,
    val variant: ProductVariant,
    val warehouseQuantities: Map<String, Int>, // WarehouseID to Quantity
    val totalQuantity: Int,
    val averageCost: Double,
    val totalCostValue: Double
)

data class SupplierReportItem(
    val supplier: Supplier,
    val totalDebit: Double, // Purchases
    val totalCredit: Double, // Payments
    val balance: Double, // Debit - Credit
    val targetProgress: Double,
    val regularOrders: List<PurchaseOrderItem>, // Orders NOT linked to checks/bills
    val obligatedOrders: List<PurchaseOrderItem> // Orders linked to checks/bills
)

data class PurchaseOrderItem(
    val entry: StockEntry,
    val linkedPaidAmount: Double,
    val remainingBalance: Double,
    val referenceNumbers: List<String> = emptyList(),
    val items: List<StockEntry> = emptyList(),
    val autoLinkedAmount: Double = 0.0,
    val hasManualLink: Boolean = false,
    val totalActualPaid: Double = 0.0,
    val totalLinkedAmount: Double = 0.0
)

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

    // Aggregate loading state
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

    private val _grandTotalInventoryQuantity = MutableStateFlow(0)
    val grandTotalInventoryQuantity = _grandTotalInventoryQuantity.asStateFlow()

    private val _grandTotalInventoryValue = MutableStateFlow(0.0)
    val grandTotalInventoryValue = _grandTotalInventoryValue.asStateFlow()

    private val _supplierReport = MutableStateFlow<List<SupplierReportItem>>(emptyList())
    val supplierReport = _supplierReport.asStateFlow()

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

    private fun updateSidebarNames() {
        if (_selectedTab.value != 0) return
        viewModelScope.launch {
            try {
                val pMap = _productsMap.value
                val query = _barcodeFilter.value

                val names = pMap.values.asSequence()
                    .filter { !it.archived }
                    .filter { p ->
                        if (query.isNullOrBlank()) true
                        else p.name.contains(query, ignoreCase = true)
                    }
                    .map { it.name }
                    .distinct()
                    .sorted()
                    .toList()
                
                _allInventoryItemNames.value = names
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error updating sidebar", e)
            }
        }
    }

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val filteredWarehouses: StateFlow<List<Warehouse>> = combine(
        _warehouses,
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { allWh, seller, user ->
        val active = allWh.filter { it.isActive }
        val result = if (seller) active.filter { it.id == user?.warehouseId } else active
        result.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _productsMap = MutableStateFlow<Map<String, Product>>(emptyMap())
    val productsMap = _productsMap.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    val inventoryReport: Flow<PagingData<InventoryReportItem>> = combine(
        barcodeFilter,
        filteredWarehouses,
        _productsMap,
        _isSeller,
        _inventoryStartDate,
        _inventoryEndDate,
        refreshTrigger,
        selectedTab
    ) { args ->
        val tab = args[7] as Int
        if (tab != 0) return@combine null
        Sextuple(
            args[0] as String?,
            args[1] as List<Warehouse>,
            args[2] as Map<String, Product>,
            args[3] as Boolean,
            args[4] as Long?,
            args[5] as Long?
        )
    }.filterNotNull().flowOn(kotlinx.coroutines.Dispatchers.Default).flatMapLatest { (query, warehouseList, pMap, seller, start, end) ->
        Pager(PagingConfig(pageSize = 25)) {
            InventoryPagingSource(firestore, stockEntryRepository, pMap, warehouseList, query, seller, start, end)
        }.flow.cachedIn(viewModelScope)
    }

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _isSeller.value = user?.role == com.batterysales.data.models.User.ROLE_SELLER

            loadReferenceData()

            // TAB SYNC: 0=Inventory, 1=Scrap, 2=Suppliers
            when(_selectedTab.value) {
                0 -> { loadInventoryReport(reset = true); updateSidebarNames() }
                1 -> loadScrapReport()
                2 -> loadSuppliersOverview()
            }
        }

        _selectedTab.onEach { tab ->
            when(tab) {
                0 -> if (_allInventoryItemNames.value.isEmpty()) { loadInventoryReport(reset = true); updateSidebarNames() }
                1 -> if (_scrapWarehouses.value.isEmpty()) loadScrapReport()
                2 -> if (_suppliersOverviewList.value.isEmpty()) loadSuppliersOverview()
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun loadReferenceData() {
        try {
            val wh = warehouseRepository.getWarehousesOnce()
            val prod = productRepository.getProductsOnce().associateBy { it.id }
            _warehouses.value = wh
            _productsMap.value = prod
        } catch (e: Exception) {
            Log.e("ReportsViewModel", "Error loading reference data", e)
        }
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
        if (index == 0 && _allInventoryItemNames.value.isEmpty()) updateSidebarNames()
    }

    fun refreshAll() {
        refreshTrigger.value += 1
        viewModelScope.launch {
            loadReferenceData()
            when(_selectedTab.value) {
                0 -> { loadInventoryReport(reset = true); updateSidebarNames() }
                1 -> loadScrapReport()
                2 -> loadSuppliersOverview()
            }
        }
    }

    fun onBarcodeScanned(barcode: String?) {
        _barcodeFilter.value = barcode
        loadInventoryReport(reset = true)
    }

    fun onInventoryDateRangeSelected(start: Long?, end: Long?) {
        _inventoryStartDate.value = start
        _inventoryEndDate.value = end
        loadInventoryReport(reset = true)
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

    fun loadSuppliersOverview() {
        viewModelScope.launch {
            try {
                _isSupplierLoading.value = true
                val query = _supplierSearchQuery.value

                // Try summary first
                val overview = summaryRepository.getSuppliersOverview()
                val suppliersMap = overview?.suppliers ?: emptyMap()

                var filtered = if (query.isNotBlank()) {
                    suppliersMap.values.filter { it.name.contains(query, ignoreCase = true) }
                } else {
                    suppliersMap.values.toList()
                }.sortedBy { it.name }

                // --- FALLBACK Logic ---
                // If summary is empty OR search query returned nothing from summary, try cloud fallback
                if (filtered.isEmpty()) {
                    val fallback = if (query.isNotBlank()) {
                        supplierRepository.getSuppliersOnce(query)
                    } else {
                        supplierRepository.getSuppliersOnce()
                    }

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
                val supplier = supplierRepository.getSupplier(supplierId) ?: return@launch

                if (start == null && end == null) {
                    val cache = summaryRepository.getSupplierReportCache(supplierId)
                    if (cache != null) {
                        _selectedSupplierReport.value = SupplierReportItem(
                            supplier = supplier,
                            totalDebit = cache.totalDebit,
                            totalCredit = cache.totalCredit,
                            balance = cache.balance,
                            targetProgress = if (supplier.yearlyTarget > 0) cache.totalDebit / supplier.yearlyTarget else 0.0,
                            regularOrders = cache.regularOrders.map { it.toOrderItem() },
                            obligatedOrders = cache.obligatedOrders.map { it.toOrderItem() }
                        )
                        _isSupplierLoading.value = false
                        return@launch
                    }
                }

                val allEntries = stockEntryRepository.getEntriesBySuppliers(listOf(supplierId))
                val allBills = billRepository.getBillsBySuppliers(listOf(supplierId))

                val supplierEntries: List<StockEntry> = allEntries.filter { entry ->
                    entry.status == "approved" &&
                            (supplier.resetDate == null || !entry.getEffectiveDate().before(supplier.resetDate))
                }

                val supplierBills: List<Bill> = allBills.filter { bill ->
                    (supplier.resetDate == null || !bill.createdAt.before(supplier.resetDate))
                }

                val groupedEntries: Map<String, List<StockEntry>> = supplierEntries
                    .groupBy { it.invoiceNumber.trim().ifEmpty { it.orderId.trim().ifEmpty { it.id } } }

                val purchaseOrders: List<PurchaseOrderItem> = groupedEntries.map { (key, group) ->
                    val representative = group.first()
                    val totalOrderCost = group.sumOf { it.getNetCost() }
                    val effectiveBalance = if (representative.isSettled) 0.0 else (representative.remainingBalance ?: 0.0)

                    val manualLinkedBills = supplierBills.filter { bill ->
                        val ref = bill.referenceNumber.trim()
                        bill.relatedEntryId == key || ref == key || (ref.isNotEmpty() && ref == representative.invoiceNumber.trim())
                    }.distinctBy { it.id }

                    PurchaseOrderItem(
                        entry = representative.copy(totalCost = totalOrderCost),
                        linkedPaidAmount = manualLinkedBills.sumOf { it.paidAmount },
                        remainingBalance = effectiveBalance,
                        items = group,
                        hasManualLink = manualLinkedBills.isNotEmpty(),
                        totalActualPaid = totalOrderCost - effectiveBalance,
                        totalLinkedAmount = maxOf(manualLinkedBills.sumOf { it.amount }, totalOrderCost - effectiveBalance)
                    )
                }

                val positiveOrders = purchaseOrders.filter { it.entry.totalCost > 0 }
                    .sortedWith(compareBy<PurchaseOrderItem> { it.entry.getEffectiveDate() }.thenBy { it.entry.timestamp })

                val totalDebit = if (start == null && end == null) supplier.totalDebit else positiveOrders.sumOf { it.entry.totalCost }
                val totalCredit = if (start == null && end == null) supplier.totalCredit else (supplierBills.sumOf { it.paidAmount } + purchaseOrders.filter { it.entry.totalCost < 0 }.sumOf { -it.entry.totalCost })
                val balance = if (start == null && end == null) supplier.currentBalance else (totalDebit - totalCredit)

                val adjustedStart = start?.let { com.batterysales.utils.DateUtils.getStartOfDay(it) }
                val adjustedEnd = end?.let { com.batterysales.utils.DateUtils.getEndOfDay(it) }

                val finalOrdersForDisplay = positiveOrders.filter { po ->
                    (adjustedStart == null || po.entry.getEffectiveDate().time >= adjustedStart) &&
                            (adjustedEnd == null || po.entry.getEffectiveDate().time <= adjustedEnd)
                }

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

    fun loadInventoryReport(reset: Boolean = false) {
        if (reset) {
            _grandTotalInventoryQuantity.value = 0
            _grandTotalInventoryValue.value = 0.0

            viewModelScope.launch {
                _isInventoryLoading.value = true
                try {
                    val user = userRepository.getCurrentUser()
                    val seller = user?.role == "seller"
                    val targetWarehouseId = if (seller) user?.warehouseId else null

                    if (targetWarehouseId == null) {
                        val statsSnap = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID).get().await()
                        val stats = statsSnap.toObject(SystemStats::class.java)
                        if (stats != null) {
                            _grandTotalInventoryQuantity.value = stats.totalInventoryQuantity
                            _grandTotalInventoryValue.value = stats.totalInventoryValue
                        }
                    } else {
                        var baseQuery = firestore.collection(StockEntry.COLLECTION_NAME)
                            .whereEqualTo("status", "approved")
                            .whereEqualTo("warehouseId", targetWarehouseId)

                        val qtySnap = baseQuery.aggregate(
                            AggregateField.sum("quantity"),
                            AggregateField.sum("returnedQuantity")
                        ).get(AggregateSource.SERVER).await()

                        val totalQty = (qtySnap.getLong(AggregateField.sum("quantity")) ?: 0).toInt()
                        val totalRet = (qtySnap.getLong(AggregateField.sum("returnedQuantity")) ?: 0).toInt()
                        _grandTotalInventoryQuantity.value = totalQty - totalRet
                    }
                } catch (e: Exception) {
                    Log.e("ReportsViewModel", "Error calculating grand total", e)
                } finally {
                    _isInventoryLoading.value = false
                }
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
    return PurchaseOrderItem(
        entry = StockEntry(
            id = this["id"] as? String ?: "",
            totalCost = (this["totalCost"] as? Number)?.toDouble() ?: 0.0,
            invoiceNumber = this["invoiceNumber"] as? String ?: "",
            timestamp = this["timestamp"] as? Date ?: Date()
        ),
        linkedPaidAmount = 0.0,
        remainingBalance = (this["remainingBalance"] as? Number)?.toDouble() ?: 0.0,
        referenceNumbers = (this["referenceNumbers"] as? List<String>) ?: emptyList()
    )
}
