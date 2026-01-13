package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.batterysales.data.repositories.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class BillViewModel @Inject constructor(
    private val repository: BillRepository
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

    fun addBill(description: String, amount: Double, dueDate: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val bill = Bill(description = description, amount = amount, dueDate = dueDate)
                repository.addBill(bill)
                loadBills()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAsPaid(billId: String) {
        viewModelScope.launch {
            try {
                repository.updateBillStatus(billId, BillStatus.PAID)
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
                loadBills()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
