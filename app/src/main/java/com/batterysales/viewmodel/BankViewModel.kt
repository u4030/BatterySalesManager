package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.BankTransaction
import com.batterysales.data.repositories.BankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getAllTransactionsFlow().collect {
                    _transactions.value = it.sortedByDescending { t -> t.date }
                    _balance.value = repository.getCurrentBalance()
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
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
