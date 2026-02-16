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
import android.util.Log
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.*
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

    private val _totalExpenses = MutableStateFlow(0.0)
    val totalExpenses = _totalExpenses.asStateFlow()

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

    private val _selectedPaymentMethod = MutableStateFlow<String?>(null) // null means "All"
    val selectedPaymentMethod = _selectedPaymentMethod.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: All, 1: Withdrawals
    val selectedTab = _selectedTab.asStateFlow()

    private val _selectedYear = MutableStateFlow<Int?>(null)
    val selectedYear = _selectedYear.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null
    private var currentStartDate: Long? = null
    private var currentEndDate: Long? = null

    init {
        // Default to current year
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        _selectedYear.value = year

        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        currentStartDate = cal.timeInMillis
        cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        currentEndDate = cal.timeInMillis

        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _currentUser.value = user
            
            if (user?.role == "admin") {
                warehouseRepository.getWarehouses().onEach { allWh ->
                    val active = allWh.filter { it.isActive }
                    _warehouses.value = active
                    if (_selectedWarehouseId.value == null) {
                        _selectedWarehouseId.value = active.firstOrNull()?.id
                        loadData(reset = true)
                    }
                }.launchIn(viewModelScope)
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

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
        loadData(reset = true)
    }

    fun onPaymentMethodSelected(method: String?) {
        _selectedPaymentMethod.value = method
        loadData(reset = true)
    }

    fun onYearSelected(year: Int?) {
        _selectedYear.value = year
        if (year != null) {
            val cal = Calendar.getInstance()
            cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
            currentStartDate = cal.timeInMillis
            cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
            currentEndDate = cal.timeInMillis
        } else {
            currentStartDate = null
            currentEndDate = null
        }
        loadData(reset = true)
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

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
                val paymentMethod = _selectedPaymentMethod.value
                val typesFilter = if (_selectedTab.value == 1) {
                    listOf(com.batterysales.data.models.TransactionType.EXPENSE.name, com.batterysales.data.models.TransactionType.REFUND.name)
                } else null
                
                if (reset) {
                    coroutineScope {
                        val balanceJob = async { repository.getCurrentBalance(warehouseId, paymentMethod, currentEndDate) }
                        val totalExpensesJob = async { repository.getTotalExpenses(warehouseId, paymentMethod, currentStartDate, currentEndDate) }
                        val expensesJob = async { repository.getAllExpenses() }

                        _balance.value = balanceJob.await()
                        _totalExpenses.value = totalExpensesJob.await()
                        _expenses.value = expensesJob.await()
                    }
                }

                val result = repository.getTransactionsPaginated(
                    warehouseId = warehouseId,
                    paymentMethod = paymentMethod,
                    types = typesFilter,
                    startDate = currentStartDate,
                    endDate = currentEndDate,
                    lastDocument = lastDocument,
                    limit = 20
                )

                val newTransactions = result.first
                lastDocument = result.second

                _transactions.update { current -> if (reset) newTransactions else current + newTransactions }
                _isLastPage.value = newTransactions.size < 20
            } catch (e: Exception) {
                Log.e("AccountingViewModel", "Error loading data", e)
                _errorMessage.value = "خطأ في تحميل البيانات: ${e.message}"
            } finally {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        currentStartDate = start
        currentEndDate = end
        loadData(reset = true)
    }

    fun addExpense(description: String, amount: Double, paymentMethod: String = "cash") {
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

    fun addManualTransaction(
        type: com.batterysales.data.models.TransactionType, 
        amount: Double, 
        description: String, 
        referenceNumber: String = "",
        paymentMethod: String = "cash"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (type == com.batterysales.data.models.TransactionType.EXPENSE) {
                    val expense = Expense(
                        description = description,
                        amount = amount,
                        warehouseId = _selectedWarehouseId.value,
                        paymentMethod = paymentMethod
                    )
                    repository.addExpense(expense)
                } else {
                    val transaction = Transaction(
                        type = type,
                        amount = amount,
                        description = description,
                        referenceNumber = referenceNumber,
                        warehouseId = _selectedWarehouseId.value,
                        paymentMethod = paymentMethod
                    )
                    repository.addTransaction(transaction)
                }
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
                Log.e("AccountingViewModel", "Error deleting transaction", e)
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                repository.updateTransaction(transaction)
                loadData(reset = true)
            } catch (e: Exception) {
                Log.e("AccountingViewModel", "Error updating transaction", e)
            }
        }
    }
}
