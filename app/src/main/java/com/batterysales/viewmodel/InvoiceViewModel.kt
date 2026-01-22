package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Invoice
import com.batterysales.data.repositories.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceUiState(
    val invoices: List<Invoice> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceUiState())
    val uiState: StateFlow<InvoiceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            invoiceRepository.getAllInvoices()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load invoices") } }
                .collect { invoices ->
                    _uiState.update { it.copy(invoices = invoices, isLoading = false) }
                }
        }
    }

    fun deleteInvoice(invoiceId: String) {
        viewModelScope.launch {
            try {
                invoiceRepository.deleteInvoice(invoiceId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to delete invoice") }
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
