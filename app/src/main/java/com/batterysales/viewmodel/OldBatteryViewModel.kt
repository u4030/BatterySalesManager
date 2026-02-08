package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.OldBatteryTransaction
import com.batterysales.data.models.OldBatteryTransactionType
import com.batterysales.data.models.Transaction
import com.batterysales.data.models.TransactionType
import com.batterysales.data.repositories.AccountingRepository
import com.batterysales.data.repositories.InvoiceRepository
import com.batterysales.data.repositories.OldBatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OldBatteryViewModel @Inject constructor(
    private val repository: OldBatteryRepository,
    private val accountingRepository: AccountingRepository,
    private val invoiceRepository: InvoiceRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<OldBatteryTransaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _summary = MutableStateFlow(Pair(0, 0.0))
    val summary = _summary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getAllTransactionsFlow().collect {
                    _transactions.value = it.sortedByDescending { t -> t.date }
                    _summary.value = repository.getStockSummary()
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            try {
                val transaction = _transactions.value.find { it.id == id }
                repository.deleteTransaction(id)

                // Sync with invoice if applicable
                transaction?.invoiceId?.let { invoiceId ->
                    updateInvoiceScrap(invoiceId, 0, 0.0, 0.0)
                }
            } catch (e: Exception) {}
        }
    }

    fun updateTransaction(transaction: com.batterysales.data.models.OldBatteryTransaction) {
        viewModelScope.launch {
            try {
                val oldTransaction = _transactions.value.find { it.id == transaction.id }
                repository.updateTransaction(transaction)

                // Sync with invoice if applicable
                transaction.invoiceId?.let { invoiceId ->
                    val invoice = invoiceRepository.getInvoice(invoiceId)
                    if (invoice != null) {
                        val rate = if (invoice.oldBatteriesTotalAmperes > 0) invoice.oldBatteriesValue / invoice.oldBatteriesTotalAmperes else 0.0
                        val newValue = transaction.totalAmperes * rate
                        updateInvoiceScrap(invoiceId, transaction.quantity, transaction.totalAmperes, newValue)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private suspend fun updateInvoiceScrap(invoiceId: String, qty: Int, amps: Double, value: Double) {
        val invoice = invoiceRepository.getInvoice(invoiceId) ?: return
        val newTotal = invoice.subtotal - value
        val updatedInvoice = invoice.copy(
            oldBatteriesQuantity = qty,
            oldBatteriesTotalAmperes = amps,
            oldBatteriesValue = value,
            totalAmount = newTotal,
            finalAmount = newTotal,
            remainingAmount = newTotal - invoice.paidAmount,
            status = if (invoice.paidAmount >= newTotal) "paid" else "pending",
            notes = invoice.notes + "\n[تحديث تلقائي: تم تعديل البطاريات القديمة]"
        )
        invoiceRepository.updateInvoice(updatedInvoice)
    }

    fun addManualIntake(quantity: Int, totalAmperes: Double, notes: String) {
        viewModelScope.launch {
            try {
                val transaction = com.batterysales.data.models.OldBatteryTransaction(
                    quantity = quantity,
                    totalAmperes = totalAmperes,
                    type = com.batterysales.data.models.OldBatteryTransactionType.INTAKE,
                    date = java.util.Date(),
                    notes = notes
                )
                repository.addTransaction(transaction)
            } catch (e: Exception) {}
        }
    }

    fun sellBatteries(quantity: Int, totalAmperes: Double, amount: Double) {
        viewModelScope.launch {
            try {
                val transaction = OldBatteryTransaction(
                    quantity = quantity,
                    totalAmperes = totalAmperes,
                    amount = amount,
                    type = OldBatteryTransactionType.SALE,
                    date = Date(),
                    notes = "بيع بطاريات قديمة"
                )
                repository.addTransaction(transaction)

                // Add to Treasury
                val treasuryTransaction = Transaction(
                    type = TransactionType.INCOME,
                    amount = amount,
                    description = "بيع بطاريات قديمة (سكراب): $quantity حبة",
                    relatedId = null // Manual income in treasury
                )
                accountingRepository.addTransaction(treasuryTransaction)
            } catch (e: Exception) {}
        }
    }
}
