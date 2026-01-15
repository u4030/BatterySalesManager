package com.batterysales.ui.productmanagement

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.repositories.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    val products = mutableStateOf<List<Product>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        fetchProducts()
    }

    fun fetchProducts() {
        viewModelScope.launch {
            try {
                products.value = productRepository.getProducts()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch products"
            }
        }
    }

    fun addProduct(name: String, capacity: Int, productType: String, barcode: String, sellingPrice: Double) {
        viewModelScope.launch {
            try {
                val product = Product(
                    name = name,
                    capacity = capacity,
                    productType = productType,
                    barcode = barcode,
                    sellingPrice = sellingPrice
                )
                productRepository.addProduct(product)
                fetchProducts()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add product"
            }
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            try {
                productRepository.updateProduct(product)
                fetchProducts()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update product"
            }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            try {
                productRepository.deleteProduct(productId)
                fetchProducts()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete product"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
