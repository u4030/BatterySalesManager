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

    private val _oldBatteryWarehouseSummary = MutableStateFlow<Map<String, Pair<Int, Double>>>(emptyMap())
    val oldBatteryWarehouseSummary = _oldBatteryWarehouseSummary.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)
    private var isMigrationRun = false

    // For Sidebar Navigation: Track all items in order to find indices
    // Updated to include date filtering synchronization
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
        val start = args[4] as Long?
        val end = args[5] as Long?
        val query = args[6] as String?

        val pMap = products.filter { !it.archived }.associateBy { it.id }
        val userWhId = user?.warehouseId

        // This is a simplified version of the PagingSource logic to keep UI responsive
        // We might not be able to check EVERY StockEntry here for performance,
        // but we can at least filter by basic criteria.

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
    val inventoryReport: Flow<PagingData<InventoryReportItem>> = combine(
        barcodeFilter,
        filteredWarehouses,
        productsMap,
        _isSeller,
        _inventoryStartDate,
        _inventoryEndDate,
        refreshTrigger
    ) { args ->
        args
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).flatMapLatest { args ->
        val query = args[0] as String?
        val warehouseList = args[1] as List<Warehouse>
        val pMap = args[2] as Map<String, Product>
        val seller = args[3] as Boolean
        val start = args[4] as Long?
        val end = args[5] as Long?

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

        // Reactively refresh inventory when stock changes (Lightweight listener)
        firestore.collection(StockEntry.COLLECTION_NAME)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                if (e == null && snapshot != null) {
                    Log.d("ReportsViewModel", "Stock changed detected, refreshing")
                    refreshTrigger.value += 1
                }
            }

        // Also listen for Product updates to refresh names/specs immediately
        firestore.collection(Product.COLLECTION_NAME)
            .addSnapshotListener { snapshot, e ->
                if (e == null && snapshot != null) {
                    Log.d("ReportsViewModel", "Product updated, refreshing")
                    refreshTrigger.value += 1
                }
            }

        // Also listen for Variant updates
        firestore.collection(ProductVariant.COLLECTION_NAME)
            .addSnapshotListener { snapshot, e ->
                if (e == null && snapshot != null) {
                    Log.d("ReportsViewModel", "Variant updated, refreshing")
                    refreshTrigger.value += 1
                }
            }

        _selectedTab.onEach { tab ->
            if (tab == 2 && _supplierReport.value.isEmpty()) {
                loadSupplierReport()
            }
        }.launchIn(viewModelScope)

        // Run migration for existing data
        viewModelScope.launch {
            try {
                // Run only once per session to avoid redundant Firestore reads
                if (!isMigrationRun) {
                    stockEntryRepository.migrateInvoiceDates()
                    isMigrationRun = true
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

            // Calculate Global Grand Total once
            viewModelScope.launch {
                _isInventoryLoading.value = true
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
                } finally {
                    _isInventoryLoading.value = false
                }
            }
        }
    }

    fun loadScrapReport() {
        viewModelScope.launch {
            _isScrapLoading.value = true
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
            _isScrapLoading.value = false
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

                // 1. Fetch all relevant entries and bills once in parallel
                val allEntriesJob = async { stockEntryRepository.getAllStockEntries() }
                val allBillsJob = async { billRepository.getAllBills() }

                val allEntries = allEntriesJob.await()
                val allBills = allBillsJob.await()

                // Filter suppliers by name OR by search results in entries/bills (invoice/ref)
                val suppliers = if (query.isBlank()) {
                    allSuppliers
                } else {
                    val matchingEntrySupplierIds = allEntries.filter {
                        it.invoiceNumber.contains(query, ignoreCase = true) || it.orderId.contains(query, ignoreCase = true)
                    }.map { it.supplierId }.toSet()

                    val matchingBillSupplierIds = allBills.filter {
                        it.referenceNumber.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
                    }.map { it.supplierId }.toSet()

                    allSuppliers.filter {
                        it.name.contains(query, ignoreCase = true) ||
                                matchingEntrySupplierIds.contains(it.id) ||
                                matchingBillSupplierIds.contains(it.id)
                    }
                }

                val start = _startDate.value
                val end = _endDate.value


                val report = suppliers.map { supplier ->
                    async {
                        val adjustedStart = start?.let { com.batterysales.utils.DateUtils.getStartOfDay(it) }
                        val adjustedEnd = end?.let { com.batterysales.utils.DateUtils.getEndOfDay(it) }

                        // Filter entries for this supplier
                        val supplierEntries = allEntries.filter { entry ->
                            val matchId = entry.supplierId.isNotEmpty() && entry.supplierId == supplier.id
                            // Robust name matching fallback for legacy entries
                            val matchName = entry.supplier.isNotBlank() &&
                                    (entry.supplier.trim().equals(supplier.name.trim(), ignoreCase = true) ||
                                            entry.supplier.trim().contains(supplier.name.trim(), ignoreCase = true))

                            (matchId || matchName) &&
                                    entry.status == "approved" &&
                                    (supplier.resetDate == null || !entry.getEffectiveDate().before(supplier.resetDate)) &&
                                    (adjustedStart == null || entry.getEffectiveDate().time >= adjustedStart) &&
                                    (adjustedEnd == null || entry.getEffectiveDate().time <= adjustedEnd)
                        }

                        // Filter bills for this supplier
                        // ملاحظة: الربط يعتمد على كافة الشيكات للمورد بغض النظر عن الفلتر الزمني للتقرير
                        // لضمان دقة الأرصدة والروابط التلقائية
                        val supplierBills = allBills.filter { bill ->
                            bill.supplierId == supplier.id &&
                                    (supplier.resetDate == null || !bill.createdAt.before(supplier.resetDate))
                        }

                        // الشيكات التي تقع ضمن الفلتر الزمني (لعرضها في المجاميع إذا لزم الأمر مستقبلاً)
                        val filteredBills = supplierBills.filter { bill ->
                            (adjustedStart == null || bill.dueDate.time >= adjustedStart) &&
                                    (adjustedEnd == null || bill.dueDate.time <= adjustedEnd)
                        }

                        // Calculate totals locally for accuracy and consistency
                        // totalDebit should use the net cost (after returns)
                        val totalDebit = supplierEntries.sumOf { it.getNetCost() }
                        val totalCredit = supplierBills.sumOf { it.paidAmount }
                        val balance = totalDebit - totalCredit

                        // Group entries into Purchase Orders
                        // Priority: orderId -> invoiceNumber -> id
                        // ملاحظة: هذا المفتاح يجب أن يتطابق مع المفتاح المستخدم في BillRepository.autoLinkBillsForSupplier
                        // نستبعد القيود الفردية التي ليس لها رقم فاتورة أو طلبية من منطق التجميع الرئيسي هنا
                        val groupedEntries = supplierEntries
                            .filter { it.orderId.isNotEmpty() || it.invoiceNumber.isNotEmpty() || it.quantity > 0 }
                            .groupBy { it.orderId.ifEmpty { it.invoiceNumber.ifEmpty { it.id } } }

                        val purchaseOrders = groupedEntries.map { (key, group) ->
                            val representative = group.first()
                            val totalOrderCost = group.sumOf { it.getNetCost() }

                            val allLinkedBills = supplierBills.filter { bill ->
                                bill.relatedEntryId == key ||
                                bill.referenceNumber == key ||
                                (bill.referenceNumber.isNotEmpty() && (bill.referenceNumber == representative.invoiceNumber || bill.referenceNumber == representative.id)) ||
                                group.any { entry -> entry.id == bill.relatedEntryId || (bill.referenceNumber.isNotEmpty() && bill.referenceNumber == entry.invoiceNumber) }
                            }.distinctBy { it.id }

                            val totalLinkedPaid = allLinkedBills.sumOf { it.paidAmount }
                            val totalLinkedAmount = allLinkedBills.sumOf { it.amount }

                            // حساب المبالغ المرتبطة تلقائياً من الشيكات التي لا تحمل ربطاً يدوياً
                            val autoAllocatedBills = supplierBills
                                .filter { bill -> allLinkedBills.none { it.id == bill.id } }

                            val autoAllocatedAmountForThisOrder = autoAllocatedBills
                                .sumOf { it.autoAllocations[key] ?: 0.0 }

                            // حساب المبلغ المسدد فعلياً من الشيكات المرتبطة تلقائياً
                            // يتم احتساب نسبة مئوية من المبلغ المسدد من الشيك بناءً على تخصيصه لهذه الطلبية
                            val autoAllocatedCashPaid = autoAllocatedBills.sumOf { bill ->
                                val allocation = bill.autoAllocations[key] ?: 0.0
                                if (bill.amount > 0) (allocation / bill.amount) * bill.paidAmount else 0.0
                            }

                            // Use the actual calculated sum from the entries in the order for consistency
                            val finalTotalCost = totalOrderCost
                            val totalActualPaid = totalLinkedPaid + autoAllocatedCashPaid

                            val refs = (allLinkedBills.filter { bill ->
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
                            } + supplierBills.filter { it.autoLinkedEntryIds.contains(key) && it.relatedEntryId == null }
                                .map { bill ->
                                    val typeStr = when (bill.billType) {
                                        BillType.CHECK -> "شيك"
                                        BillType.BILL -> "كمبيالة"
                                        BillType.TRANSFER -> "تحويل"
                                        BillType.CASH -> "نقدي"
                                        BillType.VISA -> "فيزا"
                                        BillType.E_WALLET -> "محفظة"
                                    }
                                    "$typeStr: ${bill.referenceNumber} (ربط تلقائي)"
                                }).distinct()

                            // تحديد ما إذا كان هناك ربط يدوي فعلي
                            // نعتبره يدوياً إذا كان مرتبطاً عبر المعرف أو المرجع
                            val actualManualBills = allLinkedBills

                            PurchaseOrderItem(
                                entry = representative.copy(totalCost = finalTotalCost),
                                linkedPaidAmount = totalLinkedPaid,
                                remainingBalance = finalTotalCost - totalActualPaid,
                                referenceNumbers = refs,
                                items = group,
                                autoLinkedAmount = autoAllocatedAmountForThisOrder,
                                hasManualLink = allLinkedBills.isNotEmpty(),
                                totalActualPaid = totalActualPaid,
                                totalLinkedAmount = totalLinkedAmount + autoAllocatedAmountForThisOrder
                            )
                        }.sortedWith(compareByDescending<PurchaseOrderItem> { it.entry.getEffectiveDate() }.thenByDescending { it.entry.timestamp })

                        val (obligated, regular) = purchaseOrders.partition { po ->
                            // الطلبية تعتبر "مرتبطة" وتنتقل للقائمة التمددية فقط إذا كان هناك ربط يدوي
                            // أو إذا كانت مغطاة بالكامل بواسطة الشيكات المرتبطة تلقائياً.
                            // أما الطلبيات المغطاة جزئياً فتبقى في القائمة الرئيسية مع إظهار الملاحظة.
                            val isFullyCovered = po.totalLinkedAmount >= po.entry.totalCost - 0.001
                            po.hasManualLink || isFullyCovered
                        }

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
