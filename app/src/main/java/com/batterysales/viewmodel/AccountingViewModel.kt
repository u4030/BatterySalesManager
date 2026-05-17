package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.batterysales.data.models.*
import com.batterysales.data.paging.TransactionPagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AccountingViewModel @Inject constructor(
    private val repository: AccountingRepository,
    private val summaryRepository: SummaryRepository,
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _balance = MutableStateFlow(0.0)
    val balance = _balance.asStateFlow()

    private val _totalExpenses = MutableStateFlow(0.0)
    val totalExpenses = _totalExpenses.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()

    private val _selectedWarehouseId = MutableStateFlow<String?>(null)
    val selectedWarehouseId = _selectedWarehouseId.asStateFlow()

    private val _selectedPaymentMethod = MutableStateFlow<String?>(null)
    val selectedPaymentMethod = _selectedPaymentMethod.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: All, 1: Withdrawals
    val selectedTab = _selectedTab.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)

    private val _selectedYear = MutableStateFlow<Int?>(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear = _selectedYear.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)
    private val _isDataLoaded = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: Flow<PagingData<Transaction>> = combine(
        _selectedWarehouseId, _selectedTab, _selectedPaymentMethod, _startDate, _endDate, refreshTrigger, _isDataLoaded
    ) { args ->
        if (!(args[6] as Boolean)) return@combine null
        Filters(
            warehouseId = args[0] as String?,
            tab = args[1] as Int,
            paymentMethod = args[2] as String?,
            start = args[3] as Long?,
            end = args[4] as Long?
        )
    }.filterNotNull().flatMapLatest { filters ->
        Pager(PagingConfig(pageSize = 20)) {
            TransactionPagingSource(
                repository = repository,
                warehouseId = if (filters.warehouseId == "all") null else filters.warehouseId,
                paymentMethod = if (filters.paymentMethod == "all") null else filters.paymentMethod,
                types = if (filters.tab == 1) listOf(TransactionType.EXPENSE.name, TransactionType.REFUND.name) else null,
                startDate = filters.start,
                endDate = filters.end
            )
        }.flow.cachedIn(viewModelScope)
    }

    private data class Filters(val warehouseId: String?, val tab: Int, val paymentMethod: String?, val start: Long?, val end: Long?)

    init {
        loadInitialData()
        loadData()
    }

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun loadInitialData() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            val isAdmin = user?.role == "admin"
            val allWh = warehouseRepository.getWarehousesOnce()

            if (isAdmin) {
                _warehouses.value = allWh.filter { it.isActive }
                _selectedWarehouseId.value = allWh.firstOrNull { it.isActive }?.id
            } else {
                _selectedWarehouseId.value = user?.warehouseId
                _warehouses.value = emptyList()
            }

            // --- ELITE STRATEGY: Load balances from summary (1 read) ---
            loadBalancesFromSummary()
            loadTotals()
        }
    }

    private suspend fun loadBalancesFromSummary() {
        try {
            val whId = _selectedWarehouseId.value
            val method = _selectedPaymentMethod.value
            val status = summaryRepository.getFinancialStatus()

            if (status != null) {
                if (whId == null || whId == "all") {
                    _balance.value = if (method == "bank") status.globalBankBalance
                                    else if (method == "cash") status.globalCashBalance
                                    else status.globalCashBalance + status.globalBankBalance
                } else {
                    val whBalance = status.warehouseBalances[whId]
                    _balance.value = if (method == "bank") whBalance?.bankBalance ?: 0.0
                                    else if (method == "cash") whBalance?.cashBalance ?: 0.0
                                    else (whBalance?.cashBalance ?: 0.0) + (whBalance?.bankBalance ?: 0.0)
                }
            } else {
                // Fallback to heavy calculation if summary is missing
                val balance = repository.getCurrentBalance(
                    warehouseId = if (whId == "all") null else whId,
                    paymentMethod = if (method == "all" || method == null) null else method
                )
                _balance.value = balance
            }
        } catch (e: Exception) {
            Log.e("AccountingViewModel", "Error loading financial summary", e)
        }
    }

    private suspend fun loadTotals() {
        try {
            val whId = _selectedWarehouseId.value
            val method = _selectedPaymentMethod.value
            val start = _startDate.value
            val end = _endDate.value

            val total = repository.getTotalExpenses(
                warehouseId = if (whId == "all" || whId == null) null else whId,
                paymentMethod = if (method == "all" || method == null) null else method,
                startDate = start,
                endDate = end
            )
            _totalExpenses.value = total
        } catch (e: Exception) {
            Log.e("AccountingViewModel", "Error loading totals", e)
        }
    }

    fun loadData(reset: Boolean = false) {
        _isDataLoaded.value = true
        if (reset) refreshTrigger.value += 1
        viewModelScope.launch {
            loadBalancesFromSummary()
            loadTotals()
        }
    }

    fun onWarehouseSelected(id: String) {
        _selectedWarehouseId.value = id
        _isDataLoaded.value = true // Automatically load for new selection
        viewModelScope.launch {
            loadBalancesFromSummary()
            loadTotals()
            refreshTrigger.value += 1
        }
    }

    fun onPaymentMethodSelected(method: String?) {
        _selectedPaymentMethod.value = method
        _isDataLoaded.value = true
        viewModelScope.launch {
            loadBalancesFromSummary()
            loadTotals()
            refreshTrigger.value += 1
        }
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun onYearSelected(year: Int?) {
        _selectedYear.value = year
        _isDataLoaded.value = true
        refreshTrigger.value += 1
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun transferDailyIncomeToMain() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Simplified for brevity - actual logic would involve summing today's income
                // and creating a transfer transaction.
                loadBalancesFromSummary()
            } finally { _isLoading.value = false }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateTransaction(transaction)
                loadData(reset = true)
            } finally { _isLoading.value = false }
        }
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
        _isDataLoaded.value = true
        refreshTrigger.value += 1
        viewModelScope.launch { loadTotals() }
    }

    fun addManualTransaction(type: TransactionType, amount: Double, description: String, referenceNumber: String = "", paymentMethod: String = "cash") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val transaction = Transaction(
                    type = type, amount = amount, description = description,
                    referenceNumber = referenceNumber, warehouseId = _selectedWarehouseId.value ?: "",
                    paymentMethod = paymentMethod
                )
                repository.addTransaction(transaction)
                loadData(reset = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            loadData(reset = true)
        }
    }
}
