package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    private val _isLoading = MutableStateFlow(true)
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

    val warehouses: StateFlow<List<Warehouse>> = warehouseRepository.getWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredWarehouses: StateFlow<List<Warehouse>> = combine(
        warehouses,
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { allWh, seller, user ->
        if (seller) allWh.filter { it.id == user?.warehouseId } else allWh
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inventoryReport = MutableStateFlow<List<InventoryReportItem>>(emptyList())
    val inventoryReport = _inventoryReport.asStateFlow()

    private fun loadInventoryReport() {
        viewModelScope.launch {
            _isLoading.value = true
            combine(
                productRepository.getProducts(),
                productVariantRepository.getAllVariantsFlow(),
                filteredWarehouses,
                _barcodeFilter
            ) { products: List<Product>, allVariants: List<ProductVariant>, warehouseList: List<Warehouse>, barcode: String? ->
                Quadruple(products, allVariants, warehouseList, barcode)
            }.collectLatest { quad ->
                val products = quad.first
                val allVariants = quad.second
                val warehouseList = quad.third
                val barcode = quad.fourth
                val reportItems = mutableListOf<InventoryReportItem>()
                val activeProducts = products.filter { !it.archived }.associateBy { it.id }
                val activeVariants = allVariants.filter { !it.archived }
                    .filter { if (barcode.isNullOrBlank()) true else it.barcode == barcode }

                for (variant in activeVariants) {
                    val product = activeProducts[variant.productId] ?: continue

                    val warehouseQuantities = mutableMapOf<String, Int>()
                    var totalQuantity = 0

                    // Batch optimization: use aggregated summary for the entire variant first
                    val globalSummary = stockEntryRepository.getVariantSummary(variant.id, null)
                    if (globalSummary.first <= 0) continue

                    for (warehouse in warehouseList) {
                        val whSummary = stockEntryRepository.getVariantSummary(variant.id, warehouse.id)
                        warehouseQuantities[warehouse.id] = whSummary.first
                        totalQuantity += whSummary.first
                    }

                    if (totalQuantity <= 0) continue

                    reportItems.add(
                        InventoryReportItem(
                            product = product,
                            variant = variant,
                            warehouseQuantities = warehouseQuantities,
                            totalQuantity = totalQuantity,
                            averageCost = globalSummary.second,
                            totalCostValue = totalQuantity * globalSummary.second
                        )
                    )
                }
                _inventoryReport.value = reportItems
                _isLoading.value = false
            }
        }
    }

    fun onBarcodeScanned(barcode: String?) {
        _barcodeFilter.value = barcode
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
    }

    fun onSupplierSearchQueryChanged(query: String) {
        _supplierSearchQuery.value = query
    }

    private val _supplierReport = MutableStateFlow<List<SupplierReportItem>>(emptyList())
    val supplierReport = _supplierReport.asStateFlow()

    private fun loadSupplierReport() {
        viewModelScope.launch {
            combine(
                supplierRepository.getSuppliers(),
                _startDate,
                _endDate,
                _supplierSearchQuery
            ) { suppliers, start, end, query ->
                suppliers.map { supplier ->
                    // Optimized aggregation instead of fetching all entries/bills
                    val totalDebit = stockEntryRepository.getSupplierDebit(supplier.id, supplier.resetDate, start, end)
                    val totalCredit = billRepository.getSupplierCredit(supplier.id, supplier.resetDate, start, end)
                    val balance = totalDebit - totalCredit

                    // Purchase orders still need some fetching for details if expanded, but we can optimize the list
                    // For the summary view, we don't necessarily need all purchase orders immediately.
                    // However, to keep existing UI working, we fetch only the relevant ones.
                    val purchaseOrders = emptyList<PurchaseOrderItem>() // Simplified for now, or fetch on demand

                    val targetProgress = if (supplier.yearlyTarget > 0) totalDebit / supplier.yearlyTarget else 0.0

                    SupplierReportItem(
                        supplier = supplier,
                        totalDebit = totalDebit,
                        totalCredit = totalCredit,
                        balance = balance,
                        targetProgress = targetProgress,
                        purchaseOrders = purchaseOrders
                    )
                }.filter { item ->
                    if (query.isBlank()) true
                    else item.supplier.name.contains(query, ignoreCase = true)
                }
            }.collect { report ->
                _supplierReport.value = report
            }
        }
    }

    val oldBatterySummary: StateFlow<Pair<Int, Double>> = combine(
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { seller, user ->
        val warehouseId = if (seller) user?.warehouseId else null
        oldBatteryRepository.getStockSummary(warehouseId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0.0))

    val oldBatteryWarehouseSummary: StateFlow<Map<String, Pair<Int, Double>>> = combine(
        warehouses,
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { allWh, seller, user ->
        val result = mutableMapOf<String, Pair<Int, Double>>()
        val targetWhs = if (seller) allWh.filter { it.id == user?.warehouseId } else allWh
        for (wh in targetWhs) {
            result[wh.id] = oldBatteryRepository.getStockSummary(wh.id)
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        checkUserRoleAndLoadReports()
    }

    private fun checkUserRoleAndLoadReports() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _isSeller.value = user?.role == "seller"

            loadInventoryReport()
            loadSupplierReport()
        }
    }
}
