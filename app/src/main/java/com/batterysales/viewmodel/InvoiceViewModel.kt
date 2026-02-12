package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Invoice
import com.batterysales.data.models.Warehouse
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.AccountingRepository
import com.batterysales.data.repositories.InvoiceRepository
import com.batterysales.data.repositories.PaymentRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import com.batterysales.data.repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceUiState(
    val invoices: List<Invoice> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val invoiceToDelete: Invoice? = null,
    val deletionWarningMessage: String = "",
    val selectedWarehouseId: String = "",
    val selectedTab: Int = 0, // 0: All, 1: Pending
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
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceUiState())
    val uiState: StateFlow<InvoiceUiState> = _uiState.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null

    init {
        checkRoleAndLoadWarehouses()
        loadInvoices(reset = true)
    }

    private fun checkRoleAndLoadWarehouses() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            val isAdmin = user?.role == "admin"
            
            warehouseRepository.getWarehouses().collect { warehouses ->
                _uiState.update { state ->
                    val initialWarehouseId = if (isAdmin) {
                        warehouses.firstOrNull()?.id ?: ""
                    } else {
                        user?.warehouseId ?: ""
                    }
                    
                    state.copy(
                        warehouses = warehouses,
                        isAdmin = isAdmin,
                        selectedWarehouseId = initialWarehouseId
                    )
                }
            }
        }
    }

    fun loadInvoices(reset: Boolean = false) {
        if (reset) {
            lastDocument = null
            _uiState.update { it.copy(invoices = emptyList(), isLastPage = false, isLoading = true) }
        }

        if (_uiState.value.isLastPage || _uiState.value.isLoadingMore) return

        viewModelScope.launch {
            try {
                if (!reset) _uiState.update { it.copy(isLoadingMore = true) }

                val state = _uiState.value
                val statusFilter = if (state.selectedTab == 1) "pending" else null

                val result = invoiceRepository.getInvoicesPaginated(
                    warehouseId = state.selectedWarehouseId,
                    status = statusFilter,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    lastDocument = lastDocument,
                    limit = 20
                )

                val newInvoices = result.first
                lastDocument = result.second

                _uiState.update { currentState ->
                    val combinedInvoices = if (reset) newInvoices else currentState.invoices + newInvoices

                    // Filter by search query in memory for now as Firestore doesn't support complex text search
                    val filteredBySearch = if (currentState.searchQuery.isNotBlank()) {
                        combinedInvoices.filter {
                            it.invoiceNumber.contains(currentState.searchQuery, ignoreCase = true) ||
                            it.customerName.contains(currentState.searchQuery, ignoreCase = true)
                        }
                    } else combinedInvoices

                    currentState.copy(
                        invoices = filteredBySearch,
                        isLoading = false,
                        isLoadingMore = false,
                        isLastPage = newInvoices.size < 20
                    )
                }
                
                // Calculate debt (this should also be moved to server-side aggregation later)
                calculateTotalDebt()

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isLoadingMore = false, errorMessage = "Failed to load invoices") }
            }
        }
    }

    private fun calculateTotalDebt() {
        viewModelScope.launch {
            try {
                val warehouseId = _uiState.value.selectedWarehouseId
                if (warehouseId.isNotBlank()) {
                    val debt = invoiceRepository.getTotalDebtForWarehouse(warehouseId)
                    _uiState.update { it.copy(totalDebt = debt) }
                }
            } catch (e: Exception) {
                // Fallback or log error
            }
        }
    }

    fun onWarehouseSelected(warehouseId: String) {
        _uiState.update { it.copy(selectedWarehouseId = warehouseId) }
        loadInvoices(reset = true)
    }

    fun onTabSelected(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
        loadInvoices(reset = true)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        // For search, we might want to reload everything or just filter in-memory if the list is small.
        // Given we are paginating, we should probably reset and load from server if searching is supported there,
        // but since it's not well supported, we stay with current list or reset.
        loadInvoices(reset = true)
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _uiState.update { it.copy(startDate = start, endDate = end) }
        loadInvoices(reset = true)
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(invoiceToDelete = null, deletionWarningMessage = "") }
    }

    fun onConfirmDelete() {
        viewModelScope.launch {
            _uiState.value.invoiceToDelete?.let { invoice ->
                try {
                    // 1. Get related stock entries
                    val saleEntries = stockEntryRepository.getEntriesForInvoice(invoice.id)

                    // 2. Create reversal stock entries
                    val reversalEntries = saleEntries.map {
                        it.copy(
                            id = "", // Let Firestore generate a new ID
                            quantity = -it.quantity, // Reverse the quantity
                            supplier = "Reversal for Invoice ${invoice.id}",
                            invoiceId = null // Clear the invoice ID
                        )
                    }
                    stockEntryRepository.addStockEntries(reversalEntries)

                    // 3. Delete related treasury transactions
                    // Delete for each payment
                    val payments = paymentRepository.getPaymentsForInvoice(invoice.id).first()
                    payments.forEach { payment ->
                        accountingRepository.deleteTransactionsByRelatedId(payment.id)
                    }
                    // Also delete any legacy transactions linked directly to the invoice ID
                    accountingRepository.deleteTransactionsByRelatedId(invoice.id)

                    // 4. Delete the invoice and associated payments
                    invoiceRepository.deleteInvoice(invoice.id)

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
                if (saleEntries.isEmpty()) {
                    // No stock entries to reverse, proceed with normal deletion
                    _uiState.update { it.copy(
                        invoiceToDelete = invoice,
                        deletionWarningMessage = "هل أنت متأكد أنك تريد حذف هذه الفاتورة؟"
                    ) }
                    return@launch
                }

                // Assuming all items in an invoice come from the same warehouse
                val warehouseId = saleEntries.first().warehouseId
                val warehouse = warehouseRepository.getWarehouse(warehouseId)
                val warehouseName = warehouse?.name ?: "مستودع غير معروف"

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
                val updatedInvoice = invoice.copy(customerName = newName, customerPhone = newPhone)
                invoiceRepository.updateInvoice(updatedInvoice)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to update customer info") }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
