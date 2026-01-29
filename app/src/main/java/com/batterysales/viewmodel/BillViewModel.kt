package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.batterysales.data.models.BillType
import com.batterysales.data.models.Transaction
import com.batterysales.data.repositories.AccountingRepository
import com.batterysales.data.repositories.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class BillViewModel @Inject constructor(
    private val repository: BillRepository,
    private val accountingRepository: AccountingRepository
) : ViewModel() {

    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills = _bills.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadBills()
    }

    fun loadBills() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _bills.value = repository.getAllBills()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addBill(description: String, amount: Double, dueDate: Date, billType: BillType) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val bill = Bill(
                    description = description,
                    amount = amount,
                    dueDate = dueDate,
                    billType = billType
                )
                repository.addBill(bill)
                loadBills()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun recordPayment(billId: String, amount: Double) {
        viewModelScope.launch {
            try {
                val bill = _bills.value.find { it.id == billId } ?: return@launch
                repository.recordPayment(billId, amount)

                // Record in treasury
                val transaction = Transaction(
                    type = com.batterysales.data.models.TransactionType.EXPENSE,
                    amount = amount,
                    description = "تسديد ${if (amount >= (bill.amount - bill.paidAmount)) "كلي" else "جزئي"} لكمبيالة: ${bill.description}",
                    relatedId = billId
                )
                accountingRepository.addTransaction(transaction)

                loadBills()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteBill(billId: String) {
        viewModelScope.launch {
            try {
                repository.deleteBill(billId)
                // Also delete related treasury transactions
                accountingRepository.deleteTransactionsByRelatedId(billId)
                loadBills()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateBill(bill: Bill) {
        viewModelScope.launch {
            try {
                repository.updateBill(bill)
                // Also update treasury transactions description
                accountingRepository.updateTransactionByRelatedId(
                    relatedId = bill.id,
                    newDescription = "تسديد لكمبيالة: ${bill.description}"
                )
                loadBills()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
