package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
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
    val isSubmitting: Boolean = false,
    val isFinished: Boolean = false
)

@HiltViewModel
class StockTransferViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val summaryRepository: SummaryRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val selectedSourceWarehouseState = MutableStateFlow<Warehouse?>(null)

    private val _uiState = MutableStateFlow(StockTransferUiState())
    val uiState: StateFlow<StockTransferUiState> = _uiState.asStateFlow()

    private var currentUser: User? = null
    private var cachedInventorySummary: InventorySummary? = null

    init {
        loadInitialData()
        observeSourceWarehouseSummaryReactive()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeSourceWarehouseSummaryReactive() {
        selectedSourceWarehouseState
            .flatMapLatest { wh ->
                val whId = wh?.id ?: currentUser?.warehouseId
                summaryRepository.getInventorySummaryFlow(whId)
            }
            .onEach { summary ->
                cachedInventorySummary = summary
                val sourceWhId = summary.warehouseId ?: selectedSourceWarehouseState.value?.id ?: currentUser?.warehouseId ?: ""

                val newStockMap = _uiState.value.stockLevels.toMutableMap()
                val currentVariants = _uiState.value.variants.map { v ->
                    val item = summary.items[v.id]
                    val qty = item?.currentStock ?: 0
                    if (sourceWhId.isNotBlank()) {
                        newStockMap[Pair(v.id, sourceWhId)] = qty
                    }
                    v
                }

                _uiState.update { state ->
                    state.copy(
                        variants = currentVariants,
                        stockLevels = newStockMap
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = userRepository.getCurrentUser()
                currentUser = user

                val warehouses = warehouseRepository.getWarehousesOnce()
                val activeWarehouses = warehouses.filter { it.isActive }
                val products = productRepository.getProductsOnce()

                val sourceWh = if (user?.role == "seller") activeWarehouses.find { w -> w.id == user.warehouseId } else activeWarehouses.firstOrNull()
                val destWh = if (sourceWh != null && activeWarehouses.size >= 2) {
                    activeWarehouses.firstOrNull { it.id != sourceWh.id }
                } else null

                selectedSourceWarehouseState.value = sourceWh

                _uiState.update {
                    it.copy(
                        products = products.filter { !it.archived }.sortedBy { p -> p.name },
                        warehouses = activeWarehouses,
                        sourceWarehouse = sourceWh,
                        destinationWarehouse = destWh,
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

            val targetWhId = _uiState.value.sourceWarehouse?.id ?: currentUser?.warehouseId ?: ""
            val variantsForProduct = productVariantRepository.getVariantsForProduct(product.id)
                .filter { !it.archived }.sortedBy { it.capacity }

            val summaryItems = cachedInventorySummary?.items ?: emptyMap()
            val newStockMap = _uiState.value.stockLevels.toMutableMap()

            variantsForProduct.forEach { v ->
                val qty = summaryItems[v.id]?.currentStock ?: 0
                if (targetWhId.isNotBlank()) {
                    newStockMap[Pair(v.id, targetWhId)] = qty
                }
            }

            _uiState.update { it.copy(variants = variantsForProduct, stockLevels = newStockMap, isLoading = false) }
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { it.copy(selectedVariant = variant) }
    }

    fun onSourceWarehouseSelected(warehouse: Warehouse) {
        if (_uiState.value.sourceWarehouse?.id == warehouse.id) return

        val activeWhs = uiState.value.warehouses
        val autoDest = if (activeWhs.size == 2 || uiState.value.destinationWarehouse?.id == warehouse.id) {
            activeWhs.firstOrNull { it.id != warehouse.id }
        } else {
            uiState.value.destinationWarehouse
        }

        selectedSourceWarehouseState.value = warehouse
        _uiState.update { it.copy(sourceWarehouse = warehouse, destinationWarehouse = autoDest) }
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

            val available = state.stockLevels[Pair(variant.id, source.id)] ?: 0
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

                stockEntryRepository.transferStockBulk(
                    listOf(
                        StockEntryRepository.TransferItem(
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
                    )
                )

                // Clear fields and reset state for next operation while preserving selected warehouses
                _uiState.update {
                    it.copy(
                        selectedProduct = null,
                        selectedVariant = null,
                        variants = emptyList(),
                        quantity = "",
                        isFinished = true,
                        isSubmitting = false,
                        errorMessage = null
                    )
                }

                val currentSourceWh = _uiState.value.sourceWarehouse
                if (currentSourceWh != null) {
                    onSourceWarehouseSelected(currentSourceWh)
                }
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Transfer error", e)
                _uiState.update { it.copy(errorMessage = "فشل النقل: ${e.message}", isSubmitting = false) }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun getStockForVariant(variant: ProductVariant?, warehouseId: String?): Int {
        if (variant == null || warehouseId == null) return 0
        return uiState.value.stockLevels[Pair(variant.id, warehouseId)] ?: 0
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Try summary cache first (ZERO reads)
            val item = cachedInventorySummary?.items?.values?.find { it.barcode == barcode }
            if (item != null) {
                val product = _uiState.value.products.find { it.id == item.productId }
                if (product != null) {
                    onProductSelected(product)
                    val variant = _uiState.value.variants.find { it.id == item.variantId }
                    if (variant != null) onVariantSelected(variant)
                    return@launch
                }
            }
            
            // Cloud fallback
            try {
                val variant = productVariantRepository.getVariantByBarcode(barcode)
                if (variant != null && !variant.archived) {
                    val product = productRepository.getProduct(variant.productId)
                    if (product != null) {
                        onProductSelected(product)
                        onVariantSelected(variant)
                    }
                }
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Barcode error", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "خطأ في البحث") }
            }
        }
    }
}
 
