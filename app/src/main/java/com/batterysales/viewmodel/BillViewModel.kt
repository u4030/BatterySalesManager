package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class BillViewModel @Inject constructor(
    private val repository: BillRepository,
    private val supplierRepository: SupplierRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val accountingRepository: AccountingRepository,
    private val bankRepository: BankRepository
) : ViewModel() {

    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills = _bills.asStateFlow()

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers = _suppliers.asStateFlow()

    private val _pendingPurchases = MutableStateFlow<List<StockEntry>>(emptyList())
    val pendingPurchases = _pendingPurchases.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        loadBills()
        loadSuppliers()
        loadPendingPurchases()
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

    fun loadSuppliers() {
        viewModelScope.launch {
            supplierRepository.getSuppliers().collect {
                _suppliers.value = it
            }
        }
    }

    fun loadPendingPurchases() {
        viewModelScope.launch {
            stockEntryRepository.getAllStockEntriesFlow().collect { entries ->
                _pendingPurchases.value = entries.filter { it.status == "approved" && it.totalCost > 0 }
            }
        }
    }

    fun addBill(description: String, amount: Double, dueDate: Date, billType: BillType, referenceNumber: String = "", supplierId: String = "", relatedEntryId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val bill = Bill(
                    description = description,
                    amount = amount,
                    dueDate = dueDate,
                    billType = billType,
                    referenceNumber = referenceNumber,
                    supplierId = supplierId,
                    relatedEntryId = relatedEntryId
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
                val supplier = _suppliers.value.find { it.id == bill.supplierId }
                val supplierName = supplier?.name ?: ""

                repository.recordPayment(billId, amount)

                // Record in treasury (always)
                val transaction = Transaction(
                    type = com.batterysales.data.models.TransactionType.EXPENSE,
                    amount = amount,
                    description = "تسديد ${if (amount >= (bill.amount - bill.paidAmount)) "كلي" else "جزئي"} ${if (bill.billType == BillType.CHECK) "لشيك" else "لكمبيالة"}: ${bill.description} (المورد: $supplierName)",
                    relatedId = billId,
                    referenceNumber = bill.referenceNumber
                )
                accountingRepository.addTransaction(transaction)

                // Additionally record in bank if it's a check
                if (bill.billType == com.batterysales.data.models.BillType.CHECK) {
                    val bankTransaction = com.batterysales.data.models.BankTransaction(
                        type = com.batterysales.data.models.BankTransactionType.WITHDRAWAL,
                        amount = amount,
                        description = "تسديد لشيك: ${bill.description} (المورد: $supplierName)",
                        billId = billId,
                        referenceNumber = bill.referenceNumber
                    )
                    bankRepository.addTransaction(bankTransaction)
                }

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
                // Also delete related treasury or bank transactions
                accountingRepository.deleteTransactionsByRelatedId(billId)
                bankRepository.deleteTransactionsByBillId(billId)
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
                // Also update treasury transactions description only
                // Overwriting amount would be wrong if partial payments exist
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
