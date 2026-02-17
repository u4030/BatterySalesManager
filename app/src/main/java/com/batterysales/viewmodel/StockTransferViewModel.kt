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
    val stockLevels: Map<Pair<String, String>, Int> = emptyMap(),
    val selectedProduct: Product? = null,
    val selectedVariant: ProductVariant? = null,
    val sourceWarehouse: Warehouse? = null,
    val destinationWarehouse: Warehouse? = null,
    val quantity: String = "",
    val isSourceWarehouseFixed: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = userRepository.getCurrentUser()
            currentUser = user

            combine(
                productRepository.getProducts(),
                warehouseRepository.getWarehouses(),
                stockEntryRepository.getAllStockEntriesFlow()
            ) { products, warehouses, stockEntries ->
                val approvedEntries = stockEntries.filter { it.status == "approved" }
                val stockMap = mutableMapOf<Pair<String, String>, Int>()
                for (entry in approvedEntries) {
                    val key = Pair(entry.productVariantId, entry.warehouseId)
                    stockMap[key] = (stockMap[key] ?: 0) + entry.quantity
                }

                _uiState.update {
                    it.copy(
                        products = products.filter { p -> !p.archived },
                        warehouses = warehouses.filter { w -> w.isActive },
                        sourceWarehouse = if (user?.role == "seller") warehouses.find { w -> w.id == user.warehouseId } else it.sourceWarehouse,
                        isSourceWarehouseFixed = user?.role == "seller",
                        stockLevels = stockMap,
                        isLoading = false
                    )
                }
            }.collect()
        }
    }

    fun onProductSelected(product: Product) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProduct = product, selectedVariant = null, variants = emptyList(), isLoading = true) }
            productVariantRepository.getVariantsForProductFlow(product.id)
                .map { variants -> variants.filter { !it.archived } }
                .onEach { variants ->
                    _uiState.update { it.copy(variants = variants, isLoading = false) }
                }
                .catch { e ->
                    Log.e("StockTransferVM", "Error fetching variants", e)
                    _uiState.update { it.copy(errorMessage = "Failed to fetch variants", isLoading = false) }
                }
                .collect()
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { it.copy(selectedVariant = variant) }
    }

    fun getStockForVariant(variantId: String, warehouseId: String): Int {
        return _uiState.value.stockLevels[Pair(variantId, warehouseId)] ?: 0
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
                if (!source.isActive || !dest.isActive) {
                    _uiState.update { it.copy(errorMessage = "عذراً، أحد المستودعات المختارة متوقف حالياً ولا يمكن إجراء عمليات عليه.", isLoading = false) }
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
                _uiState.update { it.copy(isFinished = true) }
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Error transferring stock", e)
                _uiState.update { it.copy(errorMessage = "Failed to transfer stock: ${e.message}", isLoading = false) }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val allVariants = productVariantRepository.getAllVariants()
            val variant = allVariants.find { it.barcode == barcode }
            if (variant != null) {
                val product = productRepository.getProduct(variant.productId)
                if (product != null) {
                    onProductSelected(product)
                    onVariantSelected(variant)
                }
            } else {
                _uiState.update { it.copy(errorMessage = "لم يتم العثور على منتج بهذا الباركود") }
            }
        }
    }
}
