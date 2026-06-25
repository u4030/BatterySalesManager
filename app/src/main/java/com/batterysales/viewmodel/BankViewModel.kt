package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.batterysales.data.models.*
import com.batterysales.data.paging.BankPagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import android.util.Log
import androidx.paging.filter
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BankViewModel @Inject constructor(
    private val repository: BankRepository,
    private val summaryRepository: SummaryRepository,
    private val accountingRepository: AccountingRepository,
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _balance = MutableStateFlow(0.0)
    val balance = _balance.asStateFlow()

    private val _totalWithdrawals = MutableStateFlow(0.0)
    val totalWithdrawals = _totalWithdrawals.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) 
    val selectedTab = _selectedTab.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)
    private val _isDataLoaded = MutableStateFlow(false)

    val warehouses: StateFlow<List<Warehouse>> = flow {
        emit(warehouseRepository.getWarehousesOnce())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: Flow<PagingData<BankTransaction>> = combine(
        _startDate, _endDate, _selectedTab, _searchQuery, refreshTrigger, _isDataLoaded
    ) { args: Array<Any?> ->
        val isLoaded = args[5] as Boolean
        val query = args[3] as String
        
        if (!isLoaded && query.isEmpty()) null
        else BankFilters(
            start = args[0] as? Long,
            end = args[1] as? Long,
            tab = args[2] as Int,
            query = query,
            refresh = args[4] as Int
        )
    }
        .filterNotNull()
        .flatMapLatest { filters ->
            val filterType = if (filters.tab == 1) BankTransactionType.WITHDRAWAL else null
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                BankPagingSource(repository = repository, startDate = filters.start, endDate = filters.end, type = filterType?.name)
            }.flow.map { pagingData ->
                pagingData.filter { trans ->
                    val typeMatch = filterType == null || trans.type == filterType
                    val queryMatch = filters.query.isBlank() || trans.description.contains(filters.query, ignoreCase = true) || trans.referenceNumber.contains(filters.query, ignoreCase = true)
                    typeMatch && queryMatch
                }
            }
        }.cachedIn(viewModelScope)

    private data class BankFilters(val start: Long?, val end: Long?, val tab: Int, val query: String, val refresh: Int)

    init {
        // Default to current year range
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        _startDate.value = cal.timeInMillis
        cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        _endDate.value = cal.timeInMillis

        viewModelScope.launch { 
            loadBalancesFromSummary()
            loadTotals()
        }
        loadData()
        observeFinancialStatus()
    }

    private fun observeFinancialStatus() {
        summaryRepository.getFinancialStatusFlow()
            .onEach { status ->
                _balance.value = status.globalBankBalance
            }
            .launchIn(viewModelScope)
    }

    private suspend fun loadBalancesFromSummary() {
        try {
            val status = summaryRepository.getFinancialStatus()
            if (status != null) {
                _balance.value = status.globalBankBalance
            } else {
                // Fallback to aggregation
                val balance = repository.getCurrentBalance()
                _balance.value = balance
            }
        } catch (e: Exception) {
            Log.e("BankViewModel", "Error loading bank summary", e)
        }
    }

    private suspend fun loadTotals() {
        try {
            val start = _startDate.value
            val end = _endDate.value
            val total = repository.getTotalWithdrawals(start, end)
            _totalWithdrawals.value = total
        } catch (e: Exception) {
            Log.e("BankViewModel", "Error loading totals", e)
        }
    }

    fun loadData() {
        _isDataLoaded.value = true
        _isLoading.value = true
        viewModelScope.launch {
            try {
                loadBalancesFromSummary()
                loadTotals()
                refreshTrigger.value += 1
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
        loadData()
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isNotEmpty()) _isDataLoaded.value = true
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun updateTransaction(transaction: BankTransaction) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateTransaction(transaction)
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addManualTransaction(type: BankTransactionType, amount: Double, description: String, referenceNumber: String = "", supplierName: String = "", fromTreasury: Boolean = false, warehouseId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val transaction = BankTransaction(type = type, amount = amount, description = description, referenceNumber = referenceNumber, supplierName = supplierName, date = Date())
                val transId = repository.addTransaction(transaction)

                if (type == BankTransactionType.DEPOSIT && fromTreasury) {
                    val mainWhId = warehouseRepository.getWarehousesOnce().find { it.isMain }?.id
                    val targetWhId = mainWhId ?: warehouseId ?: userRepository.getCurrentUser()?.warehouseId
                    
                    if (targetWhId != null) {
                        accountingRepository.addTransaction(Transaction(
                            type = TransactionType.EXPENSE, amount = amount, description = "تغذية رصيد بنك: $description", 
                            referenceNumber = referenceNumber, warehouseId = targetWhId, relatedId = transId, paymentMethod = "cash",
                            isSystemManaged = true
                        ))
                    }
                }
                loadData()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteTransaction(id)
                accountingRepository.deleteTransactionsByRelatedId(id)
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}
 
