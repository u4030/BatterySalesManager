package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.batterysales.data.models.Invoice
import com.batterysales.data.models.Warehouse
import com.batterysales.data.paging.InvoicePagingSource
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.paging.filter
import java.util.Calendar
import javax.inject.Inject

data class InvoiceUiState(
    val warehouses: List<Warehouse> = emptyList(),
    val isLoading: Boolean = false, // Stop automatic loading
    val errorMessage: String? = null,
    val invoiceToDelete: Invoice? = null,
    val deletionWarningMessage: String = "",
    val selectedWarehouseId: String = "",
    val selectedTab: Int = 0, // 0: Today, 1: Pending, 2: All
    val totalDebt: Double = 0.0,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val searchQuery: String = "",
    val isAdmin: Boolean = false,
    val isLastPage: Boolean = false,
    val isLoadingMore: Boolean = false
)

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val warehouseRepository: WarehouseRepository,
    private val paymentRepository: PaymentRepository,
    private val accountingRepository: AccountingRepository,
    private val summaryRepository: SummaryRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceUiState())
    val uiState: StateFlow<InvoiceUiState> = _uiState.asStateFlow()

    private val filterState = MutableStateFlow(InvoiceFilters())
    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val invoices: Flow<PagingData<Invoice>> = combine(filterState, refreshTrigger) { f, _ -> f }
        .flatMapLatest { filters ->
            val startOfToday = com.batterysales.utils.DateUtils.getStartOfDay(System.currentTimeMillis())
            val endOfToday = com.batterysales.utils.DateUtils.getEndOfDay(System.currentTimeMillis())

            Pager(PagingConfig(pageSize = 25)) { 
                InvoicePagingSource(
                    repository = invoiceRepository,
                    warehouseId = if (filters.warehouseId == "all" || filters.warehouseId.isBlank()) null else filters.warehouseId,
                    status = if (filters.selectedTab == 1) "pending" else null,
                    startDate = if (filters.selectedTab == 0) startOfToday else filters.startDate,
                    endDate = if (filters.selectedTab == 0) endOfToday else filters.endDate,
                    searchQuery = filters.searchQuery.ifBlank { null },
                    useUpdatedAt = false
                )
            }.flow.cachedIn(viewModelScope)
        }

    init {
        checkRoleAndLoadWarehouses()
    }

    data class InvoiceFilters(
        val warehouseId: String = "",
        val selectedTab: Int = 0,
        val startDate: Long? = null,
        val endDate: Long? = null,
        val searchQuery: String = ""
    )

    private fun checkRoleAndLoadWarehouses() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            val isAdmin = user?.role == "admin"
            val allWh = warehouseRepository.getWarehousesOnce()
            val activeWh = allWh.filter { it.isActive }
            val displayWarehouses = (if (isAdmin) allWh else activeWh).sortedBy { it.name }

            val finalWhId = if (isAdmin) (displayWarehouses.firstOrNull()?.id ?: "") else user?.warehouseId ?: ""

            _uiState.update { it.copy(
                warehouses = displayWarehouses,
                isAdmin = isAdmin,
                selectedWarehouseId = finalWhId
            ) }
            filterState.update { it.copy(warehouseId = finalWhId) }

            // --- ELITE STRATEGY: Load debt from Financial Status Summary ---
            loadDebtFromSummary()
        }
    }

    private suspend fun loadDebtFromSummary() {
        try {
            val whId = _uiState.value.selectedWarehouseId
            val status = summaryRepository.getFinancialStatus()

            val debt = if (status != null) {
                if (whId == "all" || whId.isEmpty()) {
                    status.warehouseBalances.values.sumOf { it.pendingCollection }
                } else {
                    status.warehouseBalances[whId]?.pendingCollection ?: 0.0
                }
            } else {
                // Fallback to repository aggregation
                invoiceRepository.getTotalDebtForWarehouse(if (whId == "all") null else whId)
            }

            _uiState.update { it.copy(totalDebt = debt) }
        } catch (e: Exception) {
            Log.e("InvoiceViewModel", "Error loading debt summary", e)
        }
    }

    fun loadInvoices(reset: Boolean = false) {
        _uiState.update { it.copy(isLoading = true) }
        refreshTrigger.value += 1
        viewModelScope.launch { loadDebtFromSummary() }
    }

    fun onWarehouseSelected(warehouseId: String) {
        if (_uiState.value.selectedWarehouseId == warehouseId) return
        _uiState.update { it.copy(selectedWarehouseId = warehouseId) }
        filterState.update { it.copy(warehouseId = warehouseId) }
    }

    fun onTabSelected(tabIndex: Int) {
        if (_uiState.value.selectedTab == tabIndex) return
        _uiState.update { it.copy(selectedTab = tabIndex, startDate = null, endDate = null) }
        filterState.update { it.copy(selectedTab = tabIndex, startDate = null, endDate = null) }
    }

    private var searchJob: kotlinx.coroutines.Job? = null
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            filterState.update { it.copy(searchQuery = query) }
        }
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _uiState.update { it.copy(startDate = start, endDate = end) }
        filterState.update { it.copy(startDate = start, endDate = end) }
        refreshTrigger.value += 1
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(invoiceToDelete = null, deletionWarningMessage = "") }
    }

    fun onConfirmDelete() {
        viewModelScope.launch {
            _uiState.value.invoiceToDelete?.let { invoice ->
                try {
                    invoiceRepository.deleteInvoice(invoice.id)
                    paymentRepository.getPaymentsForInvoice(invoice.id).first().forEach { payment ->
                        accountingRepository.deleteTransactionsByRelatedId(payment.id)
                    }
                    accountingRepository.deleteTransactionsByRelatedId(invoice.id)
                    loadInvoices(reset = true)
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = "Failed to delete invoice") }
                } finally {
                    onDismissDeleteDialog()
                }
            }
        }
    }

    fun deleteInvoice(invoice: Invoice) {
        viewModelScope.launch {
            try {
                val saleEntries = stockEntryRepository.getEntriesForInvoice(invoice.id)
                val warehouseName = if (saleEntries.isNotEmpty()) {
                    warehouseRepository.getWarehouse(saleEntries.first().warehouseId)?.name ?: "مستودع غير معروف"
                } else "مستودع غير معروف"

                _uiState.update { it.copy(
                    invoiceToDelete = invoice,
                    deletionWarningMessage = "عند حذف هذه الفاتورة، سيتم إرجاع الكمية المباعة إلى مستودع '$warehouseName'. هل تريد المتابعة؟"
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to prepare for deletion") }
            }
        }
    }

    fun updateCustomerInfo(invoice: Invoice, newName: String, newPhone: String) {
        viewModelScope.launch {
            try {
                invoiceRepository.updateInvoice(invoice.copy(customerName = newName, customerPhone = newPhone))
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to update customer info") }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
