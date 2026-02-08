package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import com.batterysales.data.repositories.OldBatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
    productRepository: ProductRepository,
    productVariantRepository: ProductVariantRepository,
    warehouseRepository: WarehouseRepository,
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

    init {
        checkUserRole()
    }

    private fun checkUserRole() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _isSeller.value = user?.role == "seller"
        }
    }

    val warehouses: StateFlow<List<Warehouse>> = warehouseRepository.getWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredWarehouses: StateFlow<List<Warehouse>> = combine(
        warehouses,
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { allWh, seller, user ->
        if (seller) allWh.filter { it.id == user?.warehouseId } else allWh
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryReport: StateFlow<List<InventoryReportItem>> = combine(
        productRepository.getProducts(),
        productVariantRepository.getAllVariantsFlow(),
        stockEntryRepository.getAllStockEntriesFlow(),
        filteredWarehouses,
        _barcodeFilter
    ) { products, allVariants, allStockEntries, warehouseList, barcode ->

        val reportItems = mutableListOf<InventoryReportItem>()
        val activeProducts = products.filter { !it.archived }.associateBy { it.id }
        val activeVariants = allVariants.filter { !it.archived }

        for (variant in activeVariants) {
            val product = activeProducts[variant.productId] ?: continue

            // Only count approved stock for reports
            val variantEntries = allStockEntries.filter { it.productVariantId == variant.id && it.status == "approved" }
            if (variantEntries.isEmpty()) continue

            val warehouseQuantities = mutableMapOf<String, Int>()
            var totalQuantity = 0

            for (warehouse in warehouseList) {
                val quantityInWarehouse = variantEntries
                    .filter { it.warehouseId == warehouse.id }
                    .sumOf { it.quantity }
                warehouseQuantities[warehouse.id] = quantityInWarehouse
                totalQuantity += quantityInWarehouse
            }

            if (totalQuantity <= 0) continue

            val positiveEntries = variantEntries.filter { it.quantity > 0 }
            val totalCostOfPurchases = positiveEntries.sumOf { it.totalCost }
            val totalItemsPurchased = positiveEntries.sumOf { it.quantity }
            val averageCost = if (totalItemsPurchased > 0) totalCostOfPurchases / totalItemsPurchased else 0.0
            val totalCostValue = totalQuantity * averageCost

            reportItems.add(
                InventoryReportItem(
                    product = product,
                    variant = variant,
                    warehouseQuantities = warehouseQuantities,
                    totalQuantity = totalQuantity,
                    averageCost = averageCost,
                    totalCostValue = totalCostValue
                )
            )
        }
        reportItems
    }.map { items ->
        if (barcodeFilter.value.isNullOrBlank()) {
            items
        } else {
            items.filter { it.variant.barcode == barcodeFilter.value }
        }
    }.onStart { _isLoading.value = true }
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val supplierReport: StateFlow<List<SupplierReportItem>> = combine(
        supplierRepository.getSuppliers(),
        stockEntryRepository.getAllStockEntriesFlow(),
        billRepository.getAllBillsFlow(),
        _startDate,
        _endDate,
        _supplierSearchQuery
    ) { suppliers, allEntries, allBills, start, end, query ->
        suppliers.map { supplier ->
            val supplierEntries = allEntries.filter { 
                it.supplierId == supplier.id && 
                it.status == "approved" &&
                (start == null || it.timestamp.time >= start) &&
                (end == null || it.timestamp.time <= end)
            }
            val supplierBills = allBills.filter { 
                it.supplierId == supplier.id &&
                (start == null || it.dueDate.time >= start) &&
                (end == null || it.dueDate.time <= end)
            }

            val totalDebit = supplierEntries.sumOf { it.totalCost }
            val totalCredit = supplierBills.sumOf { it.paidAmount }
            val balance = totalDebit - totalCredit
            
            val purchaseOrders = supplierEntries.map { entry ->
                val linkedBills = supplierBills.filter { it.relatedEntryId == entry.id }
                val linkedPaid = linkedBills.sumOf { it.paidAmount }
                PurchaseOrderItem(
                    entry = entry,
                    linkedPaidAmount = linkedPaid,
                    remainingBalance = entry.totalCost - linkedPaid,
                    referenceNumbers = linkedBills.map { it.referenceNumber }.filter { it.isNotEmpty() }
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
        }.filter { item ->
            if (query.isBlank()) true
            else {
                item.supplier.name.contains(query, ignoreCase = true) ||
                item.purchaseOrders.any { po ->
                    po.referenceNumbers.any { ref -> ref.contains(query, ignoreCase = true) }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val oldBatterySummary: StateFlow<Pair<Int, Double>> = combine(
        oldBatteryRepository.getAllTransactionsFlow(),
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { transactions, seller, user ->
        val filtered = if (seller) transactions.filter { it.warehouseId == user?.warehouseId } else transactions
        calculateOldBatterySummary(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0.0))

    val oldBatteryWarehouseSummary: StateFlow<Map<String, Pair<Int, Double>>> = combine(
        oldBatteryRepository.getAllTransactionsFlow(),
        isSeller,
        userRepository.getCurrentUserFlow()
    ) { transactions, seller, user ->
        val filtered = if (seller) transactions.filter { it.warehouseId == user?.warehouseId } else transactions
        filtered.groupBy { it.warehouseId }.mapValues { calculateOldBatterySummary(it.value) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private fun calculateOldBatterySummary(transactions: List<OldBatteryTransaction>): Pair<Int, Double> {
        var totalQty = 0
        var totalAmperes = 0.0
        transactions.forEach {
            when (it.type) {
                com.batterysales.data.models.OldBatteryTransactionType.INTAKE -> {
                    totalQty += it.quantity
                    totalAmperes += it.totalAmperes
                }
                com.batterysales.data.models.OldBatteryTransactionType.SALE -> {
                    totalQty -= it.quantity
                    totalAmperes -= it.totalAmperes
                }
                com.batterysales.data.models.OldBatteryTransactionType.ADJUSTMENT -> {
                    totalQty += it.quantity
                    totalAmperes += it.totalAmperes
                }
            }
        }
        return Pair(totalQty, totalAmperes)
    }
}
