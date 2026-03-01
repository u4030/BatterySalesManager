package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.batterysales.data.models.OldBatteryTransaction
import com.batterysales.data.models.OldBatteryTransactionType
import com.batterysales.data.models.Transaction
import com.batterysales.data.models.TransactionType
import com.batterysales.data.paging.OldBatteryPagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.AccountingRepository
import com.batterysales.data.repositories.InvoiceRepository
import com.batterysales.data.repositories.OldBatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import android.util.Log
import com.batterysales.utils.Quadruple
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OldBatteryViewModel @Inject constructor(
    private val repository: OldBatteryRepository,
    private val accountingRepository: AccountingRepository,
    private val invoiceRepository: InvoiceRepository,
    private val userRepository: com.batterysales.data.repositories.UserRepository,
    private val warehouseRepository: com.batterysales.data.repositories.WarehouseRepository
) : ViewModel() {

    private val _summary = MutableStateFlow(Pair(0, 0.0))
    val summary = _summary.asStateFlow()

    private val _warehouseSummary = MutableStateFlow<Map<String, Pair<Int, Double>>>(emptyMap())
    val warehouseSummary = _warehouseSummary.asStateFlow()

    private val _warehouses = MutableStateFlow<List<com.batterysales.data.models.Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()

    private val _isSeller = MutableStateFlow(false)
    val isSeller = _isSeller.asStateFlow()

    private val _userWarehouseId = MutableStateFlow<String?>(null)
    val userWarehouseId = _userWarehouseId.asStateFlow()

    private val _selectedWarehouseId = MutableStateFlow<String?>(null)
    val selectedWarehouseId = _selectedWarehouseId.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: Flow<PagingData<OldBatteryTransaction>> = combine(
        _selectedWarehouseId,
        _startDate,
        _endDate,
        _isSeller,
        _userWarehouseId
    ) { selId, start, end, seller, userWhId ->
        Quadruple(if (seller) userWhId else selId, start, end, seller)
    }.flatMapLatest { (warehouseId, start, end, _) ->
        Pager(PagingConfig(pageSize = 20)) {
            OldBatteryPagingSource(repository, warehouseId, start, end)
        }.flow.cachedIn(viewModelScope)
    }

    private var currentUser: com.batterysales.data.models.User? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isLastPage = MutableStateFlow(false)
    val isLastPage = _isLastPage.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null

    init {
        userRepository.getCurrentUserFlow()
            .onEach { user ->
                currentUser = user
                _isSeller.value = user?.role == "seller"
                _userWarehouseId.value = user?.warehouseId

                // Clear state when user changes
                _summary.value = Pair(0, 0.0)
                _selectedWarehouseId.value = null

                warehouseRepository.getWarehouses().onEach { allWh ->
                    val active = allWh.filter { it.isActive }
                    _warehouses.value = active

                    if (user?.role == "admin" && _selectedWarehouseId.value == null) {
                        active.firstOrNull()?.let {
                            _selectedWarehouseId.value = it.id
                            loadTransactions(reset = true, warehouseId = it.id)
                        }
                    }
                }.launchIn(viewModelScope)

                if (user?.role == "seller") {
                    loadTransactions(reset = true, warehouseId = user.warehouseId)
                }
            }.launchIn(viewModelScope)
    }

    fun loadInitialData() {
        // No-op now as initialization is handled by user flow
    }

    fun loadTransactions(
        reset: Boolean = false,
        warehouseId: String? = _selectedWarehouseId.value,
        startDate: Long? = _startDate.value,
        endDate: Long? = _endDate.value
    ) {
        if (reset) {
            _selectedWarehouseId.value = warehouseId
            _startDate.value = startDate
            _endDate.value = endDate
            _isLoading.value = true
        }

        viewModelScope.launch {
            try {
                val warehouseFilter = if (_isSeller.value) _userWarehouseId.value else warehouseId
                // Load summary via aggregation
                val summ = repository.getStockSummary(warehouseFilter)
                _summary.value = summ

            } catch (e: Exception) {
                Log.e("OldBatteryViewModel", "Error loading transactions", e)
                _errorMessage.value = "خطأ في تحميل البيانات: ${e.message}"
            } finally {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    fun loadData() = loadInitialData()

    fun clearError() {
        _errorMessage.value = null
    }

    private fun calculateSummary(transactions: List<OldBatteryTransaction>): Pair<Int, Double> {
        var totalQty = 0
        var totalAmperes = 0.0
        transactions.forEach {
            when (it.type) {
                OldBatteryTransactionType.INTAKE -> {
                    totalQty += it.quantity
                    totalAmperes += it.totalAmperes
                }
                OldBatteryTransactionType.SALE -> {
                    totalQty -= it.quantity
                    totalAmperes -= it.totalAmperes
                }
                OldBatteryTransactionType.ADJUSTMENT -> {
                    totalQty += it.quantity
                    totalAmperes += it.totalAmperes
                }
            }
        }
        return Pair(totalQty, totalAmperes)
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(id)
                // Sync with invoice is now less direct with Paging,
                // typically we'd fetch the transaction first if needed.
            } catch (e: Exception) {
                Log.e("OldBatteryViewModel", "Error deleting transaction", e)
            }
        }
    }

    fun updateTransaction(transaction: com.batterysales.data.models.OldBatteryTransaction) {
        viewModelScope.launch {
            try {
                repository.updateTransaction(transaction)

                // Sync with invoice if applicable
                transaction.invoiceId?.let { invoiceId ->
                    val invoice = invoiceRepository.getInvoice(invoiceId)
                    if (invoice != null) {
                        val rate = if (invoice.oldBatteriesTotalAmperes > 0) invoice.oldBatteriesValue / invoice.oldBatteriesTotalAmperes else 0.0
                        val newValue = transaction.totalAmperes * rate
                        updateInvoiceScrap(invoiceId, transaction.quantity, transaction.totalAmperes, newValue)
                    }
                }
            } catch (e: Exception) {
                Log.e("OldBatteryViewModel", "Error updating transaction", e)
            }
        }
    }

    private suspend fun updateInvoiceScrap(invoiceId: String, qty: Int, amps: Double, value: Double) {
        val invoice = invoiceRepository.getInvoice(invoiceId) ?: return
        val newTotal = invoice.subtotal - value
        val updatedInvoice = invoice.copy(
            oldBatteriesQuantity = qty,
            oldBatteriesTotalAmperes = amps,
            oldBatteriesValue = value,
            totalAmount = newTotal,
            finalAmount = newTotal,
            remainingAmount = newTotal - invoice.paidAmount,
            status = if (invoice.paidAmount >= newTotal) "paid" else "pending",
            notes = invoice.notes + "\n[تحديث تلقائي: تم تعديل البطاريات القديمة]"
        )
        invoiceRepository.updateInvoice(updatedInvoice)
    }

    fun addManualIntake(quantity: Int, totalAmperes: Double, notes: String, warehouseId: String) {
        viewModelScope.launch {
            try {
                val transaction = com.batterysales.data.models.OldBatteryTransaction(
                    quantity = quantity,
                    warehouseId = warehouseId,
                    totalAmperes = totalAmperes,
                    type = com.batterysales.data.models.OldBatteryTransactionType.INTAKE,
                    date = java.util.Date(),
                    notes = notes,
                    createdByUserName = currentUser?.displayName ?: ""
                )
                repository.addTransaction(transaction)
            } catch (e: Exception) {
                Log.e("OldBatteryViewModel", "Error adding manual intake", e)
            }
        }
    }

    fun sellBatteries(quantity: Int, totalAmperes: Double, amount: Double, warehouseId: String) {
        viewModelScope.launch {
            try {
                val transaction = OldBatteryTransaction(
                    quantity = quantity,
                    warehouseId = warehouseId,
                    totalAmperes = totalAmperes,
                    amount = amount,
                    type = OldBatteryTransactionType.SALE,
                    date = Date(),
                    notes = "بيع بطاريات قديمة",
                    createdByUserName = currentUser?.displayName ?: ""
                )
                repository.addTransaction(transaction)

                // Add to Treasury
                val treasuryTransaction = Transaction(
                    type = TransactionType.INCOME,
                    amount = amount,
                    description = "بيع بطاريات قديمة (سكراب): $quantity حبة",
                    relatedId = null // Manual income in treasury
                )
                accountingRepository.addTransaction(treasuryTransaction)
            } catch (e: Exception) {
                Log.e("OldBatteryViewModel", "Error selling batteries", e)
            }
        }
    }
}
