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
    val inventoryItems: List<InventoryReportItem> = emptyList() // Nuclear: Full list
)

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val variantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val userRepository: UserRepository,
    private val summaryRepository: SummaryRepository,
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarehouseUiState())
    val uiState: StateFlow<WarehouseUiState> = _uiState.asStateFlow()

    private var cachedSummary: InventorySummary? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = userRepository.getCurrentUser()
            val isAdmin = user?.role == User.ROLE_ADMIN
            val allWh = warehouseRepository.getWarehousesOnce()
            
            val filteredWh = if (isAdmin) allWh else allWh.filter { it.id == user?.warehouseId }
            val initialWhId = if (isAdmin) (filteredWh.firstOrNull()?.id ?: "") else user?.warehouseId ?: ""
            
            _uiState.update { it.copy(
                warehouses = filteredWh,
                isAdmin = isAdmin,
                selectedWarehouseId = initialWhId
            ) }
            
            loadWarehouseInventory(initialWhId)
        }
    }

    // --- NUCLEAR STRATEGY: Load ENTIRE warehouse inventory in ONE read with FALLBACK ---
    private fun loadWarehouseInventory(whId: String) {
        if (whId.isEmpty()) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val summary = summaryRepository.getInventorySummary(whId)
                cachedSummary = summary
                
                // Always call filterAndSetItems to handle both summary and fallback scenarios
                filterAndSetItems(_uiState.value.searchQuery, whId)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun filterAndSetItems(query: String, whId: String? = null) {
        val targetWhId = whId ?: _uiState.value.selectedWarehouseId
        if (targetWhId.isEmpty()) return

        viewModelScope.launch {
            val summary = cachedSummary
            val items = if (summary != null) {
                summary.items.values.asSequence()
                    .filter { if (query.isBlank()) true else it.productName.contains(query, ignoreCase = true) || it.barcode == query }
                    .map { item ->
                        InventoryReportItem(
                            product = Product(id = item.productId, name = item.productName),
                            variant = ProductVariant(id = item.variantId, productId = item.productId, capacity = item.capacity, barcode = item.barcode, weightedAverageCost = item.weightedAverageCost, sellingPrice = item.sellingPrice, specification = item.specification),
                            warehouseQuantities = mapOf(targetWhId to item.currentStock),
                            totalQuantity = item.currentStock,
                            averageCost = item.weightedAverageCost,
                            totalCostValue = item.currentStock * item.weightedAverageCost
                        )
                    }
                    .sortedBy { it.product.name }
                    .toList()
            } else {
                // Fallback filtering
                val variants = variantRepository.getAllVariants()
                variants.asSequence()
                    .filter { !it.archived }
                    .filter { if (query.isBlank()) true else (it.productName?.contains(query, ignoreCase = true) ?: false) || it.barcode == query }
                    .map { v ->
                        val qty = v.currentStock?.get(targetWhId) ?: 0
                        InventoryReportItem(
                            product = Product(id = v.productId, name = v.productName ?: "Unknown"),
                            variant = v,
                            warehouseQuantities = mapOf(targetWhId to qty),
                            totalQuantity = qty,
                            averageCost = v.weightedAverageCost,
                            totalCostValue = qty * v.weightedAverageCost
                        )
                    }
                    .sortedBy { it.product.name }
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

    fun addWarehouse(name: String, location: String, isMain: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                warehouseRepository.addWarehouse(Warehouse(name = name, location = location, isMain = isMain))
                loadInitialData()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateWarehouse(warehouse: Warehouse) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                warehouseRepository.updateWarehouse(warehouse)
                loadInitialData()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteWarehouse(warehouseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                warehouseRepository.deleteWarehouse(warehouseId)
                loadInitialData()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
