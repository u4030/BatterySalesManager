package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            productRepository.getAllProducts()
                .onSuccess { _products.value = it }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun addProduct(product: Product) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            productRepository.addProduct(product)
                .onSuccess {
                    _successMessage.value = "تم إضافة المنتج بنجاح"
                    loadProducts()
                }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            productRepository.updateProduct(product)
                .onSuccess {
                    _successMessage.value = "تم تحديث المنتج بنجاح"
                    loadProducts()
                }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            productRepository.deleteProduct(productId)
                .onSuccess {
                    _successMessage.value = "تم حذف المنتج بنجاح"
                    loadProducts()
                }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
