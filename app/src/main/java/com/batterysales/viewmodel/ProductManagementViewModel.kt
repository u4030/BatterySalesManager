package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse
import com.batterysales.data.models.Supplier
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.WarehouseRepository
import com.batterysales.data.repositories.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductManagementUiState(
    val products: List<Product> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val selectedProduct: Product? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val supplierRepository: SupplierRepository
) : ViewModel() {

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _barcodeFilter = MutableStateFlow("")

    val uiState: StateFlow<ProductManagementUiState> = combine(
        combine(
            productRepository.getProducts(),
            productVariantRepository.getAllVariantsFlow(),
            _barcodeFilter
        ) { p, v, b -> Triple(p, v, b) },
        _selectedProduct,
        _selectedProduct.flatMapLatest { product ->
            if (product == null) flowOf(emptyList()) else productVariantRepository.getVariantsForProductFlow(product.id)
        },
        combine(
            warehouseRepository.getWarehouses(),
            supplierRepository.getSuppliers(),
            _isLoading
        ) { w, s, l -> Triple(w, s, l) }
    ) { firstTriple, selectedProduct, variants, secondTriple ->
        val (products, allVariants, barcodeFilter) = firstTriple
        val (warehouses, suppliers, isLoading) = secondTriple

        val filteredProducts = if (barcodeFilter.isBlank()) {
            products.filter { !it.archived }
        } else {
            val productIdsWithMatchingBarcode = allVariants
                .filter { it.barcode.contains(barcodeFilter, ignoreCase = true) }
                .map { it.productId }
                .toSet()
            products.filter { !it.archived && (it.id in productIdsWithMatchingBarcode || it.name.contains(barcodeFilter, ignoreCase = true)) }
        }

        ProductManagementUiState(
            products = filteredProducts,
            selectedProduct = selectedProduct,
            variants = variants.filter { !it.archived },
            warehouses = warehouses,
            suppliers = suppliers,
            isLoading = isLoading,
            errorMessage = _errorMessage.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductManagementUiState(isLoading = true))

    fun onBarcodeFilterChanged(query: String) {
        _barcodeFilter.value = query
    }

    fun generateUniqueBarcode(): String {
        return System.currentTimeMillis().toString()
    }


    fun selectProduct(product: Product) {
        _selectedProduct.value = product
    }

    fun addProduct(name: String, supplierId: String) {
        viewModelScope.launch {
            try {
                val existing = productRepository.getProductsOnce()
                if (existing.any { it.name.equals(name, ignoreCase = true) && !it.archived }) {
                    _errorMessage.value = "هذا المنتج موجود مسبقاً"
                    return@launch
                }

                val product = Product(name = name, supplierId = supplierId)
                if (product.isValid()) {
                    productRepository.addProduct(product)
                } else {
                    _errorMessage.value = product.getValidationError()
                }
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error adding product", e)
                _errorMessage.value = "Failed to add product: ${e.message}"
            }
        }
    }

    fun addVariant(capacity: Int, sellingPrice: Double, barcode: String, minQuantity: Int, minQuantities: Map<String, Int>, specification: String) {
        viewModelScope.launch {
            _selectedProduct.value?.let { product ->
                try {
                    val existing = productVariantRepository.getVariantsForProduct(product.id)
                    if (existing.any { it.capacity == capacity && it.specification.equals(specification, ignoreCase = true) && !it.archived }) {
                        _errorMessage.value = "هذه السعة والمواصفة موجودة مسبقاً لهذا المنتج"
                        return@launch
                    }

                    val variant = ProductVariant(
                        productId = product.id,
                        capacity = capacity,
                        sellingPrice = sellingPrice,
                        barcode = barcode,
                        minQuantity = minQuantity,
                        minQuantities = minQuantities,
                        specification = specification
                    )
                    if (variant.isValid()) {
                        productVariantRepository.addVariant(variant)
                    } else {
                        _errorMessage.value = variant.getValidationError()
                    }
                } catch (e: Exception) {
                    Log.e("ProductMgmtVM", "Error adding variant", e)
                    _errorMessage.value = "Failed to add variant: ${e.message}"
                }
            }
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            try {
                val existing = productRepository.getProductsOnce()
                if (existing.any { it.name.equals(product.name, ignoreCase = true) && it.id != product.id && !it.archived }) {
                    _errorMessage.value = "يوجد منتج آخر بنفس هذا الاسم"
                    return@launch
                }

                if (product.isValid()) {
                    productRepository.updateProduct(product)
                } else {
                    _errorMessage.value = product.getValidationError()
                }
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error updating product", e)
                _errorMessage.value = "Failed to update product: ${e.message}"
            }
        }
    }

    fun archiveProduct(product: Product) {
        viewModelScope.launch {
            try {
                val archivedProduct = product.copy(archived = true)
                productRepository.updateProduct(archivedProduct)
                _selectedProduct.value = null // Deselect after archiving
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error archiving product", e)
                _errorMessage.value = "Failed to archive product: ${e.message}"
            }
        }
    }

    fun updateVariant(variant: ProductVariant) {
        viewModelScope.launch {
            try {
                val existing = productVariantRepository.getVariantsForProduct(variant.productId)
                if (existing.any { it.capacity == variant.capacity && it.specification.equals(variant.specification, ignoreCase = true) && it.id != variant.id && !it.archived }) {
                    _errorMessage.value = "هذه السعة والمواصفة موجودة مسبقاً لهذا المنتج"
                    return@launch
                }

                if (variant.isValid()) {
                    productVariantRepository.updateVariant(variant)
                } else {
                    _errorMessage.value = variant.getValidationError()
                }
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error updating variant", e)
                _errorMessage.value = "Failed to update variant: ${e.message}"
            }
        }
    }

    fun archiveVariant(variant: ProductVariant) {
        viewModelScope.launch {
            try {
                val archivedVariant = variant.copy(archived = true)
                productVariantRepository.updateVariant(archivedVariant)
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error archiving variant", e)
                _errorMessage.value = "Failed to archive variant: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
