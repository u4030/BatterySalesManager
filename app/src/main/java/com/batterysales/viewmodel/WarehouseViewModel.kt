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
    val inventoryItems: List<InventoryReportItem> = emptyList()
)

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val variantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarehouseUiState())
    val uiState: StateFlow<WarehouseUiState> = _uiState.asStateFlow()

    private val selectedWarehouseIdFlow = MutableStateFlow("")
    private val searchQueryFlow = MutableStateFlow("")

    init {
        loadInitialData()
        observeInventoryFlow()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = userRepository.getCurrentUser()
            val isAdmin = user?.role == User.ROLE_ADMIN
            val allWh = warehouseRepository.getWarehousesOnce()
            
            val filteredWh = if (isAdmin) allWh else allWh.filter { it.id == user?.warehouseId }
            val initialWhId = if (isAdmin) (filteredWh.firstOrNull()?.id ?: "") else user?.warehouseId ?: ""
            
            selectedWarehouseIdFlow.value = initialWhId
            _uiState.update { it.copy(
                warehouses = filteredWh,
                isAdmin = isAdmin,
                selectedWarehouseId = initialWhId
            ) }
        }
    }

    private fun observeInventoryFlow() {
        combine(
            variantRepository.getVariantsFlow(),
            selectedWarehouseIdFlow,
            searchQueryFlow
        ) { variants, whId, query ->
            if (whId.isEmpty()) {
                emptyList()
            } else {
                variants.asSequence()
                    .filter { !it.archived && !it.isDiscontinued }
                    .mapNotNull { v ->
                        val qty = (v.currentStock?.get(whId) as? Number)?.toInt() ?: 0
                        if (qty > 0) {
                            val averageCost = v.weightedAverageCost
                            InventoryReportItem(
                                product = Product(id = v.productId, name = v.productName ?: "Unknown"),
                                variant = v,
                                warehouseQuantities = mapOf(whId to qty),
                                totalQuantity = qty,
                                averageCost = averageCost,
                                totalCostValue = qty * averageCost
                            )
                        } else {
                            null
                        }
                    }
                    .filter { item ->
                        if (query.isBlank()) {
                            true
                        } else {
                            (item.product.name.contains(query, ignoreCase = true)) ||
                                    (item.variant.barcode.equals(query, ignoreCase = true))
                        }
                    }
                    .sortedWith(
                        compareBy<InventoryReportItem> { it.product.name }
                            .thenByDescending { it.variant.capacity }
                    )
                    .toList()
            }
        }
        .onEach { items ->
            _uiState.update { it.copy(inventoryItems = items, isLoading = false) }
        }
        .launchIn(viewModelScope)
    }

    fun onWarehouseSelected(id: String) {
        selectedWarehouseIdFlow.value = id
        _uiState.update { it.copy(selectedWarehouseId = id) }
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun addWarehouse(name: String, location: String, isMain: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                warehouseRepository.addWarehouse(Warehouse(name = name, location = location, isMain = isMain))
                loadInitialData()
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
                loadInitialData()
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
                loadInitialData()
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
