package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Expense
import com.batterysales.data.models.Transaction
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.AccountingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountingViewModel @Inject constructor(
    private val repository: AccountingRepository,
    private val userRepository: com.batterysales.data.repositories.UserRepository,
    private val warehouseRepository: com.batterysales.data.repositories.WarehouseRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses = _expenses.asStateFlow()

    private val _balance = MutableStateFlow(0.0)
    val balance = _balance.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _isLastPage = MutableStateFlow(false)
    val isLastPage = _isLastPage.asStateFlow()

    private val _warehouses = MutableStateFlow<List<com.batterysales.data.models.Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()

    private val _selectedWarehouseId = MutableStateFlow<String?>(null)
    val selectedWarehouseId = _selectedWarehouseId.asStateFlow()

    private val _currentUser = MutableStateFlow<com.batterysales.data.models.User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null
    private var currentStartDate: Long? = null
    private var currentEndDate: Long? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _currentUser.value = user

            if (user?.role == "admin") {
                warehouseRepository.getWarehouses().collect { allWh ->
                    val active = allWh.filter { it.isActive }
                    _warehouses.value = active
                    if (_selectedWarehouseId.value == null) {
                        _selectedWarehouseId.value = active.firstOrNull()?.id
                        loadData(reset = true)
                    }
                }
            } else {
                _selectedWarehouseId.value = user?.warehouseId
                loadData(reset = true)
            }
        }
    }

    fun onWarehouseSelected(id: String) {
        _selectedWarehouseId.value = id
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

                val warehouseId = _selectedWarehouseId.value

                val result = repository.getTransactionsPaginated(
                    warehouseId = warehouseId,
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
                    _balance.value = repository.getCurrentBalance(warehouseId)
                    _expenses.value = repository.getAllExpenses() // Expenses are fewer, keep for now
                }
            } finally {
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

    fun addExpense(description: String, amount: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val expense = Expense(
                    description = description,
                    amount = amount,
                    warehouseId = _selectedWarehouseId.value
                )
                repository.addExpense(expense)
                loadData(reset = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addManualTransaction(type: com.batterysales.data.models.TransactionType, amount: Double, description: String, referenceNumber: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val transaction = Transaction(
                    type = type,
                    amount = amount,
                    description = description,
                    referenceNumber = referenceNumber,
                    warehouseId = _selectedWarehouseId.value
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
            try {
                repository.deleteTransaction(id)
                loadData(reset = true)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                repository.updateTransaction(transaction)
                loadData(reset = true)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
