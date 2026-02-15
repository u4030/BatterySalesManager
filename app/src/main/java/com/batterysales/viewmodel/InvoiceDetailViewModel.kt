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
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository,
    private val accountingRepository: AccountingRepository,
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

                // Update UI state with invoice details first
                _uiState.update { it.copy(invoice = invoice, isLoading = false) }

                // Then, start listening for real-time payment updates
                paymentRepository.getPaymentsForInvoice(id)
                    .collect { payments ->
                        // Recalculate totals every time payments change
                        val paidAmount = payments.sumOf { it.amount }
                        val remainingAmount = (invoice.totalAmount - paidAmount).coerceAtLeast(0.0)
                        val status = if (remainingAmount <= 0) "paid" else "pending"

                        val updatedInvoice = invoice.copy(
                            paidAmount = paidAmount,
                            remainingAmount = remainingAmount,
                            status = status
                        )

                        // If the invoice status has changed, update it in the DB
                        if (updatedInvoice.paidAmount != invoice.paidAmount || updatedInvoice.status != invoice.status) {
                            invoiceRepository.updateInvoice(updatedInvoice)
                        }

                        _uiState.update {
                            it.copy(
                                invoice = updatedInvoice,
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
                    timestamp = Date()
                )
                val paymentId = paymentRepository.addPayment(payment)

                // Record in treasury
                val invoice = _uiState.value.invoice
                val transaction = Transaction(
                    type = TransactionType.PAYMENT,
                    amount = amount,
                    description = "دفعة فاتورة: ${invoice?.customerName ?: ""} (رقم: ${invoice?.invoiceNumber ?: ""})",
                    relatedId = paymentId, // Use paymentId instead of invoiceId for granular tracking
                    warehouseId = currentInvoice?.warehouseId,
                    paymentMethod = paymentMethod
                )
                accountingRepository.addTransaction(transaction)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to add payment") }
            }
        }
    }

    fun updatePayment(payment: Payment, newAmount: Double) {
        viewModelScope.launch {
            if (newAmount <= 0) return@launch
            try {
                val updatedPayment = payment.copy(amount = newAmount)
                paymentRepository.updatePayment(updatedPayment)

                // Also update treasury
                val invoice = _uiState.value.invoice
                accountingRepository.updateTransactionByRelatedId(
                    relatedId = payment.id,
                    newAmount = newAmount,
                    newDescription = "تعديل دفعة فاتورة: ${invoice?.customerName ?: ""}"
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to update payment") }
            }
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch {
            try {
                paymentRepository.deletePayment(paymentId)
                // Also delete from treasury
                accountingRepository.deleteTransactionsByRelatedId(paymentId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to delete payment") }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
