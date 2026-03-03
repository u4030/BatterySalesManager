package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.batterysales.data.models.BankTransaction
import com.batterysales.data.paging.BankPagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.BankRepository
import com.batterysales.utils.Quadruple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import android.util.Log
import androidx.paging.filter
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class BankViewModel @Inject constructor(
    private val repository: BankRepository,
    private val accountingRepository: com.batterysales.data.repositories.AccountingRepository,
    private val userRepository: com.batterysales.data.repositories.UserRepository,
    private val warehouseRepository: com.batterysales.data.repositories.WarehouseRepository
) : ViewModel() {

    private val _balance = MutableStateFlow(0.0)
    val balance = _balance.asStateFlow()

    private val _totalWithdrawals = MutableStateFlow(0.0)
    val totalWithdrawals = _totalWithdrawals.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _isLastPage = MutableStateFlow(false)
    val isLastPage = _isLastPage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: All, 1: Withdrawals
    val selectedTab = _selectedTab.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)

    val warehouses: StateFlow<List<com.batterysales.data.models.Warehouse>> = warehouseRepository.getWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: Flow<PagingData<BankTransaction>> = combine(
        _startDate,
        _endDate,
        _selectedTab,
        _searchQuery,
        refreshTrigger
    ) { start, end, tab, query, refresh ->
        BankFilters(start, end, tab, query, refresh)
    }
        .distinctUntilChanged()
        .flatMapLatest { filters ->
            Log.d("BankViewModel", "Applying filters: tab=${filters.tab}, query=${filters.query}")
            // Explicit type string for Firestore and local matching
            val filterType = if (filters.tab == 1) com.batterysales.data.models.BankTransactionType.WITHDRAWAL else null

            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                com.batterysales.data.paging.BankPagingSource(
                    repository = repository,
                    startDate = filters.start,
                    endDate = filters.end,
                    type = filterType?.name
                )
            }.flow
                .map { pagingData ->
                    pagingData.filter { trans ->
                        // Strict local filtering fallback to guarantee tab correctness
                        val typeMatch = filterType == null || trans.type == filterType
                        val queryMatch = filters.query.isBlank() ||
                                trans.description.contains(filters.query, ignoreCase = true) ||
                                trans.referenceNumber.contains(filters.query, ignoreCase = true) ||
                                trans.supplierName.contains(filters.query, ignoreCase = true) ||
                                trans.notes.contains(filters.query, ignoreCase = true)

                        typeMatch && queryMatch
                    }
                }
        }
        .cachedIn(viewModelScope)

    private data class BankFilters(
        val start: Long?,
        val end: Long?,
        val tab: Int,
        val query: String,
        val refresh: Int
    )

    init {
        // Default to current year
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        _startDate.value = cal.timeInMillis
        cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        _endDate.value = cal.timeInMillis

        loadData()
    }

    fun loadData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val end = _endDate.value
                val start = _startDate.value

                kotlinx.coroutines.coroutineScope {
                    val balanceJob = async { repository.getCurrentBalance(end) }
                    val withdrawalsJob = async { repository.getTotalWithdrawals(start, end) }

                    _balance.value = balanceJob.await()
                    _totalWithdrawals.value = withdrawalsJob.await()
                }
                
                refreshTrigger.value += 1 // Force Paging 3 to reload
            } catch (e: Exception) {
                Log.e("BankViewModel", "Error loading data", e)
                _errorMessage.value = "خطأ في تحميل البيانات: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
        loadData()
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
        loadData()
    }

    private var searchJob: kotlinx.coroutines.Job? = null
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            loadData()
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(id)
                // Also delete related treasury transaction if it exists
                accountingRepository.deleteTransactionsByRelatedId(id)
                loadData()
            } catch (e: Exception) {
                Log.e("BankViewModel", "Error deleting transaction", e)
            }
        }
    }

    fun updateTransaction(transaction: BankTransaction) {
        viewModelScope.launch {
            try {
                // Update the bank transaction
                // Note: We don't have a specific update in repo, but we can use addTransaction with same ID if repo supported it.
                // For now, let's just use the bankRepo.delete + add or if there is an updateTransaction.
                // Assuming we might need an updateTransaction in repository.
                loadData()
            } catch (e: Exception) {
                Log.e("BankViewModel", "Error updating transaction", e)
            }
        }
    }

    fun addManualTransaction(
        type: com.batterysales.data.models.BankTransactionType,
        amount: Double,
        description: String,
        referenceNumber: String = "",
        supplierName: String = "",
        fromTreasury: Boolean = false,
        warehouseId: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val transaction = com.batterysales.data.models.BankTransaction(
                    type = type,
                    amount = amount,
                    description = description,
                    referenceNumber = referenceNumber,
                    supplierName = supplierName,
                    date = java.util.Date()
                )
                val transId = repository.addTransaction(transaction)

                // Requirement: Option to deduct from treasury during deposit
                if (type == com.batterysales.data.models.BankTransactionType.DEPOSIT && fromTreasury) {
                    val user = userRepository.getCurrentUser()
                    val targetWhId = warehouseId ?: user?.warehouseId

                    if (targetWhId != null) {
                        val accountingEntry = com.batterysales.data.models.Transaction(
                            type = com.batterysales.data.models.TransactionType.EXPENSE,
                            amount = amount,
                            description = "إيداع في البنك: $description",
                            referenceNumber = referenceNumber,
                            warehouseId = targetWhId,
                            relatedId = transId,
                            paymentMethod = "cash"
                        )
                        accountingRepository.addTransaction(accountingEntry)
                    }
                }

                loadData()
            } catch (e: Exception) {
                Log.e("BankViewModel", "Error adding manual transaction", e)
                _errorMessage.value = "خطأ في إضافة العملية: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
