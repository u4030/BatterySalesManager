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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import android.util.Log
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class BankViewModel @Inject constructor(
    private val repository: BankRepository
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

    private val filterState = MutableStateFlow(Triple<Long?, Long?, Int>(null, null, 0))

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: Flow<PagingData<BankTransaction>> = filterState.flatMapLatest { (start, end, tab) ->
        Pager(PagingConfig(pageSize = 20)) {
            com.batterysales.data.paging.BankPagingSource(
                repository = repository,
                startDate = start,
                endDate = end,
                type = if (tab == 1) com.batterysales.data.models.BankTransactionType.WITHDRAWAL.name else null
            )
        }.flow.cachedIn(viewModelScope)
    }

    private var currentStartDate: Long? = null
    private var currentEndDate: Long? = null

    init {
        // Default to current year
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        currentStartDate = cal.timeInMillis
        cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        currentEndDate = cal.timeInMillis

        loadData(reset = true)
    }

    fun loadData(reset: Boolean = false) {
        if (reset) {
            _isLoading.value = true
            filterState.update { it.copy() }
        }

        viewModelScope.launch {
            try {
                _balance.value = repository.getCurrentBalance(currentEndDate)
                _totalWithdrawals.value = repository.getTotalWithdrawals(currentStartDate, currentEndDate)
                
                _isLoading.value = false
                _isLoadingMore.value = false
            } catch (e: Exception) {
                Log.e("BankViewModel", "Error loading data", e)
                _isLoading.value = false
                _isLoadingMore.value = false
                _errorMessage.value = "خطأ في تحميل البيانات: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        currentStartDate = start
        currentEndDate = end
        filterState.update { it.copy(first = start, second = end) }
        loadData(reset = true)
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
        filterState.update { it.copy(third = index) }
        loadData(reset = true)
    }

    fun addManualTransaction(type: com.batterysales.data.models.BankTransactionType, amount: Double, description: String, referenceNumber: String = "", supplierName: String = "") {
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
                repository.addTransaction(transaction)
                loadData(reset = true)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
