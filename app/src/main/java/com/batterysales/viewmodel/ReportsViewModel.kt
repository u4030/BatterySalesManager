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

                val report = activeVariants.mapNotNull { variant ->
                    val product = activeProducts[variant.productId] ?: return@mapNotNull null

                    val warehouseQuantities = mutableMapOf<String, Int>()
                    var totalQuantity = 0
                    for (wh in warehouseList) {
                        val qty = variant.stockLevels[wh.id] ?: 0
                        warehouseQuantities[wh.id] = qty
                        totalQuantity += qty
                    }

                    if (totalQuantity <= 0 && barcode == null) return@mapNotNull null

                    // For Average Cost, we still need to calculate it or store it.
                    // Currently, we'll keep the summary call for average cost ONLY if needed,
                    // but wait, the user wants performance.
                    // Let's use the summary call ONLY ONCE per variant to get global average cost.
                    // Or even better, store averageCost in ProductVariant.

                    val globalSummary = stockEntryRepository.getVariantSummary(variant.id, null)
                    val averageCost = globalSummary.second

                    InventoryReportItem(
                        product = product,
                        variant = variant,
                        warehouseQuantities = warehouseQuantities,
                        totalQuantity = totalQuantity,
                        averageCost = averageCost,
                        totalCostValue = totalQuantity * averageCost
                    )
                }

                _inventoryReport.value = report
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

                // If date range is selected, we still need to fetch entries for that range.
                // But for the general summary, we use the stored balances.
                val isDateFiltered = start != null || end != null

                // For detailed purchase orders, we'll still need entries.
                // However, we can fetch only the ones for the specific suppliers or just the recent ones.
                val allStockEntries = if (isDateFiltered) stockEntryRepository.getAllStockEntries() else emptyList()
                val allBills = if (isDateFiltered) billRepository.getAllBills() else emptyList()

                val report = suppliers.map { supplier ->
                    val totalDebit: Double
                    val totalCredit: Double

                    if (isDateFiltered) {
                        val supplierEntries = allStockEntries.filter {
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
                        totalDebit = supplierEntries.sumOf { it.totalCost }
                        totalCredit = supplierBills.sumOf { it.paidAmount }
                    } else {
                        totalDebit = supplier.totalDebit
                        totalCredit = supplier.totalCredit
                    }

                    val balance = totalDebit - totalCredit

                    // We still need detailed purchase orders for the UI expansion
                    // To optimize, we could fetch these on-demand or only for the current year.
                    // For now, let's at least optimize the summary.

                    val supplierEntriesForOrders = if (isDateFiltered) {
                        allStockEntries.filter { it.supplierId == supplier.id && it.status == "approved" && (start == null || it.timestamp.time >= start) && (end == null || it.timestamp.time <= end) }
                    } else {
                         stockEntryRepository.getAllStockEntries().filter { it.supplierId == supplier.id && it.status == "approved" }
                    }
                    val supplierBillsForOrders = if (isDateFiltered) {
                        allBills.filter { it.supplierId == supplier.id && (start == null || it.dueDate.time >= start) && (end == null || it.dueDate.time <= end) }
                    } else {
                         billRepository.getAllBills().filter { it.supplierId == supplier.id }
                    }

                    val groupedEntries = supplierEntriesForOrders.filter { it.quantity > 0 }.groupBy { it.orderId.ifEmpty { it.id } }
                    val purchaseOrders = groupedEntries.map { (key, group) ->
                        val representative = group.first()
                        val totalOrderCost = if (representative.grandTotalCost > 0) representative.grandTotalCost else group.sumOf { it.totalCost }

                        val linkedBills = supplierBillsForOrders.filter {
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
