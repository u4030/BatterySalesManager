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
    private val supplierRepository: SupplierRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val accountingRepository: AccountingRepository,
    private val bankRepository: BankRepository,
    private val warehouseRepository: com.batterysales.data.repositories.WarehouseRepository,
    private val userRepository: com.batterysales.data.repositories.UserRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers = _suppliers.asStateFlow()

    val warehouses: StateFlow<List<com.batterysales.data.models.Warehouse>> = warehouseRepository.getWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bills: Flow<PagingData<Bill>> = combine(
        _searchQuery,
        _suppliers,
        refreshTrigger
    ) { query, suppliers, _ ->
        Pair(query, suppliers)
    }.flatMapLatest { (query, suppliers) ->
        val suppliersMap = suppliers.associateBy { it.id }
        Pager(PagingConfig(pageSize = 20)) {
            BillPagingSource(repository)
        }.flow.map { pagingData ->
            if (query.isBlank()) pagingData
            else pagingData.filter { bill ->
                val supplierName = suppliersMap[bill.supplierId]?.name ?: ""
                bill.referenceNumber.contains(query, ignoreCase = true) ||
                        supplierName.contains(query, ignoreCase = true) ||
                        bill.description.contains(query, ignoreCase = true)
            }
        }.cachedIn(viewModelScope)
    }

    private val _allRecentPurchases = stockEntryRepository.getAllStockEntriesFlow()
        .map { entries -> 
            entries.filter { it.status == "approved" && it.totalCost > 0 }
                .take(2000) 
        }
        .distinctUntilChanged()
    
    private val _linkedAmounts = repository.getAllBillsFlow()
        .map { bills ->
            bills.filter { it.relatedEntryId != null }
                .groupBy { it.relatedEntryId!! }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
        }
        .distinctUntilChanged()
    
    val pendingPurchases: StateFlow<List<StockEntry>> = combine(
        _allRecentPurchases,
        _linkedAmounts
    ) { entries, linkedAmounts ->
        val grouped = entries.groupBy { it.orderId.ifEmpty { it.id } }
        grouped.mapNotNull { (key, group) ->
            val representative = group.first()
            val totalOrderCost = if (representative.grandTotalCost > 0) representative.grandTotalCost else group.sumOf { it.totalCost }
            val linkedAmount = linkedAmounts[key] ?: 0.0
            
            if (linkedAmount < totalOrderCost - 0.001) {
                representative.copy(
                    id = key,
                    totalCost = totalOrderCost - linkedAmount,
                    grandTotalCost = totalOrderCost
                )
            } else {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _isLastPage = MutableStateFlow(false)
    val isLastPage = _isLastPage.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        loadBills(reset = true)
        loadSuppliers()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun loadData() = loadInitialData()

    fun loadBills(reset: Boolean = false) {
        if (reset) {
            _isLoading.value = true
            refreshTrigger.value += 1
        }
    }

    fun loadSuppliers() {
        viewModelScope.launch {
            supplierRepository.getSuppliers().collect {
                _suppliers.value = it
            }
        }
    }

    fun addBill(
        description: String, 
        amount: Double, 
        dueDate: Date, 
        billType: BillType, 
        referenceNumber: String = "", 
        supplierId: String = "", 
        relatedEntryId: String? = null,
        warehouseId: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val finalWarehouseId = warehouseId ?: if (relatedEntryId != null) {
                    pendingPurchases.value.find { it.id == relatedEntryId || it.orderId == relatedEntryId }?.warehouseId
                } else {
                    userRepository.getCurrentUser()?.warehouseId
                }

                val bill = Bill(
                    description = description,
                    amount = amount,
                    dueDate = dueDate,
                    billType = billType,
                    referenceNumber = referenceNumber,
                    supplierId = supplierId,
                    relatedEntryId = relatedEntryId,
                    warehouseId = finalWarehouseId
                )
                repository.addBill(bill)
                loadBills(reset = true)
            } catch (e: Exception) {
                Log.e("BillViewModel", "Error adding bill", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun recordPayment(billId: String, amount: Double) {
        viewModelScope.launch {
            try {
                // Fetch the bill once to get details
                val snapshot = repository.getBill(billId) ?: return@launch
                val bill = snapshot
                val supplier = _suppliers.value.find { it.id == bill.supplierId }
                val supplierName = supplier?.name ?: ""
                
                repository.recordPayment(billId, amount)

                // Logic updated based on requirement:
                // 1. Promissory Notes (BILL/TRANSFER/OTHER) are deducted from Treasury (Accounting)
                // 2. Checks (CHECK) are deducted from Bank ONLY.

                if (bill.billType == com.batterysales.data.models.BillType.CHECK) {
                    // Record ONLY in bank if it's a check
                    val bankTransaction = com.batterysales.data.models.BankTransaction(
                        type = com.batterysales.data.models.BankTransactionType.WITHDRAWAL,
                        amount = amount,
                        description = "تسديد لشيك: ${bill.description} (المورد: $supplierName)",
                        billId = billId,
                        referenceNumber = bill.referenceNumber,
                        supplierName = supplierName
                    )
                    bankRepository.addTransaction(bankTransaction)
                } else {
                    // Record ONLY in treasury (Accounting) for other types
                    val transaction = Transaction(
                        type = com.batterysales.data.models.TransactionType.EXPENSE,
                        amount = amount,
                        description = "تسديد ${if (amount >= (bill.amount - bill.paidAmount)) "كلي" else "جزئي"} لكمبيالة: ${bill.description} (المورد: $supplierName)",
                        relatedId = billId,
                        referenceNumber = bill.referenceNumber,
                        warehouseId = bill.warehouseId
                    )
                    accountingRepository.addTransaction(transaction)
                }

                loadBills(reset = true)
            } catch (e: Exception) {
                Log.e("BillViewModel", "Error recording payment", e)
            }
        }
    }

    fun deleteBill(billId: String) {
        viewModelScope.launch {
            try {
                repository.deleteBill(billId)
                // Also delete related treasury or bank transactions
                accountingRepository.deleteTransactionsByRelatedId(billId)
                bankRepository.deleteTransactionsByBillId(billId)
                loadBills(reset = true)
            } catch (e: Exception) {
                Log.e("BillViewModel", "Error deleting bill", e)
            }
        }
    }

    fun updateBill(bill: Bill) {
        viewModelScope.launch {
            try {
                repository.updateBill(bill)
                // Also update treasury transactions description only
                // Overwriting amount would be wrong if partial payments exist
                accountingRepository.updateTransactionByRelatedId(
                    relatedId = bill.id,
                    newDescription = "تسديد لكمبيالة: ${bill.description}"
                )
                loadBills(reset = true)
            } catch (e: Exception) {
                Log.e("BillViewModel", "Error updating bill", e)
            }
        }
    }
}
