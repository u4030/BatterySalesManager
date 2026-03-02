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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.paging.map
import android.util.Log
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    val purchaseOrders: List<PurchaseOrderItem>
)

data class PurchaseOrderItem(
    val entry: StockEntry,
    val linkedPaidAmount: Double,
    val remainingBalance: Double,
    val referenceNumbers: List<String> = emptyList()
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val supplierRepository: SupplierRepository,
    private val billRepository: BillRepository,
    private val oldBatteryRepository: OldBatteryRepository,
    private val userRepository: UserRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _barcodeFilter = MutableStateFlow<String?>(null)
    val barcodeFilter = _barcodeFilter.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    val startDate = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Long?>(null)
    val endDate = _endDate.asStateFlow()

    private val _isSeller = MutableStateFlow(false)
    val isSeller = _isSeller.asStateFlow()

    private val _supplierSearchQuery = MutableStateFlow("")
    val supplierSearchQuery = _supplierSearchQuery.asStateFlow()

    private val _grandTotalInventoryQuantity = MutableStateFlow(0)
    val grandTotalInventoryQuantity = _grandTotalInventoryQuantity.asStateFlow()

    private val _supplierReport = MutableStateFlow<List<SupplierReportItem>>(emptyList())
    val supplierReport = _supplierReport.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _oldBatterySummary = MutableStateFlow<Pair<Int, Double>>(Pair(0, 0.0))
    val oldBatterySummary = _oldBatterySummary.asStateFlow()

    private val _oldBatteryWarehouseSummary = MutableStateFlow<Map<String, Pair<Int, Double>>>(emptyMap())
    val oldBatteryWarehouseSummary = _oldBatteryWarehouseSummary.asStateFlow()

    private val aggregationSemaphore = Semaphore(10)

    val warehouses: StateFlow<List<Warehouse>> = warehouseRepository.getWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredWarehouses: StateFlow<List<Warehouse>> = combine(
        warehouses,
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { allWh, seller, user ->
        val active = allWh.filter { it.isActive }
        if (seller) active.filter { it.id == user?.warehouseId } else active
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val inventoryReport: Flow<PagingData<InventoryReportItem>> = combine(
        barcodeFilter,
        filteredWarehouses
    ) { barcode, warehouseList ->
        Pair(barcode, warehouseList)
    }.flatMapLatest { (barcode, warehouseList) ->
        val productsMap = productRepository.getProductsOnce().associateBy { it.id }
        Pager(PagingConfig(pageSize = 20)) {
            InventoryPagingSource(firestore, stockEntryRepository, productsMap, warehouseList, barcode)
        }.flow.cachedIn(viewModelScope)
    }

    init {
        userRepository.getCurrentUserFlow()
            .onEach { user ->
                _isSeller.value = user?.role == "seller"
                refreshAll()
            }.launchIn(viewModelScope)

        _selectedTab.onEach { tab ->
            if (tab == 2 && _supplierReport.value.isEmpty()) {
                loadSupplierReport()
            }
        }.launchIn(viewModelScope)
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun refreshAll() {
        loadInventoryReport(reset = true)
        loadScrapReport()
        loadSupplierReport()
    }

    fun onBarcodeScanned(barcode: String?) {
        _barcodeFilter.value = barcode
        loadInventoryReport(reset = true)
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
        loadSupplierReport()
    }

    fun onSupplierSearchQueryChanged(query: String) {
        _supplierSearchQuery.value = query
        loadSupplierReport()
    }

    fun loadInventoryReport(reset: Boolean = false) {
        if (reset) {
            _grandTotalInventoryQuantity.value = 0

            // Calculate Global Grand Total once
            viewModelScope.launch {
                try {
                    val qtySnap = firestore.collection(StockEntry.COLLECTION_NAME)
                        .whereEqualTo("status", "approved")
                        .aggregate(
                            AggregateField.sum("quantity"),
                            AggregateField.sum("returnedQuantity")
                        ).get(AggregateSource.SERVER).await()

                    val totalQty = (qtySnap.getLong(AggregateField.sum("quantity")) ?: 0).toInt()
                    val totalRet = (qtySnap.getLong(AggregateField.sum("returnedQuantity")) ?: 0).toInt()
                    _grandTotalInventoryQuantity.value = totalQty - totalRet
                } catch (e: Exception) {
                    Log.e("ReportsViewModel", "Error calculating grand total", e)
                }
            }
        }
    }

    fun loadScrapReport() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            val seller = user?.role == "seller"
            val targetWarehouseId = if (seller) user?.warehouseId else null

            if (targetWarehouseId != null) {
                val summary = oldBatteryRepository.getStockSummary(targetWarehouseId)
                _oldBatterySummary.value = summary
                _oldBatteryWarehouseSummary.value = mapOf(targetWarehouseId to summary)
            } else {
                val globalSummary = oldBatteryRepository.getStockSummary(null)
                _oldBatterySummary.value = globalSummary

                val whSummaries = mutableMapOf<String, Pair<Int, Double>>()
                for (wh in warehouses.value.filter { it.isActive }) {
                    whSummaries[wh.id] = oldBatteryRepository.getStockSummary(wh.id)
                }
                _oldBatteryWarehouseSummary.value = whSummaries
            }
        }
    }

    private var supplierJob: kotlinx.coroutines.Job? = null
    fun loadSupplierReport() {
        supplierJob?.cancel()
        supplierJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                val query = _supplierSearchQuery.value
                val allSuppliers = supplierRepository.getSuppliersOnce()
                val suppliers = if (query.isBlank()) allSuppliers
                               else allSuppliers.filter { it.name.contains(query, ignoreCase = true) }

                val start = _startDate.value
                val end = _endDate.value

                // 1. Fetch all relevant entries and bills once to avoid multiple full collection scans
                val allEntries = stockEntryRepository.getAllStockEntries()
                val allBills = billRepository.getAllBills()

                val report = suppliers.map { supplier ->
                    async {
                        // Use server-side aggregation for totals to ensure absolute accuracy
                        val totalDebit = stockEntryRepository.getSupplierDebit(supplier.id, supplier.resetDate, start, end)
                        val totalCredit = billRepository.getSupplierCredit(supplier.id, supplier.resetDate, start, end)
                        val balance = totalDebit - totalCredit

                        // Filter pre-fetched logs for the specific supplier
                        val supplierEntries = allEntries.filter {
                            it.supplierId == supplier.id &&
                                    it.status == "approved" &&
                                    (supplier.resetDate == null || !it.timestamp.before(supplier.resetDate)) &&
                                    (start == null || it.timestamp.time >= start) &&
                                    (end == null || it.timestamp.time <= end)
                        }
                        val supplierBills = allBills.filter {
                            it.supplierId == supplier.id &&
                                    (supplier.resetDate == null || !it.createdAt.before(supplier.resetDate)) &&
                                    (start == null || it.dueDate.time >= start) &&
                                    (end == null || it.dueDate.time <= end)
                        }

                        val groupedEntries = supplierEntries.filter { it.quantity > 0 }.groupBy { it.orderId.ifEmpty { it.id } }
                        val purchaseOrders = groupedEntries.map { (key, group) ->
                            val representative = group.first()
                            val totalOrderCost = if (representative.grandTotalCost > 0) representative.grandTotalCost else group.sumOf { it.totalCost }

                            val linkedBills = supplierBills.filter {
                                it.relatedEntryId == key || group.any { entry -> entry.id == it.relatedEntryId }
                            }
                            val linkedPaid = linkedBills.sumOf { it.paidAmount }

                            PurchaseOrderItem(
                                entry = representative.copy(totalCost = totalOrderCost),
                                linkedPaidAmount = linkedPaid,
                                remainingBalance = totalOrderCost - linkedPaid,
                                referenceNumbers = linkedBills.filter {
                                    it.referenceNumber.isNotEmpty() && it.status == BillStatus.PAID
                                }.map { bill ->
                                    val typeStr = when (bill.billType) {
                                        BillType.CHECK -> "شيك"
                                        BillType.BILL -> "كمبيالة"
                                        BillType.TRANSFER -> "تحويل"
                                        BillType.OTHER -> "أخرى"
                                    }
                                    "$typeStr: ${bill.referenceNumber}"
                                }.distinct()
                            )
                        }.sortedByDescending { it.entry.timestamp }

                        val targetProgress = if (supplier.yearlyTarget > 0) totalDebit / supplier.yearlyTarget else 0.0

                        SupplierReportItem(
                            supplier = supplier,
                            totalDebit = totalDebit,
                            totalCredit = totalCredit,
                            balance = balance,
                            targetProgress = targetProgress,
                            purchaseOrders = purchaseOrders
                        )
                    }
                }.awaitAll()

                _supplierReport.value = report.filter { it.totalDebit > 0 || it.totalCredit > 0 || it.supplier.name.contains(query, ignoreCase = true) }
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error loading supplier report", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
