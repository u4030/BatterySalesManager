package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Invoice
import com.batterysales.data.models.InvoiceItem
import com.batterysales.data.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository
) : ViewModel() {

    private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
    val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

    private val _selectedInvoice = MutableStateFlow<Invoice?>(null)
    val selectedInvoice: StateFlow<Invoice?> = _selectedInvoice.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        loadInvoices()
    }

    fun loadInvoices() {
        viewModelScope.launch {
            _isLoading.value = true
            invoiceRepository.getAllInvoices()
                .onSuccess {
                    _invoices.value = it
                }
                .onFailure {
                    _errorMessage.value = "خطأ في تحميل الفواتير: ${it.message}"
                }
            _isLoading.value = false
        }
    }

    fun getInvoiceById(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            invoiceRepository.getInvoice(id)
                .onSuccess { _selectedInvoice.value = it }
                .onFailure { _errorMessage.value = "الفاتورة غير موجودة" }
            _isLoading.value = false
        }
    }

    fun createInvoice(invoice: Invoice) {
        viewModelScope.launch {
            _isLoading.value = true
            invoiceRepository.createInvoice(invoice)
                .onSuccess {
                    _successMessage.value = "تم إنشاء الفاتورة بنجاح"
                    loadInvoices()
                }
                .onFailure { _errorMessage.value = "فشل في إنشاء الفاتورة" }
            _isLoading.value = false
        }
    }

    fun recordPayment(invoiceId: String, amount: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            invoiceRepository.recordPayment(invoiceId, amount)
                .onSuccess {
                    _successMessage.value = "تم تسجيل الدفعة بنجاح"
                    getInvoiceById(invoiceId)
                    loadInvoices()
                }
                .onFailure { _errorMessage.value = "فشل في تسجيل الدفعة" }
            _isLoading.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
    fun clearSuccess() { _successMessage.value = null }
}
