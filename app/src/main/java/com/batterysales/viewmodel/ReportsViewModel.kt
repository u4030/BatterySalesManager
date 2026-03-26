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
import android.content.Context
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

    private val _grandTotalInventoryValue = MutableStateFlow(0.0)
    val grandTotalInventoryValue = _grandTotalInventoryValue.asStateFlow()

    private val _supplierReport = MutableStateFlow<List<SupplierReportItem>>(emptyList())
    val supplierReport = _supplierReport.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _oldBatterySummary = MutableStateFlow<Pair<Int, Double>>(Pair(0, 0.0))
    val oldBatterySummary = _oldBatterySummary.asStateFlow()

    private val _oldBatteryWarehouseSummary = MutableStateFlow<Map<String, Pair<Int, Double>>>(emptyMap())
    val oldBatteryWarehouseSummary = _oldBatteryWarehouseSummary.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)

    val warehouses: StateFlow<List<Warehouse>> = warehouseRepository.getWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredWarehouses: StateFlow<List<Warehouse>> = combine(
        warehouses,
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { allWh, seller, user ->
        val active = allWh.filter { it.isActive }
        val result = if (seller) active.filter { it.id == user?.warehouseId } else active
        result.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productsMap: StateFlow<Map<String, Product>> = productRepository.getProducts()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val inventoryReport: Flow<PagingData<InventoryReportItem>> = combine(
        barcodeFilter,
        filteredWarehouses,
        productsMap,
        _isSeller,
        refreshTrigger
    ) { barcode, warehouseList, pMap, seller, _ ->
        Pair(barcode, warehouseList) to (pMap to seller)
    }.flatMapLatest { (pair1, pair2) ->
        val (barcode, warehouseList) = pair1
        val (pMap, seller) = pair2
        Pager(PagingConfig(pageSize = 25)) {
            InventoryPagingSource(firestore, stockEntryRepository, pMap, warehouseList, barcode, seller)
        }.flow.cachedIn(viewModelScope)
    }

    init {
        userRepository.getCurrentUserFlow()
            .onEach { user ->
                _isSeller.value = user?.role == com.batterysales.data.models.User.ROLE_SELLER
                refreshAll()
            }.launchIn(viewModelScope)
        
        // Reactively refresh inventory when stock changes (Lightweight listener)
        firestore.collection(StockEntry.COLLECTION_NAME)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                if (e == null && snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    Log.d("ReportsViewModel", "Stock changed detected via lightweight listener, refreshing")
                    refreshTrigger.value += 1
                }
            }

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
        refreshTrigger.value += 1
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
            _grandTotalInventoryValue.value = 0.0
            
            // Calculate Global Grand Total once
            viewModelScope.launch {
                try {
                    val user = userRepository.getCurrentUser()
                    val seller = user?.role == "seller"
                    val targetWarehouseId = if (seller) user?.warehouseId else null

                    var baseQuery = firestore.collection(StockEntry.COLLECTION_NAME)
                        .whereEqualTo("status", "approved")
                    
                    if (targetWarehouseId != null) {
                        baseQuery = baseQuery.whereEqualTo("warehouseId", targetWarehouseId)
                    }

                    val qtySnap = baseQuery.aggregate(
                            AggregateField.sum("quantity"),
                            AggregateField.sum("returnedQuantity")
                        ).get(AggregateSource.SERVER).await()
                    
                    val totalQty = (qtySnap.getLong(AggregateField.sum("quantity")) ?: 0).toInt()
                    val totalRet = (qtySnap.getLong(AggregateField.sum("returnedQuantity")) ?: 0).toInt()
                    _grandTotalInventoryQuantity.value = totalQty - totalRet

                    // Note: Value is harder to aggregate server-side due to weighted average.
                    // But we can approximate or let the paging source handle individual sums for the current view.
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

                val activeWarehouses = warehouses.value.filter { it.isActive }.sortedBy { it.name }
                val whSummaries = mutableMapOf<String, Pair<Int, Double>>()
                for (wh in activeWarehouses) {
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

                // 1. Fetch all relevant entries and bills once in parallel
                val allEntriesJob = async { stockEntryRepository.getAllStockEntries() }
                val allBillsJob = async { billRepository.getAllBills() }
                
                val allEntries = allEntriesJob.await()
                val allBills = allBillsJob.await()

                val report = suppliers.map { supplier ->
                    async {
                        val adjustedEnd = end?.let { it + 86400000 }
                        
                        // Filter entries for this supplier
                        val supplierEntries = allEntries.filter { entry ->
                            val matchId = entry.supplierId.isNotEmpty() && entry.supplierId == supplier.id
                            // Robust name matching fallback for legacy entries
                            val matchName = entry.supplier.isNotBlank() && 
                                           (entry.supplier.trim().equals(supplier.name.trim(), ignoreCase = true) ||
                                            entry.supplier.trim().contains(supplier.name.trim(), ignoreCase = true))
                            
                            (matchId || matchName) &&
                                    entry.status == "approved" &&
                                    (supplier.resetDate == null || !entry.timestamp.before(supplier.resetDate)) &&
                                    (start == null || entry.timestamp.time >= start) &&
                                    (adjustedEnd == null || entry.timestamp.time <= adjustedEnd)
                        }

                        // Filter bills for this supplier
                        val supplierBills = allBills.filter { bill ->
                            bill.supplierId == supplier.id &&
                                    (supplier.resetDate == null || !bill.createdAt.before(supplier.resetDate)) &&
                                    (start == null || bill.dueDate.time >= start) &&
                                    (adjustedEnd == null || bill.dueDate.time <= adjustedEnd)
                        }

                        // Calculate totals locally for accuracy and consistency
                        val totalDebit = supplierEntries.sumOf { it.totalCost }
                        val totalCredit = supplierBills.sumOf { it.paidAmount }
                        val balance = totalDebit - totalCredit

                        // Group entries into Purchase Orders
                        val groupedEntries = supplierEntries.filter { it.quantity > 0 }
                            .groupBy { it.orderId.ifEmpty { it.id } }
                        
                        val purchaseOrders = groupedEntries.map { (key, group) ->
                            val representative = group.first()
                            val totalOrderCost = if (representative.grandTotalCost > 0) representative.grandTotalCost else group.sumOf { it.totalCost }

                            val linkedBills = supplierBills.filter { bill ->
                                bill.relatedEntryId == key || group.any { entry -> entry.id == bill.relatedEntryId }
                            }
                            val linkedPaid = linkedBills.sumOf { it.paidAmount }

                            PurchaseOrderItem(
                                entry = representative.copy(totalCost = totalOrderCost),
                                linkedPaidAmount = linkedPaid,
                                remainingBalance = totalOrderCost - linkedPaid,
                                referenceNumbers = linkedBills.filter { bill ->
                                    bill.referenceNumber.isNotEmpty() && bill.status == BillStatus.PAID
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
                
                _supplierReport.value = report
                    .filter { it.totalDebit > 0 || it.totalCredit > 0 || query.isNotBlank() }
                    .sortedBy { it.supplier.name }
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error loading supplier report", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
