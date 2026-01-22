package com.batterysales.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Invoice
import com.batterysales.data.models.Payment
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val invoiceId: String = savedStateHandle.get<String>("invoiceId")!!

    private val _errorMessage = MutableStateFlow<String?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<InvoiceDetailUiState> =
        invoiceRepository.getInvoice(invoiceId)
            .flatMapLatest { invoice ->
                if (invoice == null) {
                    flowOf(InvoiceDetailUiState(isLoading = false, errorMessage = "Invoice not found"))
                } else {
                    paymentRepository.getPaymentsForInvoice(invoiceId)
                        .map { payments ->
                            val paidAmount = payments.sumOf { it.amount }
                            val remainingAmount = (invoice.totalAmount - paidAmount).coerceAtLeast(0.0)
                            val status = if (remainingAmount <= 0) "paid" else "pending"

                            val updatedInvoice = invoice.copy(
                                paidAmount = paidAmount,
                                remainingAmount = remainingAmount,
                                status = status
                            )

                            // If status changes, trigger a DB update.
                            if (updatedInvoice.status != invoice.status) {
                                viewModelScope.launch {
                                    invoiceRepository.updateInvoice(updatedInvoice)
                                }
                            }

                            InvoiceDetailUiState(
                                invoice = updatedInvoice,
                                payments = payments,
                                isLoading = false
                            )
                        }
                }
            }
            .catch { e -> emit(InvoiceDetailUiState(isLoading = false, errorMessage = "Failed to load details: ${e.message}")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InvoiceDetailUiState(isLoading = true))

    fun addPayment(amount: Double) {
        viewModelScope.launch {
            if (amount <= 0) {
                _errorMessage.value = "المبلغ يجب أن يكون أكبر من صفر"
                return@launch
            }
            try {
                val payment = Payment(invoiceId = invoiceId, amount = amount, timestamp = Date())
                paymentRepository.addPayment(payment)
            } catch (e: Exception) {
                 _errorMessage.value = "Failed to add payment"
            }
        }
    }

    fun updatePayment(payment: Payment, newAmount: Double) {
        viewModelScope.launch {
            if (newAmount <= 0) {
                _errorMessage.value = "المبلغ يجب أن يكون أكبر من صفر"
                return@launch
            }
            try {
                val updatedPayment = payment.copy(amount = newAmount)
                paymentRepository.updatePayment(updatedPayment)
            } catch (e: Exception) {
                 _errorMessage.value = "Failed to update payment"
            }
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch {
            try {
                paymentRepository.deletePayment(paymentId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete payment"
            }
        }
    }

    fun onDismissError() {
        _errorMessage.value = null
    }
}
