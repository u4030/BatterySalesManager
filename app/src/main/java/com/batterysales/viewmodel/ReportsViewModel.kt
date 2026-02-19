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
    private val userRepository: UserRepository
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

    private val _inventoryReport = MutableStateFlow<List<InventoryReportItem>>(emptyList())
    val inventoryReport = _inventoryReport.asStateFlow()

    private val _supplierReport = MutableStateFlow<List<SupplierReportItem>>(emptyList())
    val supplierReport = _supplierReport.asStateFlow()

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

    init {
        userRepository.getCurrentUserFlow()
            .onEach { user ->
                _isSeller.value = user?.role == "seller"
                refreshAll()
            }.launchIn(viewModelScope)
    }

    fun refreshAll() {
        loadInventoryReport()
        loadScrapReport()
        loadSupplierReport()
    }

    fun onBarcodeScanned(barcode: String?) {
        _barcodeFilter.value = barcode
        loadInventoryReport()
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
        loadSupplierReport()
    }

    fun onSupplierSearchQueryChanged(query: String) {
        _supplierSearchQuery.value = query
        // The supplier report is filtered locally currently for responsiveness, 
        // but we could also reload if query is specific.
    }

    fun loadInventoryReport() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Ensure filteredWarehouses has data
                if (filteredWarehouses.value.isEmpty()) {
                    filteredWarehouses.filter { it.isNotEmpty() }.first()
                }

                val products = productRepository.getProducts().first()
                val allVariants = productVariantRepository.getAllVariantsFlow().first()
                val warehouseList = filteredWarehouses.value
                val barcode = _barcodeFilter.value

                val activeProducts = products.filter { !it.archived }.associateBy { it.id }
                val activeVariants = allVariants.filter { !it.archived && (barcode == null || it.barcode == barcode) }

                val jobs = activeVariants.map { variant ->
                    async {
                        aggregationSemaphore.withPermit {
                            val product = activeProducts[variant.productId] ?: return@withPermit null

                            // Get global summary first
                            val globalSummary = stockEntryRepository.getVariantSummary(variant.id, null)
                            if (globalSummary.first <= 0 && barcode == null) return@withPermit null

                            val warehouseQuantities = mutableMapOf<String, Int>()
                            for (wh in warehouseList) {
                                val whSummary = stockEntryRepository.getVariantSummary(variant.id, wh.id)
                                warehouseQuantities[wh.id] = whSummary.first
                            }

                            val averageCost = globalSummary.second // We'll use the aggregated totalCost / (qty-returned)

                            InventoryReportItem(
                                product = product,
                                variant = variant,
                                warehouseQuantities = warehouseQuantities,
                                totalQuantity = globalSummary.first,
                                averageCost = averageCost,
                                totalCostValue = globalSummary.first * averageCost
                            )
                        }
                    }
                }

                _inventoryReport.value = jobs.awaitAll().filterNotNull()
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error loading inventory report", e)
            } finally {
                _isLoading.value = false
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

    fun loadSupplierReport() {
        viewModelScope.launch {
            try {
                val suppliers = supplierRepository.getSuppliers().first()
                val start = _startDate.value
                val end = _endDate.value

                // Use suspend fetch instead of .first() on flow to ensure we wait for fresh data
                val allStockEntries = stockEntryRepository.getAllStockEntries()
                val allBills = billRepository.getAllBills()

                val report = suppliers.map { supplier ->
                    val supplierEntries = allStockEntries.filter {
                        (it.supplierId == supplier.id || (it.supplierId.isBlank() && it.supplier == supplier.name)) &&
                                it.status == "approved" &&
                                (supplier.resetDate == null || !it.timestamp.before(supplier.resetDate)) &&
                                (start == null || it.timestamp.time >= start) &&
                                (end == null || it.timestamp.time <= end)
                    }
                    val supplierBills = allBills.filter {
                        (it.supplierId == supplier.id || it.supplierId.isBlank()) && // Fallback for bills is risky if same name, but usually unique enough
                                (supplier.resetDate == null || !it.createdAt.before(supplier.resetDate)) &&
                                (start == null || it.dueDate.time >= start) &&
                                (end == null || it.dueDate.time <= end)
                    }

                    val totalDebit = supplierEntries.sumOf { it.totalCost }
                    val totalCredit = supplierBills.sumOf { it.paidAmount }
                    val balance = totalDebit - totalCredit

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
                    }

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
                _supplierReport.value = report
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error loading supplier report", e)
            }
        }
    }
}
