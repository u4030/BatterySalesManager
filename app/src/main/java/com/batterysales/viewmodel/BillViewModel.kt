package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
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
    private val userRepository: UserRepository
) : ViewModel() {

    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers = _suppliers.asStateFlow()

    val filteredBills: StateFlow<List<Bill>> = combine(
        _bills,
        _suppliers,
        _searchQuery
    ) { bills, suppliers, query ->
        if (query.isBlank()) return@combine bills

        val suppliersMap = suppliers.associateBy { it.id }
        bills.filter { bill ->
            val supplierName = suppliersMap[bill.supplierId]?.name ?: ""
            bill.referenceNumber.contains(query, ignoreCase = true) ||
            supplierName.contains(query, ignoreCase = true) ||
            bill.description.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _linkedAmounts = MutableStateFlow<Map<String, Double>>(emptyMap())
    
    val pendingPurchases: StateFlow<List<StockEntry>> = combine(
        stockEntryRepository.getAllStockEntriesFlow(),
        _linkedAmounts
    ) { entries, linkedAmounts ->
        // Only consider approved purchases (quantity > 0 and status approved)
        val purchases = entries.filter { it.status == StockEntry.STATUS_APPROVED && it.totalCost > 0 }

        val grouped = purchases.groupBy { it.orderId.ifEmpty { it.id } }
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
        }.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _isLastPage = MutableStateFlow(false)
    val isLastPage = _isLastPage.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null

    init {
        userRepository.getCurrentUserFlow()
            .onEach {
                loadInitialData()
            }.launchIn(viewModelScope)
    }

    fun loadInitialData() {
        loadBills(reset = true)
        loadSuppliers()
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
            lastDocument = null
            _bills.value = emptyList()
            _isLastPage.value = false
            _isLoading.value = true
        }

        if (_isLastPage.value || _isLoadingMore.value) return

        viewModelScope.launch {
            try {
                if (!reset) _isLoadingMore.value = true

                val result = repository.getBillsPaginated(
                    lastDocument = lastDocument,
                    limit = 20
                )

                val newBills = result.first
                lastDocument = result.second

                _bills.update { current -> if (reset) newBills else current + newBills }
                _isLastPage.value = newBills.size < 20

            } finally {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    fun loadSuppliers() {
        viewModelScope.launch {
            supplierRepository.getSuppliers().collect {
                _suppliers.value = it
            }
        }
    }

    fun addBill(description: String, amount: Double, dueDate: Date, billType: BillType, referenceNumber: String = "", supplierId: String = "", relatedEntryId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val warehouseId = if (relatedEntryId != null) {
                    pendingPurchases.value.find { it.id == relatedEntryId }?.warehouseId
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
                val bill = _bills.value.find { it.id == billId } ?: return@launch
                val supplier = _suppliers.value.find { it.id == bill.supplierId }
                val supplierName = supplier?.name ?: ""
                
                repository.recordPayment(billId, amount)

                // Record in treasury if it's NOT a check (checks come from Bank)
                // Promissory notes (BILL) and others usually come from Treasury cash
                if (bill.billType != BillType.CHECK) {
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

                // Record in bank if it's a check
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
                loadLinkedIds() // Refresh linking availability
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
