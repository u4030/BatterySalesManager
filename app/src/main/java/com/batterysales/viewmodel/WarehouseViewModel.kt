package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WarehouseUiState(
    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouseId: String = "",
    val isAdmin: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val inventoryItems: List<InventoryReportItem> = emptyList(), // Nuclear: Full list
    val blockingConflictError: String? = null,
    val errorWithCollision: String? = blockingConflictError
)

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val variantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val userRepository: UserRepository,
    private val summaryRepository: SummaryRepository,
    private val persistentSyncManager: com.batterysales.data.local.PersistentSyncManager,
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarehouseUiState())
    val uiState: StateFlow<WarehouseUiState> = _uiState.asStateFlow()

    private var cachedSummary: InventorySummary? = null

    fun clearCollisionError() {
        _uiState.update { it.copy(blockingConflictError = null, errorWithCollision = null) }
        viewModelScope.launch {
            persistentSyncManager.removePendingOperation("CRITICAL_WAREHOUSE_CONFLICT")
        }
    }

    fun dismissBlockingConflict() {
        clearCollisionError()
    }

    init {
        viewModelScope.launch {
            persistentSyncManager.pendingOperationsFlow.collect { ops ->
                val conflictOp = ops.find { it.operationId == "CRITICAL_WAREHOUSE_CONFLICT" && it.status == "PENDING_SYNC" }
                if (conflictOp != null && _uiState.value.blockingConflictError == null) {
                    _uiState.update { it.copy(blockingConflictError = conflictOp.payloadJson, errorWithCollision = conflictOp.payloadJson) }
                }
            }
        }

        observeWarehousesAndUser()
    }

    /**
     * Strict Listen-Only Protocol:
     * Continuously streams warehouses and user role without any .get() server calls.
     * Dampens UI re-renders using distinctUntilChanged on warehouse id and stocks map.
     */
    private fun observeWarehousesAndUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = userRepository.getCurrentUser()
            val isAdmin = user?.role == User.ROLE_ADMIN

            _uiState.update { it.copy(isAdmin = isAdmin) }

            warehouseRepository.getWarehouses()
                .distinctUntilChanged { old, new ->
                    old.map { it.id to it.stocks } == new.map { it.id to it.stocks }
                }
                .collect { allWh ->
                    val filteredWh = if (isAdmin) allWh else allWh.filter { it.id == user?.warehouseId }
                    val currentSelected = _uiState.value.selectedWarehouseId
                    val newSelectedWhId = when {
                        currentSelected.isNotEmpty() && filteredWh.any { it.id == currentSelected } -> currentSelected
                        isAdmin -> filteredWh.firstOrNull()?.id ?: ""
                        else -> user?.warehouseId ?: ""
                    }

                    _uiState.update { current ->
                        current.copy(
                            warehouses = filteredWh,
                            selectedWarehouseId = newSelectedWhId
                        )
                    }

                    if (newSelectedWhId.isNotEmpty() && (newSelectedWhId != currentSelected || summaryJob == null)) {
                        loadWarehouseInventory(newSelectedWhId)
                    } else if (filteredWh.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
        }
    }

    // --- NUCLEAR STRATEGY: Load ENTIRE warehouse inventory via Real-time Flow ---
    private var summaryJob: kotlinx.coroutines.Job? = null
    private fun loadWarehouseInventory(whId: String) {
        if (whId.isEmpty()) return
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            summaryRepository.getInventorySummaryFlow(whId)
                .onEach { summary ->
                    cachedSummary = summary
                    filterAndSetItems(_uiState.value.searchQuery, whId)
                    _uiState.update { it.copy(isLoading = false) }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun filterAndSetItems(query: String, whId: String? = null) {
        val targetWhId = whId ?: _uiState.value.selectedWarehouseId
        if (targetWhId.isEmpty()) return

        viewModelScope.launch {
            val summary = cachedSummary
            val items = if (summary != null) {
                summary.items.values.asSequence()
                    .filter { !it.isDiscontinued }
                    .filter { if (query.isBlank()) true else it.productName.contains(query, ignoreCase = true) || it.barcode == query }
                    .map { item ->
                        InventoryReportItem(
                            product = Product(id = item.productId, name = item.productName),
                            variant = ProductVariant(id = item.variantId, productId = item.productId, capacity = item.capacity, barcode = item.barcode, weightedAverageCost = item.weightedAverageCost, sellingPrice = item.sellingPrice, specification = item.specification, productName = item.productName, isDiscontinued = item.isDiscontinued),
                            warehouseQuantities = mapOf(targetWhId to item.currentStock),
                            totalQuantity = item.currentStock,
                            averageCost = item.weightedAverageCost,
                            totalCostValue = item.currentStock * item.weightedAverageCost
                        )
                    }
                    .sortedWith(compareByDescending<InventoryReportItem> { it.product.name }.thenByDescending { it.variant.capacity })
                    .toList()
            } else {
                // Fallback filtering
                val variants = variantRepository.getAllVariants()
                variants.asSequence()
                    .filter { !it.archived && !it.isDiscontinued }
                    .filter { if (query.isBlank()) true else (it.productName?.contains(query, ignoreCase = true) ?: false) || it.barcode == query }
                    .map { v ->
                        val qty = (v.currentStock?.get(targetWhId) as? Number)?.toInt() ?: 0
                        InventoryReportItem(
                            product = Product(id = v.productId, name = v.productName ?: "Unknown"),
                            variant = v,
                            warehouseQuantities = mapOf(targetWhId to qty),
                            totalQuantity = qty,
                            averageCost = v.weightedAverageCost,
                            totalCostValue = qty * v.weightedAverageCost
                        )
                    }
                    .sortedWith(compareByDescending<InventoryReportItem> { it.product.name }.thenByDescending { it.variant.capacity })
                    .toList()
            }
            
            _uiState.update { it.copy(inventoryItems = items) }
        }
    }

    fun onWarehouseSelected(id: String) {
        _uiState.update { it.copy(selectedWarehouseId = id) }
        loadWarehouseInventory(id)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterAndSetItems(query)
    }

    fun addWarehouseOptimistic(name: String, location: String, isMain: Boolean = false) {
        val tempId = "wh_${System.currentTimeMillis()}"
        val optimisticWh = Warehouse(id = tempId, name = name, location = location, isMain = isMain, isActive = true)
        // الحقن التفاؤلي الفوري للمستودع الجديد في القوائم المنسدلة والـ TabRow بـ (0ms)
        _uiState.update { it.copy(warehouses = listOf(optimisticWh) + it.warehouses.filter { w -> w.id != tempId }) }
    }

    fun addWarehouse(name: String, location: String, isMain: Boolean = false) {
        val tempId = "wh_${System.currentTimeMillis()}"
        val optimisticWh = Warehouse(id = tempId, name = name, location = location, isMain = isMain, isActive = true)
        val previousWarehouses = _uiState.value.warehouses

        // حقن تفاؤلي فوري في الـ TabRow والقائمة الحية بـ (0ms)
        _uiState.update { it.copy(warehouses = listOf(optimisticWh) + it.warehouses, isSubmitting = true) }

        viewModelScope.launch {
            val opId = "wh_add_${System.currentTimeMillis()}"
            try {
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "WAREHOUSE_ADD",
                        relatedId = name,
                        status = "PENDING_SYNC"
                    )
                )

                warehouseRepository.addWarehouse(Warehouse(name = name, location = location, isMain = isMain))
                persistentSyncManager.removePendingOperation(opId)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _uiState.update { it.copy(warehouses = previousWarehouses) }
                val errorMsg = "تنبيه مزامنة حرج: فشل اعتماد إضافة المستودع [$name] في السيرفر بسبب تعارض في البيانات أثناء انقطاع اتصالك. تم إعادة فهارس الكاش لحالتها الحقيقية تلقائياً، يرجى النقر لتأكيد الاطلاع وتوقيع التسوية يدوياً."
                _uiState.update { it.copy(blockingConflictError = errorMsg, errorWithCollision = errorMsg) }
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = "CRITICAL_WAREHOUSE_CONFLICT",
                        type = "CONFLICT_LOCK",
                        relatedId = name,
                        payloadJson = errorMsg,
                        status = "PENDING_SYNC"
                    )
                )
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun updateWarehouse(warehouse: Warehouse) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                warehouseRepository.updateWarehouse(warehouse)
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deleteWarehouse(warehouseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                warehouseRepository.deleteWarehouse(warehouseId)
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
