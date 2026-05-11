package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.UserRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
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
    val isSourceWarehouseFixed: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isFinished: Boolean = false
)

@HiltViewModel
class StockTransferViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockTransferUiState())
    val uiState: StateFlow<StockTransferUiState> = _uiState.asStateFlow()

    private var currentUser: com.batterysales.data.models.User? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = userRepository.getCurrentUser()
                currentUser = user

                // Use one-time fetches for setup to avoid continuous background reads
                val products = productRepository.getProductsOnce().filter { !it.archived }.sortedBy { it.name }
                val warehouses = warehouseRepository.getWarehousesOnce().filter { it.isActive }

                _uiState.update {
                    it.copy(
                        products = products,
                        warehouses = warehouses,
                        sourceWarehouse = if (user?.role == "seller") warehouses.find { w -> w.id == user.warehouseId } else null,
                        isSourceWarehouseFixed = user?.role == "seller",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Init error", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "خطأ في تحميل البيانات") }
            }
        }
    }

    fun onProductSelected(product: Product) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProduct = product, selectedVariant = null, variants = emptyList(), isLoading = true, quantity = "") }
            try {
                // Targeted fetch for product variants
                val variants = productVariantRepository.getVariantsForProduct(product.id)
                    .filter { !it.archived }
                    .sortedBy { it.capacity }
                _uiState.update { it.copy(variants = variants, isLoading = false) }
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Error fetching variants", e)
                _uiState.update { it.copy(errorMessage = "فشل تحميل السعات", isLoading = false) }
            }
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { it.copy(selectedVariant = variant) }
    }

    fun getStockForVariant(variant: ProductVariant?, warehouseId: String?): Int {
        if (variant == null || warehouseId == null) return 0
        return variant.currentStock?.get(warehouseId) ?: 0
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

            val available = getStockForVariant(variant, source.id)
            if (qty > available) {
                _uiState.update { it.copy(errorMessage = "الكمية المطلوبة أكبر من المتوفر في المستودع ($available)") }
                return@launch
            }

            _uiState.update { it.copy(isSubmitting = true) }
            try {
                if (!source.isActive || !dest.isActive) {
                    _uiState.update { it.copy(errorMessage = "عذراً، أحد المستودعات المختارة متوقف حالياً.", isSubmitting = false) }
                    return@launch
                }

                stockEntryRepository.transferStock(
                    productVariantId = variant.id,
                    productName = state.selectedProduct?.name ?: "",
                    capacity = variant.capacity,
                    sourceWarehouseId = source.id,
                    destinationWarehouseId = dest.id,
                    quantity = qty,
                    status = if (currentUser?.role == "seller") "pending" else "approved",
                    createdBy = currentUser?.id ?: "",
                    createdByUserName = currentUser?.displayName ?: ""
                )
                _uiState.update { it.copy(isFinished = true, isSubmitting = false) }
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Transfer error", e)
                _uiState.update { it.copy(errorMessage = "فشل النقل: ${e.message}", isSubmitting = false) }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // High Performance: Directly fetch variant by barcode (one-time fetch)
                val variant = productVariantRepository.getVariantByBarcode(barcode)
                if (variant != null && !variant.archived) {
                    val product = productRepository.getProduct(variant.productId)
                    if (product != null) {
                        onProductSelected(product)
                        onVariantSelected(variant)
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "المنتج غير موجود") }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "لم يتم العثور على الباركود") }
                }
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Barcode error", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "خطأ في البحث") }
            }
        }
    }
}
