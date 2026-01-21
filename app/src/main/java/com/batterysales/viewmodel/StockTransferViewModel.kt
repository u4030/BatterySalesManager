package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockTransferUiState(
    val products: List<Product> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedVariant: ProductVariant? = null,
    val sourceWarehouse: Warehouse? = null,
    val destinationWarehouse: Warehouse? = null,
    val quantity: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isFinished: Boolean = false
)

@HiltViewModel
class StockTransferViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockTransferUiState())
    val uiState: StateFlow<StockTransferUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                productRepository.getProducts(),
                warehouseRepository.getWarehouses()
            ) { products, warehouses ->
                _uiState.update {
                    it.copy(
                        products = products.filter { p -> !p.isArchived },
                        warehouses = warehouses,
                        isLoading = false
                    )
                }
            }.collect()
        }
    }

    fun onProductSelected(product: Product) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProduct = product, selectedVariant = null, variants = emptyList(), isLoading = true) }
            try {
                val variants = productVariantRepository.getVariantsForProduct(product.id).filter { v -> !v.isArchived }
                _uiState.update { it.copy(variants = variants, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to fetch variants", isLoading = false) }
            }
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { it.copy(selectedVariant = variant) }
    }

    fun onSourceWarehouseSelected(warehouse: Warehouse) {
        _uiState.update { it.copy(sourceWarehouse = warehouse) }
    }

    fun onDestinationWarehouseSelected(warehouse: Warehouse) {
        _uiState.update { it.copy(destinationWarehouse = warehouse) }
    }

    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(quantity = quantity) }
    }

    fun onTransferStock() {
        viewModelScope.launch {
            val state = _uiState.value
            val variant = state.selectedVariant
            val source = state.sourceWarehouse
            val dest = state.destinationWarehouse
            val qty = state.quantity.toIntOrNull()

            if (variant == null || source == null || dest == null || qty == null || qty <= 0) {
                _uiState.update { it.copy(errorMessage = "الرجاء ملء جميع الحقول بكمية صحيحة") }
                return@launch
            }

            if (source.id == dest.id) {
                _uiState.update { it.copy(errorMessage = "لا يمكن نقل المخزون إلى نفس المستودع") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }
            try {
                stockEntryRepository.transferStock(
                    productVariantId = variant.id,
                    sourceWarehouseId = source.id,
                    destinationWarehouseId = dest.id,
                    quantity = qty
                )
                _uiState.update { it.copy(isFinished = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to transfer stock: ${e.message}", isLoading = false) }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
