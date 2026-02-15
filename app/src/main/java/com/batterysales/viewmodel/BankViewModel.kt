package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.BankTransaction
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.BankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BankViewModel @Inject constructor(
    private val repository: BankRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<BankTransaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

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

    private var lastDocument: DocumentSnapshot? = null
    private var currentStartDate: Long? = null
    private var currentEndDate: Long? = null

    init {
        loadData(reset = true)
    }

    fun loadData(reset: Boolean = false) {
        if (reset) {
            lastDocument = null
            _transactions.value = emptyList()
            _isLastPage.value = false
            _isLoading.value = true
        }

        if (_isLastPage.value || _isLoadingMore.value) return

        viewModelScope.launch {
            try {
                if (!reset) _isLoadingMore.value = true

                val result = repository.getTransactionsPaginated(
                    startDate = currentStartDate,
                    endDate = currentEndDate,
                    lastDocument = lastDocument,
                    limit = 20
                )

                val newTransactions = result.first
                lastDocument = result.second

                _transactions.update { current -> if (reset) newTransactions else current + newTransactions }
                _isLastPage.value = newTransactions.size < 20

                if (reset) {
                    _balance.value = repository.getCurrentBalance(currentEndDate)
                    _totalWithdrawals.value = repository.getTotalWithdrawals(currentStartDate, currentEndDate)
                }

                _isLoading.value = false
                _isLoadingMore.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        currentStartDate = start
        currentEndDate = end
        loadData(reset = true)
    }

    fun addManualTransaction(type: com.batterysales.data.models.BankTransactionType, amount: Double, description: String, referenceNumber: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val transaction = com.batterysales.data.models.BankTransaction(
                    type = type,
                    amount = amount,
                    description = description,
                    referenceNumber = referenceNumber,
                    date = java.util.Date()
                )
                repository.addTransaction(transaction)
                // Flow will auto-update UI
            } finally {
                _isLoading.value = false
            }
        }
    }
}
