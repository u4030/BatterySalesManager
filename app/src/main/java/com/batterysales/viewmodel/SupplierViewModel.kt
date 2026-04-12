package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Supplier
import com.batterysales.data.repositories.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val supplierRepository: SupplierRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _allSuppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers = combine(_allSuppliers, _searchQuery) { suppliers, query ->
        if (query.isBlank()) suppliers
        else suppliers.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

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
                    _allSuppliers.value = it
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "فشل تحميل الموردين: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun addSupplier(name: String, phone: String, email: String, address: String, yearlyTarget: Double, target2: Double = 0.0, target3: Double = 0.0) {
        viewModelScope.launch {
            try {
                val supplier = Supplier(
                    name = name,
                    phone = phone,
                    email = email,
                    address = address,
                    yearlyTarget = yearlyTarget,
                    yearlyTarget2 = target2,
                    yearlyTarget3 = target3
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
