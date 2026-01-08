package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.repository.AccountingRepository
import com.batterysales.data.repository.InvoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val accountingRepository: AccountingRepository
) : ViewModel() {

    private val _totalSales = MutableStateFlow(0.0)
    val totalSales = _totalSales.asStateFlow()

    private val _totalExpenses = MutableStateFlow(0.0)
    val totalExpenses = _totalExpenses.asStateFlow()

    private val _totalInvoices = MutableStateFlow(0)
    val totalInvoices = _totalInvoices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        generateReport()
    }

    fun generateReport() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                invoiceRepository.getAllInvoices().onSuccess { invoices ->
                    _totalInvoices.value = invoices.size
                    _totalSales.value = invoices.sumOf { it.totalAmount }
                }
                accountingRepository.getAllExpenses().onSuccess { expenses ->
                    _totalExpenses.value = expenses.sumOf { it.amount }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
