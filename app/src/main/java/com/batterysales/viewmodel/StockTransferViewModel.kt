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
    private val persistentSyncManager: com.batterysales.data.local.PersistentSyncManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockTransferUiState())
    val uiState: StateFlow<StockTransferUiState> = _uiState.asStateFlow()

    private var currentUser: User? = null
    private var cachedInventorySummary: InventorySummary? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = userRepository.getCurrentUser()
                currentUser = user

                // --- ELITE STRATEGY: Load setup data with MINIMAL reads ---
                val warehouses = warehouseRepository.getWarehousesOnce()
                val activeWarehouses = warehouses.filter { it.isActive }
                val products = productRepository.getProductsOnce()
                cachedInventorySummary = summaryRepository.getInventorySummary(if (user?.role == "seller") user.warehouseId else null)

                val sourceWh = if (user?.role == "seller") activeWarehouses.find { w -> w.id == user.warehouseId } else null
                val destWh = if (sourceWh != null && activeWarehouses.size == 2) {
                    activeWarehouses.firstOrNull { it.id != sourceWh.id }
                } else null

                _uiState.update {
                    it.copy(
                        products = products.filter { !it.archived }.sortedBy { it.name },
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
            
            val warehouseKey = cachedInventorySummary?.warehouseId ?: "global"
            val targetWhId = _uiState.value.sourceWarehouse?.id ?: currentUser?.warehouseId ?: warehouseKey

            // --- ELITE STRATEGY: Get variants from pre-loaded summary ---
            val summaryItems = cachedInventorySummary?.items?.values ?: emptyList()
            var variantsForProduct = summaryItems.filter { it.productId == product.id }
                .map { item -> 
                    ProductVariant(
                        id = item.variantId, productId = item.productId, capacity = item.capacity,
                        barcode = item.barcode, weightedAverageCost = item.weightedAverageCost,
                        productName = item.productName, specification = item.specification,
                        isDiscontinued = item.isDiscontinued,
                        currentStock = mapOf(warehouseKey to item.currentStock)
                    )
                }.sortedBy { it.capacity }

            // Fallback
            if (variantsForProduct.isEmpty()) {
                variantsForProduct = productVariantRepository.getVariantsForProduct(product.id)
                    .filter { !it.archived }.sortedBy { it.capacity }
            }

            val newStockMap = _uiState.value.stockLevels.toMutableMap()
            variantsForProduct.forEach { v ->
                val qty = (v.currentStock?.get(warehouseKey) as? Number)?.toInt() ?: (v.currentStock?.get(targetWhId) as? Number)?.toInt() ?: 0
                newStockMap[Pair(v.id, targetWhId)] = qty
            }

            _uiState.update { it.copy(variants = variantsForProduct, stockLevels = newStockMap, isLoading = false) }
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { it.copy(selectedVariant = variant) }
    }

    fun onSourceWarehouseSelected(warehouse: Warehouse) {
        val activeWhs = uiState.value.warehouses
        val autoDest = if (activeWhs.size == 2 || uiState.value.destinationWarehouse?.id == warehouse.id) {
            activeWhs.firstOrNull { it.id != warehouse.id }
        } else {
            uiState.value.destinationWarehouse
        }

        _uiState.update { it.copy(sourceWarehouse = warehouse, destinationWarehouse = autoDest, isLoading = true) }
        
        viewModelScope.launch {
            try {
                // Fetch the summary for the specific source warehouse
                val summary = summaryRepository.getInventorySummary(warehouse.id, forceRefresh = true)
                cachedInventorySummary = summary
                
                // Refresh stock levels for the current list of variants based on new summary
                val newStockMap = _uiState.value.stockLevels.toMutableMap()
                val updatedVariants = _uiState.value.variants.map { v ->
                    val item = summary?.items?.get(v.id)
                    val qty = item?.currentStock ?: 0
                    newStockMap[Pair(v.id, warehouse.id)] = qty
                    v.copy(currentStock = mapOf(warehouse.id to qty))
                }

                _uiState.update { it.copy(variants = updatedVariants, stockLevels = newStockMap, isLoading = false) }
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Source selection error", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onDestinationWarehouseSelected(warehouse: Warehouse) {
        _uiState.update { it.copy(destinationWarehouse = warehouse) }
    }

    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(quantity = quantity) }
    }

    fun onTransferStock() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val state = _uiState.value
            val variant = state.selectedVariant
            val source = state.sourceWarehouse
            val dest = state.destinationWarehouse
            val qty = state.quantity.toIntOrNull()

            if (variant == null || source == null || dest == null || qty == null || qty <= 0) {
                _uiState.update { it.copy(errorMessage = "الرجاء ملء جميع الحقول بكمية صحيحة", isSubmitting = false) }
                return@launch
            }

            if (source.id == dest.id) {
                _uiState.update { it.copy(errorMessage = "لا يمكن نقل المخزون إلى نفس المستودع", isSubmitting = false) }
                return@launch
            }

            val available = state.stockLevels[Pair(variant.id, source.id)] ?: 0
            if (qty > available) {
                _uiState.update { it.copy(errorMessage = "الكمية المطلوبة أكبر من المتوفر في المستودع ($available)", isSubmitting = false) }
                return@launch
            }

            // --- المقاصة التفاؤلية المزدوجة الفورية (0ms) ---
            val newStockMap = state.stockLevels.toMutableMap()
        val newSrcStock = (available - qty).coerceAtLeast(0)
        newStockMap[Pair(variant.id, source.id)] = newSrcStock
            val destStock = state.stockLevels[Pair(variant.id, dest.id)] ?: 0
        val newDestStock = destStock + qty
        newStockMap[Pair(variant.id, dest.id)] = newDestStock
            _uiState.update { it.copy(stockLevels = newStockMap) }

        executeTransferOptimistic(variant.id, source.id, dest.id, qty)

            val opId = "transfer_${System.currentTimeMillis()}_${variant.id}"
            try {
                if (!source.isActive || !dest.isActive) {
                    _uiState.update { it.copy(errorMessage = "عذراً، أحد المستودعات المختارة متوقف حالياً.", isSubmitting = false) }
                    return@launch
                }

                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "STOCK_TRANSFER",
                        relatedId = variant.id,
                        status = "PENDING_SYNC"
                    )
                )

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

                persistentSyncManager.removePendingOperation(opId)
                loadInitialData()

                // Clear fields and reset state for next operation instead of closing screen
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
            } catch (e: Exception) {
                Log.e("StockTransferVM", "Transfer error", e)
                persistentSyncManager.removePendingOperation(opId)
                _uiState.update { it.copy(errorMessage = "فشل النقل: ${e.message}", isSubmitting = false) }
            }
        }
    }

    private fun checkIfUnderThreshold(warehouseId: String, variantId: String, deltaQty: Int): Boolean {
        val currentStock = uiState.value.stockLevels[Pair(variantId, warehouseId)] ?: 0
        val resultingStock = currentStock + deltaQty
        val minQty = uiState.value.selectedVariant?.minQuantity ?: 5
        return resultingStock <= minQty
    }

    private fun checkIfAboveThreshold(warehouseId: String, variantId: String, deltaQty: Int): Boolean {
        val currentStock = uiState.value.stockLevels[Pair(variantId, warehouseId)] ?: 0
        val resultingStock = currentStock + deltaQty
        val minQty = uiState.value.selectedVariant?.minQuantity ?: 5
        return resultingStock > minQty
    }

    fun executeTransferOptimistic(variantId: String, srcWh: String, destWh: String, qty: Int) {
        viewModelScope.launch {
            val srcComposite = "low_stock_${variantId}_${srcWh}"
            val destComposite = "low_stock_${variantId}_${destWh}"
            val srcOpId = "suppress_alert_${variantId}_${srcWh}"
            val destOpId = "suppress_alert_${variantId}_${destWh}"

            // 1. مستودع المصدر: إذا كسر حد الأمان، بث تنبيه متفائل فورا بإزالة أي قفل حجب
            if (checkIfUnderThreshold(srcWh, variantId, -qty)) {
                persistentSyncManager.removePendingOperation(srcOpId)
                persistentSyncManager.removePendingOperation("suppress_${srcComposite}")

                val resultingSrcStock = (uiState.value.stockLevels[Pair(variantId, srcWh)] ?: 0) - qty
                val minQty = uiState.value.selectedVariant?.minQuantity ?: 5
                val alertPayload = com.google.gson.Gson().toJson(
                    mapOf(
                        "id" to srcComposite,
                        "variantId" to variantId,
                        "productId" to (uiState.value.selectedVariant?.productId ?: ""),
                        "productName" to (uiState.value.selectedProduct?.name ?: "بطارية"),
                        "capacity" to (uiState.value.selectedVariant?.capacity ?: 0),
                        "specification" to (uiState.value.selectedVariant?.specification ?: ""),
                        "warehouseId" to srcWh,
                        "warehouseName" to (uiState.value.sourceWarehouse?.name ?: "المستودع"),
                        "currentStock" to resultingSrcStock,
                        "minQuantity" to minQty
                    )
                )
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = "opt_$srcComposite",
                        type = "OPTIMISTIC_LOW_STOCK_ALERT",
                        relatedId = srcComposite,
                        payloadJson = alertPayload,
                        status = "PENDING_SYNC"
                    )
                )
            }

            // 2. مستودع الهدف: إذا حل العجز، اطرد التنبيه فوراً
            if (checkIfAboveThreshold(destWh, variantId, qty)) {
                persistentSyncManager.removePendingOperation("opt_$destComposite")
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = destOpId,
                        type = "ALERT_SUPPRESS",
                        relatedId = destComposite,
                        status = "PENDING_SYNC"
                    )
                )
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
 
