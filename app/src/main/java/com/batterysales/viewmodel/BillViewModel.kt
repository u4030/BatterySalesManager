package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import com.batterysales.data.paging.BillPagingSource
import com.google.firebase.firestore.DocumentSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import android.util.Log
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class BillViewModel @Inject constructor(
    private val repository: BillRepository,
    private val summaryRepository: SummaryRepository,
    val userRepository: UserRepository, 
    private val supplierRepository: SupplierRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val accountingRepository: AccountingRepository,
    private val bankRepository: BankRepository,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedSupplierId = MutableStateFlow<String?>(null)
    val selectedSupplierId = _selectedSupplierId.asStateFlow()

    private val _selectedSupplierBalance = MutableStateFlow<Double?>(null)
    val selectedSupplierBalance = _selectedSupplierBalance.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers = _suppliers.asStateFlow()

    private val _totalUnpaid = MutableStateFlow(0.0)
    val totalUnpaid = _totalUnpaid.asStateFlow()

    val warehouses: StateFlow<List<Warehouse>> = flow {
        emit(warehouseRepository.getWarehousesOnce())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val refreshTrigger = MutableStateFlow(0)
    private val _isDataLoaded = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bills: Flow<PagingData<Bill>> = combine(_searchQuery, _selectedSupplierId, refreshTrigger, _isDataLoaded) { query, supplierId, _, loaded ->
        if (!loaded && query.isEmpty() && supplierId == null) return@combine null
        Triple(query, supplierId, loaded)
    }.filterNotNull().flatMapLatest { (query, supplierId, _) ->
        Pager(PagingConfig(pageSize = 25)) {
            BillPagingSource(repository, query.ifBlank { null }, supplierId)
        }.flow.cachedIn(viewModelScope)
    }

    val pendingPurchases: StateFlow<List<StockEntry>> = flow {
        val entries = stockEntryRepository.getRecentApprovedPurchases(limit = 100)
        val linkedAmounts = repository.getLinkedAmounts()
        val grouped = entries.groupBy { it.orderId.ifEmpty { it.id } }
        emit(grouped.mapNotNull { (key, group) ->
            val representative = group.first()
            val totalOrderCost = if (representative.grandTotalCost > 0) representative.grandTotalCost else group.sumOf { it.getNetCost() }
            val linkedAmount = linkedAmounts[key] ?: 0.0
            if (linkedAmount < (totalOrderCost - 0.001)) representative.copy(id = key, totalCost = totalOrderCost - linkedAmount, grandTotalCost = totalOrderCost) else null
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        // --- ELITE STRATEGY: One read for unpaid total ---
        viewModelScope.launch { 
            loadUnpaidFromSummary() 
            _suppliers.value = supplierRepository.getSuppliersOnce()
        }
        loadData()
    }

    private suspend fun loadUnpaidFromSummary() {
        try {
            val status = summaryRepository.getFinancialStatus()
            _totalUnpaid.value = (status?.totalUnpaidBills ?: 0.0) + (status?.totalUnpaidChecks ?: 0.0)
        } catch (e: Exception) { Log.e("BillViewModel", "Error loading bills summary", e) }
    }

    fun loadData() {
        _isDataLoaded.value = true
        _isLoading.value = true
        viewModelScope.launch {
            loadUnpaidFromSummary()
            refreshTrigger.value += 1
            _isLoading.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isNotEmpty()) _isDataLoaded.value = true
    }

    fun onSupplierSelected(supplierId: String?) {
        _selectedSupplierId.value = supplierId
        _isDataLoaded.value = true
        if (supplierId != null) {
            viewModelScope.launch {
                val supplier = supplierRepository.getSupplier(supplierId)
                _selectedSupplierBalance.value = supplier?.currentBalance
            }
        } else {
            _selectedSupplierBalance.value = null
        }
    }

    fun updateBill(bill: Bill) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateBill(bill)
                loadData()
            } finally { _isLoading.value = false }
        }
    }

    fun recordPayment(billId: String, amount: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.recordPayment(billId, amount)
                loadData()
            } finally { _isLoading.value = false }
        }
    }

    fun recordPayment(bill: Bill, amount: Double, method: String, warehouseId: String, notes: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.addBillPayment(bill, amount, method, warehouseId, notes)
                loadData()
            } finally { _isLoading.value = false }
        }
    }

    fun addBill(description: String, amount: Double, dueDate: Date, billType: BillType, referenceNumber: String = "", supplierId: String = "", relatedEntryId: String? = null, warehouseId: String? = null, payImmediately: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val bill = Bill(
                    description = description, 
                    amount = amount, 
                    dueDate = dueDate, 
                    billType = billType, 
                    referenceNumber = referenceNumber, 
                    supplierId = supplierId, 
                    relatedEntryId = relatedEntryId, 
                    warehouseId = warehouseId, 
                    status = if (payImmediately) BillStatus.PAID else BillStatus.UNPAID, 
                    paidAmount = if (payImmediately) amount else 0.0, 
                    paidDate = if (payImmediately) Date() else null
                )
                repository.addBill(bill)
                loadData()
            } finally { _isLoading.value = false }
        }
    }

    fun deleteBill(billId: String) {
        viewModelScope.launch {
            repository.deleteBill(billId)
            accountingRepository.deleteTransactionsByRelatedId(billId)
            bankRepository.deleteTransactionsByBillId(billId)
            loadData()
        }
    }
}
 
