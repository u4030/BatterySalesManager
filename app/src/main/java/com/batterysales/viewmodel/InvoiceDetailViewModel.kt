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
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository,
    private val accountingRepository: AccountingRepository,
    private val summaryRepository: com.batterysales.data.repositories.SummaryRepository,
    private val persistentSyncManager: com.batterysales.data.local.PersistentSyncManager,
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
            _uiState.update { it.copy(isSubmitting = true) }
            val opId = "pay_add_${System.currentTimeMillis()}"
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

                // تسجيل فوري كـ PENDING_SYNC مع amountDelta لدعم الظهور الفوري في قيود الخزينة (0ms)
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "TREASURY_TRANSACTION",
                        relatedId = invoiceId,
                        amountDelta = amount,
                        status = "PENDING_SYNC"
                    )
                )

                invoiceRepository.addPayment(invoiceId, payment)
                persistentSyncManager.removePendingOperation(opId)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _uiState.update { it.copy(errorMessage = "فشل إضافة الدفعة: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun updatePayment(payment: Payment, newAmount: Double) {
        viewModelScope.launch {
            if (newAmount <= 0) return@launch
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val updatedPayment = payment.copy(amount = newAmount, paymentDate = Date())
                invoiceRepository.updatePayment(updatedPayment)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تحديث الدفعة: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deletePayment(paymentId: String) {
        val currentPayments = _uiState.value.payments
        val currentInvoice = _uiState.value.invoice
        
        // --- طرد الدفعة تفاؤلياً من الذاكرة الحية وإعادة احتساب الرصيد بـ (0ms) ---
        val targetPayment = currentPayments.find { it.id == paymentId }
        val updatedPayments = currentPayments.filter { it.id != paymentId }
        val newPaidAmount = updatedPayments.sumOf { it.amount }
        val newRemaining = ((currentInvoice?.totalAmount ?: 0.0) - newPaidAmount).coerceAtLeast(0.0)
        val newStatus = when {
            newRemaining <= 0.001 -> "paid"
            newPaidAmount > 0.001 -> "partial"
            else -> "pending"
        }
        val optimisticInvoice = currentInvoice?.copy(
            paidAmount = newPaidAmount,
            remainingAmount = newRemaining,
            status = newStatus
        )

        _uiState.update {
            it.copy(
                payments = updatedPayments,
                invoice = optimisticInvoice ?: it.invoice,
                isSubmitting = true
            )
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val opId = "pay_del_${System.currentTimeMillis()}_$paymentId"
            try {
                // قيد المعاملة العكسية كـ PENDING_SYNC في DataStore لحمايتها من الموت المفاجئ للهاتف
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "PAYMENT_DELETE",
                        relatedId = paymentId,
                        status = "PENDING_SYNC"
                    )
                )

                invoiceRepository.deletePayment(paymentId, invoiceId)
                persistentSyncManager.removePendingOperation(opId)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                // في حال فشل المعاملة، التراجع التفاؤلي لحالة الواجهة السابقة
                _uiState.update { 
                    it.copy(
                        payments = currentPayments,
                        invoice = currentInvoice,
                        errorMessage = "فشل حذف الدفعة: ${e.message}"
                    ) 
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Reverses an invoice fully, restoring stock immediately to SummaryRepository cache
     * and suppressing phantom low-stock alerts locally in 0ms.
     */
    fun cancelInvoiceFully(invoiceId: String, warehouseId: String, variantId: String) {
        val compositeId = "low_stock_${variantId}_${warehouseId}"
        val opId = "suppress_alert_${variantId}_${warehouseId}"
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    // طهر الـ Phantom Alert محلياً وفوراً في 0ms
                    persistentSyncManager.recordPendingOperation(
                        com.batterysales.data.local.PendingSyncOperation(
                            operationId = opId,
                            type = "ALERT_SUPPRESS",
                            relatedId = compositeId,
                            status = "PENDING_SYNC"
                        )
                    )

                    // استعادة المخزون تفاؤلياً في الكاش المركزي المشترك وإزالة التنبيه التفاؤلي
                    persistentSyncManager.removePendingOperation("opt_$compositeId")
                    persistentSyncManager.removePendingOperation("opt_alert_${variantId}_${warehouseId}")
                    val invoice = invoiceRepository.getInvoice(invoiceId)
                    val itemQty = invoice?.items?.find { it.productId == variantId }?.quantity ?: 1
                    summaryRepository.updateCacheOptimisticStock(variantId, warehouseId, itemQty)
                }

                invoiceRepository.cancelInvoiceAtomic(invoiceId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل إلغاء الفاتورة: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun reverseInvoiceFully(invoiceId: String, warehouseId: String, items: List<com.batterysales.data.models.InvoiceItem>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    items.forEach { item ->
                        val variantId = item.productId
                        val compositeAlertId = "low_stock_${variantId}_${warehouseId}"
                        val opId = "suppress_alert_${variantId}_${warehouseId}"

                        // Record ALERT_SUPPRESS in persistent sync manager for instant 0ms eviction
                        persistentSyncManager.recordPendingOperation(
                            com.batterysales.data.local.PendingSyncOperation(
                                operationId = opId,
                                type = "ALERT_SUPPRESS",
                                relatedId = compositeAlertId,
                                status = "PENDING_SYNC"
                            )
                        )

                        // Optimistically restore stock to the central SummaryRepository cache
                        persistentSyncManager.removePendingOperation("opt_$compositeAlertId")
                        persistentSyncManager.removePendingOperation("opt_alert_${variantId}_${warehouseId}")
                        summaryRepository.updateCacheOptimisticStock(variantId, warehouseId, item.quantity)
                    }
                }

                invoiceRepository.cancelInvoiceAtomic(invoiceId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل إلغاء الفاتورة: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
 
