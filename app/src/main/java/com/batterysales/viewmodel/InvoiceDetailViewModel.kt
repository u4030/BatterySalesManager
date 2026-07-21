package com.batterysales.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Invoice
import com.batterysales.data.models.Payment
import com.batterysales.data.models.Transaction
import com.batterysales.data.models.TransactionType
import com.batterysales.data.repositories.AccountingRepository
import com.batterysales.data.repositories.InvoiceRepository
import com.batterysales.data.repositories.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class InvoiceDetailUiState(
    val invoice: Invoice? = null,
    val payments: List<Payment> = emptyList(),
    val stockEntries: List<com.batterysales.data.models.StockEntry> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository,
    private val accountingRepository: AccountingRepository,
    private val stockEntryRepository: com.batterysales.data.repositories.StockEntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val invoiceId: String = savedStateHandle.get<String>("invoiceId")!!

    private val _uiState = MutableStateFlow(InvoiceDetailUiState())
    val uiState: StateFlow<InvoiceDetailUiState> = _uiState.asStateFlow()

    init {
        getInvoiceById(invoiceId)
    }

    fun getInvoiceById(id: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                // Fetch the static invoice details once
                val invoice = invoiceRepository.getInvoice(id)
                if (invoice == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Invoice not found") }
                    return@launch
                }

                // Fetch associated stock entries (The "Constraints/Entries" the user asked for)
                val entries = stockEntryRepository.getEntriesForInvoice(id, invoice.invoiceNumber)

                // Update UI state with invoice details first
                _uiState.update { it.copy(invoice = invoice, stockEntries = entries, isLoading = false) }

                // Then, start listening for real-time payment updates
                paymentRepository.getPaymentsForInvoice(id)
                    .collect { payments ->
                        // We also need to fetch the LATEST invoice document because addPayment updates it
                        val freshInvoice = invoiceRepository.getInvoice(id) ?: invoice

                        _uiState.update {
                            it.copy(
                                invoice = freshInvoice,
                                payments = payments,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load details") }
            }
        }
    }

    fun addPayment(amount: Double, paymentMethod: String = "cash") {
        viewModelScope.launch {
            if (amount <= 0) return@launch
            try {
                val currentInvoice = _uiState.value.invoice
                val payment = Payment(
                    invoiceId = invoiceId, 
                    warehouseId = currentInvoice?.warehouseId ?: "",
                    amount = amount, 
                    paymentMethod = paymentMethod,
                    paymentDate = Date(),
                    timestamp = Date()
                )
                invoiceRepository.addPayment(invoiceId, payment)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل إضافة الدفعة: ${e.message}") }
            }
        }
    }

    fun updatePayment(payment: Payment, newAmount: Double) {
        viewModelScope.launch {
            if (newAmount <= 0) return@launch
            try {
                val updatedPayment = payment.copy(amount = newAmount, paymentDate = Date())
                invoiceRepository.updatePayment(updatedPayment)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تحديث الدفعة: ${e.message}") }
            }
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch {
            try {
                invoiceRepository.deletePayment(paymentId, invoiceId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل حذف الدفعة: ${e.message}") }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
 
