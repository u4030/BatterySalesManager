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
    private val bankRepository: BankRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers = _suppliers.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bills: Flow<PagingData<Bill>> = combine(
        _searchQuery,
        _suppliers
    ) { query, suppliers ->
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

    private val _allRecentPurchases = MutableStateFlow<List<StockEntry>>(emptyList())
    private val _linkedAmounts = MutableStateFlow<Map<String, Double>>(emptyMap())
    
    val pendingPurchases: StateFlow<List<StockEntry>> = combine(
        _allRecentPurchases,
        _linkedAmounts
    ) { entries, linkedAmounts ->
        val grouped = entries.groupBy { it.orderId.ifEmpty { it.id } }
        grouped.mapNotNull { (key, group) ->
            val representative = group.first()
            val totalOrderCost = if (representative.grandTotalCost > 0) representative.grandTotalCost else group.sumOf { it.totalCost }
            val linkedAmount = linkedAmounts[key] ?: 0.0
            
            if (linkedAmount < totalOrderCost - 0.001) { // Small epsilon for float comparison
                // We return the representative entry with its ID or OrderId as its ID,
                // and we'll use totalCost to store the REMAINING balance for the UI to display.
                representative.copy(
                    id = key, // Use orderId or entryId as the ID
                    totalCost = totalOrderCost - linkedAmount,
                    grandTotalCost = totalOrderCost // Keep original total cost too
                )
            } else {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        loadPendingPurchases()
        loadLinkedIds()
    }

    private fun loadLinkedIds() {
        viewModelScope.launch {
            try {
                _linkedAmounts.value = repository.getLinkedAmounts()
            } catch (e: Exception) {
                Log.e("BillViewModel", "Error loading linked amounts", e)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun loadData() = loadInitialData()

    fun loadBills(reset: Boolean = false) {
        if (reset) {
            _isLoading.value = true
            // Paging 3 will handle refresh when flow is collected
        }
    }

    fun loadSuppliers() {
        viewModelScope.launch {
            supplierRepository.getSuppliers().collect {
                _suppliers.value = it
            }
        }
    }

    fun loadPendingPurchases() {
        viewModelScope.launch {
            // Fetch only last 100 approved purchases instead of all history
            val entries = stockEntryRepository.getRecentApprovedPurchases(100)
            _allRecentPurchases.value = entries
        }
    }

    fun addBill(description: String, amount: Double, dueDate: Date, billType: BillType, referenceNumber: String = "", supplierId: String = "", relatedEntryId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val warehouseId = if (relatedEntryId != null) {
                    _allRecentPurchases.value.find { it.id == relatedEntryId || it.orderId == relatedEntryId }?.warehouseId
                } else null

                val bill = Bill(
                    description = description,
                    amount = amount,
                    dueDate = dueDate,
                    billType = billType,
                    referenceNumber = referenceNumber,
                    supplierId = supplierId,
                    relatedEntryId = relatedEntryId,
                    warehouseId = warehouseId
                )
                repository.addBill(bill)
                loadBills(reset = true)
                loadLinkedIds() // Refresh linked IDs after adding a bill
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

                // Record in treasury (always)
                val transaction = Transaction(
                    type = com.batterysales.data.models.TransactionType.EXPENSE,
                    amount = amount,
                    description = "تسديد ${if (amount >= (bill.amount - bill.paidAmount)) "كلي" else "جزئي"} ${if (bill.billType == BillType.CHECK) "لشيك" else "لكمبيالة"}: ${bill.description} (المورد: $supplierName)",
                    relatedId = billId,
                    referenceNumber = bill.referenceNumber,
                    warehouseId = bill.warehouseId
                )
                accountingRepository.addTransaction(transaction)

                // Additionally record in bank if it's a check
                if (bill.billType == com.batterysales.data.models.BillType.CHECK) {
                    val bankTransaction = com.batterysales.data.models.BankTransaction(
                        type = com.batterysales.data.models.BankTransactionType.WITHDRAWAL,
                        amount = amount,
                        description = "تسديد لشيك: ${bill.description}",
                        billId = billId,
                        referenceNumber = bill.referenceNumber,
                        supplierName = supplierName
                    )
                    bankRepository.addTransaction(bankTransaction)
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
