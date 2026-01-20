package com.batterysales.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.ProductVariantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository
) : ViewModel() {

    val products = mutableStateOf<List<Product>>(emptyList())
    val variants = mutableStateOf<List<ProductVariant>>(emptyList())
    val selectedProduct = mutableStateOf<Product?>(null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        fetchProducts()
    }

    fun fetchProducts() {
        viewModelScope.launch {
            try {
                products.value = productRepository.getProducts().filter { !it.isArchived }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch products: ${e.message}"
            }
        }
    }

    fun selectProduct(product: Product) {
        selectedProduct.value = product
        fetchVariantsForProduct(product.id)
    }

    private fun fetchVariantsForProduct(productId: String) {
        viewModelScope.launch {
            try {
                variants.value = productVariantRepository.getVariantsForProduct(productId).filter { !it.isArchived }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch variants: ${e.message}"
            }
        }
    }

    fun addProduct(name: String, notes: String) {
        viewModelScope.launch {
            try {
                val product = Product(name = name, notes = notes)
                if (product.isValid()) {
                    productRepository.addProduct(product)
                    fetchProducts()
                } else {
                    _errorMessage.value = product.getValidationError()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add product: ${e.message}"
            }
        }
    }

    fun addVariant(capacity: Int, sellingPrice: Double, barcode: String, notes: String) {
        viewModelScope.launch {
            selectedProduct.value?.let { product ->
                try {
                    val variant = ProductVariant(
                        productId = product.id,
                        capacity = capacity,
                        sellingPrice = sellingPrice,
                        barcode = barcode,
                        notes = notes
                    )
                    if (variant.isValid()) {
                        productVariantRepository.addVariant(variant)
                        fetchVariantsForProduct(product.id)
                    } else {
                        _errorMessage.value = variant.getValidationError()
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to add variant: ${e.message}"
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            try {
                if (product.isValid()) {
                    productRepository.updateProduct(product)
                    fetchProducts()
                } else {
                    _errorMessage.value = product.getValidationError()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update product: ${e.message}"
            }
        }
    }

    fun archiveProduct(product: Product) {
        viewModelScope.launch {
            try {
                val archivedProduct = product.copy(isArchived = true)
                productRepository.updateProduct(archivedProduct)
                fetchProducts()
                selectedProduct.value = null // Deselect after archiving
            } catch (e: Exception) {
                _errorMessage.value = "Failed to archive product: ${e.message}"
            }
        }
    }

    fun updateVariant(variant: ProductVariant) {
        viewModelScope.launch {
            try {
                if (variant.isValid()) {
                    productVariantRepository.updateVariant(variant)
                    fetchVariantsForProduct(variant.productId)
                } else {
                    _errorMessage.value = variant.getValidationError()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update variant: ${e.message}"
            }
        }
    }

    fun archiveVariant(variant: ProductVariant) {
        viewModelScope.launch {
            try {
                val archivedVariant = variant.copy(isArchived = true)
                productVariantRepository.updateVariant(archivedVariant)
                fetchVariantsForProduct(variant.productId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to archive variant: ${e.message}"
            }
        }
    }
}
