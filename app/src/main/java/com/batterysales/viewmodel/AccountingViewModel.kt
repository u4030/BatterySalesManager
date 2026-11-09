package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.batterysales.data.models.*
import com.batterysales.data.paging.TransactionPagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AccountingViewModel @Inject constructor(
    private val repository: AccountingRepository,
    private val summaryRepository: SummaryRepository,
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository,
    private val persistentSyncManager: com.batterysales.data.local.PersistentSyncManager
) : ViewModel() {

    private val _balance = MutableStateFlow(0.0)
    val balance = _balance.asStateFlow()

    private val _totalExpenses = MutableStateFlow(0.0)
    val totalExpenses = _totalExpenses.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()

    private val _selectedWarehouseId = MutableStateFlow<String?>(null)
    val selectedWarehouseId = _selectedWarehouseId.asStateFlow()

    private val _selectedPaymentMethod = MutableStateFlow<String?>(null) 
    val selectedPaymentMethod = _selectedPaymentMethod.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: All, 1: Withdrawals
    val selectedTab = _selectedTab.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)

    private val _selectedYear = MutableStateFlow<Int?>(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear = _selectedYear.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)
    private val _isDataLoaded = MutableStateFlow(false)

    // حارس المعاملات المحذوفة أو المعكوسة تفاؤلياً في الذاكرة الحية (0ms) لمنع وميض وتأخر الميتاداتا
    private val suppressedTransactionIds = MutableStateFlow<Set<String>>(emptySet())

    fun suppressTransactionInstantly(txId: String) {
        suppressedTransactionIds.update { it + txId }
        viewModelScope.launch {
            persistentSyncManager.recordPendingOperation(
                com.batterysales.data.local.PendingSyncOperation(
                    operationId = "suppress_tx_${System.currentTimeMillis()}_$txId",
                    type = "TREASURY_SUPPRESS",
                    relatedId = txId,
                    status = "PENDING_SYNC"
                )
            )
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: Flow<PagingData<Transaction>> = combine(
        _selectedWarehouseId, _selectedTab, _selectedPaymentMethod, _startDate, _endDate, refreshTrigger, _isDataLoaded, _searchQuery, suppressedTransactionIds
    ) { args ->
        if (!(args[6] as Boolean)) return@combine null
        Filters(
            warehouseId = args[0] as String?,
            tab = args[1] as Int,
            paymentMethod = args[2] as String?,
            start = args[3] as Long?,
            end = args[4] as Long?
        )
    }.filterNotNull().flatMapLatest { filters ->
        Pager(PagingConfig(pageSize = 20)) {
            TransactionPagingSource(
                repository = repository,
                warehouseId = if (filters.warehouseId == "all") null else filters.warehouseId,
                paymentMethod = if (filters.paymentMethod == "all") null else filters.paymentMethod,
                types = if (filters.tab == 1) listOf(TransactionType.EXPENSE.name, TransactionType.REFUND.name) else null,
                startDate = filters.start,
                endDate = filters.end
            )
        }.flow.map { pagingData ->
            val query = _searchQuery.value
            val suppressed = suppressedTransactionIds.value
            pagingData.filter { trans ->
                !suppressed.contains(trans.id) &&
                (query.isBlank() || trans.description.contains(query, ignoreCase = true) || trans.referenceNumber.contains(query, ignoreCase = true))
            }
        }.cachedIn(viewModelScope)
    }

    private data class Filters(val warehouseId: String?, val tab: Int, val paymentMethod: String?, val start: Long?, val end: Long?)

    init {
        loadInitialData()
        loadData()
        observeFinancialStatus()
        observeSyncRegistryRealtime()
    }

    private fun observeSyncRegistryRealtime() {
        viewModelScope.launch {
            summaryRepository.getSyncRegistryFlow()
                .distinctUntilChangedBy { it.financialVersion }
                .collect {
                    refreshTrigger.value += 1
                    loadBalancesFromSummary()
                    loadTotals()
                }
        }
    }

    private fun observeFinancialStatus() {
        combine(
            summaryRepository.getFinancialStatusFlow(),
            _selectedWarehouseId,
            _selectedPaymentMethod
        ) { status, whId, method ->
            if (whId == null || whId == "all") {
                if (method == "bank") status.globalBankBalance 
                else status.globalCashBalance
            } else {
                val whBalance = status.warehouseBalances[whId]
                if (method == "bank") whBalance?.bankBalance ?: 0.0
                else whBalance?.cashBalance ?: 0.0
            }
        }
        .onEach { _balance.value = it }
        .launchIn(viewModelScope)
    }

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun loadInitialData() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            val isAdmin = user?.role == "admin"
            val allWh = warehouseRepository.getWarehousesOnce()
            
            if (isAdmin) {
                _warehouses.value = allWh.filter { it.isActive }
                _selectedWarehouseId.value = allWh.firstOrNull { it.isActive }?.id
            } else {
                _selectedWarehouseId.value = user?.warehouseId
                _warehouses.value = emptyList()
            }
            
            // --- ELITE STRATEGY: Load balances from summary (1 read) ---
            loadBalancesFromSummary()
            loadTotals()
        }
    }

    private suspend fun loadBalancesFromSummary() {
        try {
            val whId = _selectedWarehouseId.value
            val method = _selectedPaymentMethod.value
            val status = summaryRepository.getFinancialStatus()
            
            if (status != null) {
                if (whId == null || whId == "all") {
                    _balance.value = if (method == "bank") status.globalBankBalance 
                                    else status.globalCashBalance
                } else {
                    val whBalance = status.warehouseBalances[whId]
                    _balance.value = if (method == "bank") whBalance?.bankBalance ?: 0.0
                                    else whBalance?.cashBalance ?: 0.0
                }
            } else {
                // Fallback to heavy calculation if summary is missing
                val balance = repository.getCurrentBalance(
                    warehouseId = if (whId == "all") null else whId,
                    paymentMethod = if (method == "all" || method == null) null else method
                )
                _balance.value = balance
            }
        } catch (e: Exception) {
            Log.e("AccountingViewModel", "Error loading financial summary", e)
        }
    }

    private suspend fun loadTotals() {
        try {
            val whId = _selectedWarehouseId.value
            val method = _selectedPaymentMethod.value
            val start = _startDate.value
            val end = _endDate.value

            val total = repository.getTotalExpenses(
                warehouseId = if (whId == "all" || whId == null) null else whId,
                paymentMethod = if (method == "all" || method == null) null else method,
                startDate = start,
                endDate = end
            )
            _totalExpenses.value = total
        } catch (e: Exception) {
            Log.e("AccountingViewModel", "Error loading totals", e)
        }
    }

    fun loadData(reset: Boolean = false) {
        _isDataLoaded.value = true
        if (reset) refreshTrigger.value += 1
        viewModelScope.launch { 
            loadBalancesFromSummary()
            loadTotals()
        }
    }

    fun onWarehouseSelected(id: String) {
        _selectedWarehouseId.value = id
        _isDataLoaded.value = true // Automatically load for new selection
        viewModelScope.launch { 
            loadBalancesFromSummary()
            loadTotals()
            refreshTrigger.value += 1 
        }
    }

    fun onPaymentMethodSelected(method: String?) {
        _selectedPaymentMethod.value = method
        _isDataLoaded.value = true
        viewModelScope.launch { 
            loadBalancesFromSummary()
            loadTotals()
            refreshTrigger.value += 1
        }
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun onYearSelected(year: Int?) {
        _selectedYear.value = year
        _isDataLoaded.value = true
        refreshTrigger.value += 1
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isNotEmpty()) _isDataLoaded.value = true
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun transferDailyIncomeToMain(warehouseId: String? = null, dateStr: String? = null) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val targetWhId = warehouseId ?: _selectedWarehouseId.value
                val allWh = warehouseRepository.getWarehousesOnce()
                val mainWh = allWh.find { it.isMain } ?: allWh.find { it.name.contains("رئيسي") || it.name.lowercase().contains("main") } ?: allWh.firstOrNull()

                if (targetWhId == null || targetWhId == "all" || targetWhId == mainWh?.id) {
                    _errorMessage.value = "يرجى تحديد مستودع فرعي لترحيل رصيده إلى الخزينة الرئيسية."
                    return@launch
                }

                // 1. فحص مركزي عبر السيرفر للتأكد من اتساق كافة أجهزة البائعين (Server-Driven Gateway Lock)
                val isSyncRegistryClear = repository.checkGlobalSyncRegistryClear(targetWhId, dateStr)
                if (!isSyncRegistryClear || persistentSyncManager.hasLocalPendingOperations()) {
                    _errorMessage.value = "يُحظر الترحيل! توجد عمليات معلقة لدى أجهزة البائعين في هذا المستودع."
                    _isSubmitting.value = false
                    return@launch
                }

                // إتمام الترحيل المتسلسل التاريخي ذرياً
                repository.executeDailyTransferAtomic(targetWhId, dateStr)
                loadData(reset = true)
            } catch (e: Exception) {
                Log.e("AccountingViewModel", "Error transferring daily income to main", e)
                _errorMessage.value = e.localizedMessage ?: "فشل ترحيل الرصيد"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val user = userRepository.getCurrentUser()
                repository.updateTransaction(transaction, editorName = user?.displayName ?: "")
                loadData(reset = true)
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("AccountingViewModel", "Error updating transaction", e)
            } finally { _isSubmitting.value = false }
        }
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
        _isDataLoaded.value = true
        refreshTrigger.value += 1
        viewModelScope.launch { loadTotals() }
    }

    fun addManualTransaction(type: TransactionType, amount: Double, description: String, referenceNumber: String = "", paymentMethod: String = "cash") {
        // --- تعديل تفاؤلي فوري لرصيد الخزينة بـ (0ms) بالدينار الأردني JOD ---
        val delta = if (type == TransactionType.INCOME) amount else -amount
        _balance.update { (it + delta).coerceAtLeast(0.0) }

        viewModelScope.launch {
            _isSubmitting.value = true
            val opId = "acc_tx_${System.currentTimeMillis()}"
            try {
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "TREASURY_TRANSACTION",
                        relatedId = referenceNumber.ifEmpty { opId },
                        status = "PENDING_SYNC"
                    )
                )

                val transaction = Transaction(
                    type = type, amount = amount, description = description, 
                    referenceNumber = referenceNumber, warehouseId = _selectedWarehouseId.value ?: "", 
                    paymentMethod = paymentMethod
                )
                repository.addTransaction(transaction)
                persistentSyncManager.removePendingOperation(opId)
                loadData(reset = true)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _errorMessage.value = e.message
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun deleteTransaction(id: String) {
        // حجب المعاملة فوراً محلياً بـ 0ms وتجاوز ميتاداتا الفايرستور المزدوجة
        suppressTransactionInstantly(id)

        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val user = userRepository.getCurrentUser()
                val isAdmin = user?.role == "admin"
                repository.deleteTransaction(id, forceSystemUpdate = isAdmin)
                loadData(reset = true)
            } catch (e: Exception) {
                suppressedTransactionIds.update { it - id }
                _errorMessage.value = e.message
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
