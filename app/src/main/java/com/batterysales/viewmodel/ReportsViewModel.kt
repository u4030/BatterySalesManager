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
    val autoLinkedAmount: Double = 0.0, // المبلغ المرتبط تلقائياً من شيكات/كمبيالات أخرى
    val hasManualLink: Boolean = false, // هل يوجد ربط يدوي (عن طريق الرقم أو الحقل المخصص)
    val totalActualPaid: Double = 0.0, // إجمالي المبالغ المسددة فعلياً (نقدياً) لهذه الطلبية
    val totalLinkedAmount: Double = 0.0 // إجمالي مبالغ الشيكات (الورقية) المرتبطة يدوياً وتلقائياً
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
    @Suppress("UNCHECKED_CAST")
    val allInventoryItemNames: StateFlow<List<String>> = combine(
        productRepository.getProducts(),
        productVariantRepository.getAllVariantsFlow(),
        _isSeller,
        userRepository.getCurrentUserFlow(),
        _inventoryStartDate,
        _inventoryEndDate,
        _barcodeFilter,
        refreshTrigger
    ) { args ->
        val products = args[0] as List<Product>
        val variants = args[1] as List<ProductVariant>
        val seller = args[2] as Boolean
        val user = args[3] as User?
        val query = args[6] as String?

        val pMap = products.filter { !it.archived }.associateBy { it.id }
        val userWhId = user?.warehouseId

        variants.filter { !it.archived }
            .filter { v -> pMap.containsKey(v.productId) }
            .filter { v ->
                if (!seller || userWhId == null) true
                else (v.currentStock?.get(userWhId) ?: 0) > 0
            }
            .filter { v ->
                if (query.isNullOrBlank()) true
                else {
                    val pName = pMap[v.productId]?.name ?: ""
                    pName.contains(query, ignoreCase = true) || v.barcode == query
                }
            }
            .sortedWith(compareBy<ProductVariant> { pMap[it.productId]?.name ?: "" }.thenBy { it.capacity })
            .map { v -> pMap[v.productId]?.name ?: "" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    @Suppress("UNCHECKED_CAST")
    val inventoryReport: Flow<PagingData<InventoryReportItem>> = combine(
        barcodeFilter,
        filteredWarehouses,
        productsMap,
        _isSeller,
        _inventoryStartDate,
        _inventoryEndDate,
        refreshTrigger
    ) { args ->
        Sextuple(
            args[0] as String?,
            args[1] as List<Warehouse>,
            args[2] as Map<String, Product>,
            args[3] as Boolean,
            args[4] as Long?,
            args[5] as Long?
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).flatMapLatest { (query, warehouseList, pMap, seller, start, end) ->
        Pager(PagingConfig(pageSize = 25)) {
            InventoryPagingSource(firestore, stockEntryRepository, pMap, warehouseList, query, seller, start, end)
        }.flow.cachedIn(viewModelScope)
    }

    init {
        userRepository.getCurrentUserFlow()
            .onEach { user ->
                _isSeller.value = user?.role == com.batterysales.data.models.User.ROLE_SELLER
                refreshAll()
            }.launchIn(viewModelScope)

        _selectedTab.onEach { tab ->
            if (tab == 2 && _supplierReport.value.isEmpty()) {
                loadSupplierReport()
            }
        }.launchIn(viewModelScope)

        // Run migration for existing data
        viewModelScope.launch {
            try {
                if (!settingsManager.isMigrationDone()) {
                    stockEntryRepository.migrateInvoiceDates()
                    stockEntryRepository.migrateAllVariants(productRepository, supplierRepository, billRepository)
                    settingsManager.setMigrationDone(true)
                }
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Migration failed", e)
            }
        }
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

    fun onInventoryDateRangeSelected(start: Long?, end: Long?) {
        _inventoryStartDate.value = start
        _inventoryEndDate.value = end
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

    private var supplierJob: kotlinx.coroutines.Job? = null
    fun loadSupplierReport() {
        supplierJob?.cancel()
        supplierJob = viewModelScope.launch {
            try {
                _isSupplierLoading.value = true
                val query = _supplierSearchQuery.value
                val allSuppliers = supplierRepository.getSuppliersOnce()

                val suppliers = if (query.isBlank()) {
                    allSuppliers
                } else {
                    allSuppliers.filter { it.name.contains(query, ignoreCase = true) }
                }

                val start = _startDate.value
                val end = _endDate.value
                val supplierIds = suppliers.map { it.id }

                val allEntriesJob = async { stockEntryRepository.getEntriesBySuppliers(supplierIds) }
                val allBillsJob = async { billRepository.getBillsBySuppliers(supplierIds) }

                val allEntries: List<StockEntry> = allEntriesJob.await()
                val allBills: List<Bill> = allBillsJob.await()

                val report = suppliers.map { supplier: Supplier ->
                    async {
                        val adjustedStart = start?.let { com.batterysales.utils.DateUtils.getStartOfDay(it) }
                        val adjustedEnd = end?.let { com.batterysales.utils.DateUtils.getEndOfDay(it) }

                        val rawSupplierEntries: List<StockEntry> = allEntries.filter { entry: StockEntry ->
                            val matchId = entry.supplierId.isNotEmpty() && entry.supplierId == supplier.id
                            val matchName = entry.supplier.isNotBlank() &&
                                    (entry.supplier.trim().equals(supplier.name.trim(), ignoreCase = true) ||
                                            entry.supplier.trim().contains(supplier.name.trim(), ignoreCase = true))

                            (matchId || matchName) &&
                                    entry.status == "approved" &&
                                    (supplier.resetDate == null || !entry.getEffectiveDate().before(supplier.resetDate))
                        }

                        val orderToInvoiceMap: Map<String, String> = rawSupplierEntries
                            .filter { it.invoiceNumber.trim().isNotEmpty() && it.orderId.trim().isNotEmpty() }
                            .associate { it.orderId.trim() to it.invoiceNumber.trim() }
                        
                        val supplierEntries: List<StockEntry> = rawSupplierEntries.map { entry: StockEntry ->
                            val orderKey = entry.orderId.trim()
                            if (entry.invoiceNumber.trim().isEmpty() && orderKey.isNotEmpty() && orderToInvoiceMap.containsKey(orderKey)) {
                                entry.copy(invoiceNumber = orderToInvoiceMap[orderKey]!!)
                            } else entry
                        }

                        val supplierBills: List<Bill> = allBills.filter { bill: Bill ->
                            bill.supplierId == supplier.id &&
                                    (supplier.resetDate == null || !bill.createdAt.before(supplier.resetDate))
                        }

                        val groupedEntries: Map<String, List<StockEntry>> = supplierEntries
                            .groupBy { it.invoiceNumber.trim().ifEmpty { it.orderId.trim().ifEmpty { it.id } } }

                        val purchaseOrders: List<PurchaseOrderItem> = groupedEntries.map { entryTuple ->
                            val group: List<StockEntry> = entryTuple.value
                            val key: String = entryTuple.key
                            val representative: StockEntry = group.first()

                            // Use denormalized remainingBalance if available, otherwise calculate
                            val totalOrderCost = group.sumOf { it.getNetCost() }
                            val effectiveBalance = representative.remainingBalance ?: totalOrderCost

                            val manualLinkedBills: List<Bill> = supplierBills.filter { bill: Bill ->
                                val ref = bill.referenceNumber.trim()
                                bill.relatedEntryId == key || 
                                ref == key || 
                                (ref.isNotEmpty() && (ref == representative.invoiceNumber.trim() || ref == representative.id)) ||
                                group.any { entry: StockEntry ->
                                    entry.id == bill.relatedEntryId ||
                                    (ref.isNotEmpty() && ref == entry.invoiceNumber.trim()) 
                                }
                            }.distinctBy { it.id }

                            val manualPaid = manualLinkedBills.sumOf { it.paidAmount }
                            val manualPaper = manualLinkedBills.sumOf { it.amount }

                            val refs: MutableList<String> = (manualLinkedBills.filter { bill ->
                                bill.referenceNumber.isNotEmpty()
                            }.map { bill ->
                                val typeStr = when (bill.billType) {
                                    BillType.CHECK -> "شيك"
                                    BillType.BILL -> "كمبيالة"
                                    BillType.TRANSFER -> "تحويل"
                                    BillType.CASH -> "نقدي"
                                    BillType.VISA -> "فيزا"
                                    BillType.E_WALLET -> "محفظة"
                                }
                                "$typeStr: ${bill.referenceNumber}${if(bill.status != BillStatus.PAID) " (غير مسدد)" else ""}"
                            }.distinct()).toMutableList()

                            // Add automated notes stored in DB
                            refs.addAll(representative.settlementNotes)

                            PurchaseOrderItem(
                                entry = representative.copy(totalCost = totalOrderCost),
                                linkedPaidAmount = manualPaid,
                                remainingBalance = effectiveBalance,
                                referenceNumbers = refs.distinct(),
                                items = group,
                                hasManualLink = manualLinkedBills.isNotEmpty(),
                                totalActualPaid = totalOrderCost - effectiveBalance,
                                totalLinkedAmount = maxOf(manualPaper, totalOrderCost - effectiveBalance)
                            )
                        }

                        val positiveOrders: List<PurchaseOrderItem> = purchaseOrders.filter { it.entry.totalCost > 0 }
                            .sortedWith(compareByDescending<PurchaseOrderItem> { it.entry.getEffectiveDate() }.thenByDescending { it.entry.timestamp })

                        val totalDebit = if (start == null && end == null) supplier.totalDebit else positiveOrders.sumOf { it.entry.totalCost }
                        val totalCredit = if (start == null && end == null) supplier.totalCredit else (supplierBills.sumOf { it.paidAmount } + purchaseOrders.filter { it.entry.totalCost < 0 }.sumOf { -it.entry.totalCost })
                        val balance = if (start == null && end == null) supplier.currentBalance else (totalDebit - totalCredit)

                        val finalOrdersForDisplay: List<PurchaseOrderItem> = positiveOrders.filter { po: PurchaseOrderItem ->
                            (adjustedStart == null || po.entry.getEffectiveDate().time >= adjustedStart) &&
                                    (adjustedEnd == null || po.entry.getEffectiveDate().time <= adjustedEnd)
                        }

                        val partitionedResult: Pair<List<PurchaseOrderItem>, List<PurchaseOrderItem>> = finalOrdersForDisplay.partition { po: PurchaseOrderItem ->
                            val isFullyCovered = po.totalLinkedAmount >= po.entry.totalCost - 0.001
                            po.hasManualLink || isFullyCovered
                        }
                        val obligated = partitionedResult.first
                        val regular = partitionedResult.second

                        val targetProgress = if (supplier.yearlyTarget > 0) totalDebit / supplier.yearlyTarget else 0.0

                        SupplierReportItem(
                            supplier = supplier,
                            totalDebit = totalDebit,
                            totalCredit = totalCredit,
                            balance = balance,
                            targetProgress = targetProgress,
                            regularOrders = regular,
                            obligatedOrders = obligated
                        )
                    }
                }.awaitAll()

                _supplierReport.value = report
                    .filter { it.totalDebit > 0 || it.totalCredit > 0 || query.isNotBlank() }
                    .sortedBy { it.supplier.name }
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error loading supplier report", e)
            } finally {
                _isSupplierLoading.value = false
            }
        }
    }
}
