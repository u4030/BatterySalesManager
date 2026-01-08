package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Expense
import com.batterysales.data.models.Transaction
import com.batterysales.data.repository.AccountingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountingViewModel @Inject constructor(
    private val repository: AccountingRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses = _expenses.asStateFlow()

    private val _balance = MutableStateFlow(0.0)
    val balance = _balance.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getAllTransactions().onSuccess { _transactions.value = it }
                repository.getAllExpenses().onSuccess { _expenses.value = it }
                repository.getCurrentBalance().onSuccess { _balance.value = it }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addExpense(description: String, amount: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            val expense = Expense(description = description, amount = amount)
            repository.addExpense(expense).onSuccess {
                loadData()
            }
            _isLoading.value = false
        }
    }
}
