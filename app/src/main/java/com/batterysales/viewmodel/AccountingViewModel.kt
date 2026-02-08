package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Expense
import com.batterysales.data.models.Transaction
import com.batterysales.data.repositories.AccountingRepository
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
                _transactions.value = repository.getAllTransactions()
                _expenses.value = repository.getAllExpenses()
                _balance.value = repository.getCurrentBalance()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addExpense(description: String, amount: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val expense = Expense(description = description, amount = amount)
                repository.addExpense(expense)
                loadData()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addManualTransaction(type: com.batterysales.data.models.TransactionType, amount: Double, description: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val transaction = Transaction(
                    type = type,
                    amount = amount,
                    description = description
                )
                repository.addTransaction(transaction)
                loadData()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(id)
                loadData()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                repository.updateTransaction(transaction)
                loadData()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
