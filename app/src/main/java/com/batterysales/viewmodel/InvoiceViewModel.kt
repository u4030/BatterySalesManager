package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Invoice
import com.batterysales.data.repositories.AccountingRepository
import com.batterysales.data.repositories.InvoiceRepository
import com.batterysales.data.repositories.PaymentRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceUiState(
    val invoices: List<Invoice> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val invoiceToDelete: Invoice? = null,
    val deletionWarningMessage: String = "",
    val selectedTab: Int = 0, // 0: All, 1: Pending
    val startDate: Long? = null,
    val endDate: Long? = null,
    val searchQuery: String = ""
)

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val warehouseRepository: WarehouseRepository,
    private val paymentRepository: PaymentRepository,
    private val accountingRepository: AccountingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceUiState())
    val uiState: StateFlow<InvoiceUiState> = _uiState.asStateFlow()

    val invoices: StateFlow<List<Invoice>> = _uiState.map { it.invoices }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val isLoading: StateFlow<Boolean> = _uiState.map { it.isLoading }.stateIn(viewModelScope, SharingStarted.Lazily, true)


    init {
        loadInvoices()
    }

    fun loadInvoices() {
        viewModelScope.launch {
            combine(
                invoiceRepository.getAllInvoices(),
                _uiState
            ) { invoices, state ->
                invoices.filter { invoice ->
                    val matchesTab = if (state.selectedTab == 1) invoice.status == "pending" else true
                    val matchesSearch = invoice.invoiceNumber.contains(state.searchQuery, ignoreCase = true) ||
                            invoice.customerName.contains(state.searchQuery, ignoreCase = true)
                    val matchesDate = if (state.startDate != null && state.endDate != null) {
                        invoice.invoiceDate.time >= state.startDate && invoice.invoiceDate.time <= state.endDate + 86400000
                    } else true

                    matchesTab && matchesSearch && matchesDate
                }
            }
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load invoices") } }
                .collect { filteredInvoices ->
                    _uiState.update { it.copy(invoices = filteredInvoices, isLoading = false) }
                }
        }
    }

    fun onTabSelected(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onDateRangeSelected(start: Long?, end: Long?) {
        _uiState.update { it.copy(startDate = start, endDate = end) }
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
