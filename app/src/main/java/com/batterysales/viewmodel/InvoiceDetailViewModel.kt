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

    fun addPayment(amount: Double) {
        viewModelScope.launch {
            if (amount <= 0) return@launch
            try {
                val payment = Payment(invoiceId = invoiceId, amount = amount, timestamp = Date())
                paymentRepository.addPayment(payment)

                // Record in treasury
                accountingRepository.addTransaction(Transaction(
                    type = TransactionType.INCOME,
                    amount = amount,
                    description = "دفعة فاتورة: ${_uiState.value.invoice?.customerName ?: invoiceId}",
                    relatedId = invoiceId
                ))
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to add payment") }
            }
        }
    }

    fun updatePayment(payment: Payment, newAmount: Double) {
        viewModelScope.launch {
            if (newAmount <= 0) return@launch
            try {
                val diff = newAmount - payment.amount
                val updatedPayment = payment.copy(amount = newAmount)
                paymentRepository.updatePayment(updatedPayment)

                // Record in treasury adjustment
                if (diff != 0.0) {
                    accountingRepository.addTransaction(Transaction(
                        type = if (diff > 0) TransactionType.INCOME else TransactionType.REFUND,
                        amount = kotlin.math.abs(diff),
                        description = "تعديل دفعة فاتورة: ${_uiState.value.invoice?.customerName ?: invoiceId}",
                        relatedId = invoiceId
                    ))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to update payment") }
            }
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch {
            try {
                val payment = _uiState.value.payments.find { it.id == paymentId }
                paymentRepository.deletePayment(paymentId)

                // Record in treasury (Refund/Reversal)
                if (payment != null) {
                    accountingRepository.addTransaction(Transaction(
                        type = TransactionType.REFUND,
                        amount = payment.amount,
                        description = "حذف دفعة فاتورة: ${_uiState.value.invoice?.customerName ?: invoiceId}",
                        relatedId = invoiceId
                    ))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to delete payment") }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
