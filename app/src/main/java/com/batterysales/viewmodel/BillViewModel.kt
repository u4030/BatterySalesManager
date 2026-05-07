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
    val userRepository: com.batterysales.data.repositories.UserRepository, // Public for UI collectState
    private val supplierRepository: SupplierRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val accountingRepository: AccountingRepository,
    private val bankRepository: BankRepository,
    private val warehouseRepository: com.batterysales.data.repositories.WarehouseRepository
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
        refreshTrigger
    ) { query, _ ->
        query
    }.flatMapLatest { query ->
        Pager(PagingConfig(pageSize = 25)) {
            BillPagingSource(repository, query.ifBlank { null })
        }.flow.cachedIn(viewModelScope)
    }

    val pendingPurchases: StateFlow<List<StockEntry>> = flow {
        // Optimization: Instead of broad listener, fetch recent approved purchases once
        // and only when needed for adding a new bill.
        val entries = stockEntryRepository.getRecentApprovedPurchases(limit = 100)
        val linkedAmounts = repository.getLinkedAmounts() // Efficiently get linked sums

        val grouped = entries.groupBy { it.orderId.ifEmpty { it.id } }
        val result = grouped.mapNotNull { (key, group) ->
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
        emit(result)
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
        warehouseId: String? = null,
        payImmediately: Boolean = false
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
                    warehouseId = finalWarehouseId,
                    status = if (payImmediately) BillStatus.PAID else BillStatus.UNPAID,
                    paidAmount = if (payImmediately) amount else 0.0,
                    paidDate = if (payImmediately) Date() else null
                )
                val billId = repository.addBill(bill)
                
                // تحديث الروابط التلقائية للمورد
                val supplier = _suppliers.value.find { it.id == supplierId }
                repository.autoLinkBillsForSupplier(supplierId, supplier?.resetDate)

                if (payImmediately) {
                    val supplier = _suppliers.value.find { it.id == supplierId }
                    val supplierName = supplier?.name ?: ""

                    val paymentMethod = when (billType) {
                        BillType.VISA -> "visa"
                        BillType.E_WALLET -> "e-wallet"
                        else -> "cash"
                    }

                    val typeLabel = when (billType) {
                        BillType.VISA -> "فيزا"
                        BillType.E_WALLET -> "محفظة"
                        else -> "نقدي"
                    }
                    
                    // Add Treasury Transaction (EXPENSE) - ALWAYS from Main Warehouse Treasury
                    val mainWh = warehouseRepository.getWarehousesOnce().find { it.isMain && it.isActive }
                    val targetWarehouseId = mainWh?.id ?: finalWarehouseId

                    val transaction = Transaction(
                        type = com.batterysales.data.models.TransactionType.EXPENSE,
                        amount = amount,
                        description = "دفع $typeLabel مباشر: $description (المورد: $supplierName)",
                        relatedId = billId,
                        referenceNumber = referenceNumber,
                        warehouseId = targetWarehouseId,
                        paymentMethod = paymentMethod
                    )
                    accountingRepository.addTransaction(transaction)
                }
                
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
                val supplierObj = _suppliers.value.find { it.id == bill.supplierId }
                val supplierName = supplierObj?.name ?: ""
                
                repository.recordPayment(billId, amount)
                
                // تحديث الروابط التلقائية بعد تسجيل الدفعة (لأن الرصيد المتاح من الشيك تغير)
                repository.autoLinkBillsForSupplier(bill.supplierId, supplierObj?.resetDate)

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
                }

                // Record in treasury (Accounting) for all types EXCEPT checks? 
                // Or including checks if they affect the main warehouse treasury?
                // The requirement 7 says "عند التسديد لمورد نقدا من شاشة الكمبيالات لا يتم تسديدها او حسابها في الخزينه"
                // So "Cash" (نقدا) must definitely go to treasury.
                
                if (bill.billType != com.batterysales.data.models.BillType.CHECK) {
                    // Record in treasury (Accounting) for other types
                    val paymentMethod = when (bill.billType) {
                        BillType.VISA -> "visa"
                        BillType.E_WALLET -> "e-wallet"
                        else -> "cash"
                    }

                    val typeLabel = when (bill.billType) {
                        BillType.VISA -> "فيزا"
                        BillType.E_WALLET -> "محفظة"
                        else -> "كمبيالة"
                    }

                    val mainWh = warehouseRepository.getWarehousesOnce().find { it.isMain && it.isActive }
                    val targetWarehouseId = mainWh?.id ?: (bill.warehouseId ?: userRepository.getCurrentUser()?.warehouseId)

                    val transaction = Transaction(
                        type = com.batterysales.data.models.TransactionType.EXPENSE,
                        amount = amount,
                        description = "تسديد ${if (amount >= (bill.amount - bill.paidAmount)) "كلي" else "جزئي"} ل$typeLabel: ${bill.description} (المورد: $supplierName)",
                        relatedId = billId,
                        referenceNumber = bill.referenceNumber,
                        warehouseId = targetWarehouseId,
                        paymentMethod = paymentMethod
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
                val bill = repository.getBill(billId)
                repository.deleteBill(billId)
                
                // تحديث الروابط التلقائية للمورد بعد الحذف
                bill?.let { b ->
                    val supplier = _suppliers.value.find { it.id == b.supplierId }
                    repository.autoLinkBillsForSupplier(b.supplierId, supplier?.resetDate)
                }

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
                
                // تحديث الروابط التلقائية للمورد
                val supplier = _suppliers.value.find { it.id == bill.supplierId }
                repository.autoLinkBillsForSupplier(bill.supplierId, supplier?.resetDate)

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
