package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Supplier
import com.batterysales.data.repositories.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val supplierRepository: SupplierRepository
) : ViewModel() {

    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers = _suppliers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        loadSuppliers()
    }

    fun loadSuppliers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                supplierRepository.getSuppliers().collect {
                    _suppliers.value = it
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "فشل تحميل الموردين: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun addSupplier(name: String, phone: String, email: String, address: String, yearlyTarget: Double) {
        viewModelScope.launch {
            try {
                val supplier = Supplier(
                    name = name,
                    phone = phone,
                    email = email,
                    address = address,
                    yearlyTarget = yearlyTarget
                )
                supplierRepository.addSupplier(supplier)
            } catch (e: Exception) {
                _error.value = "فشل إضافة المورد: ${e.message}"
            }
        }
    }

    fun updateSupplier(supplier: Supplier) {
        viewModelScope.launch {
            try {
                supplierRepository.updateSupplier(supplier)
            } catch (e: Exception) {
                _error.value = "فشل تحديث المورد: ${e.message}"
            }
        }
    }

    fun deleteSupplier(id: String) {
        viewModelScope.launch {
            try {
                supplierRepository.deleteSupplier(id)
            } catch (e: Exception) {
                _error.value = "فشل حذف المورد: ${e.message}"
            }
        }
    }

    fun resetSupplier(supplier: Supplier) {
        viewModelScope.launch {
            try {
                val updated = supplier.copy(resetDate = java.util.Date())
                supplierRepository.updateSupplier(updated)
            } catch (e: Exception) {
                _error.value = "فشل إعادة ضبط المورد: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
