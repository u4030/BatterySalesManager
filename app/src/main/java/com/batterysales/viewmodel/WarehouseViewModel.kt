package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
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

data class WarehouseStockItem(
    val product: Product,
    val variant: ProductVariant,
    val warehouse: Warehouse,
    val quantity: Int
)

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val userRepository: com.batterysales.data.repositories.UserRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedWarehouseId = MutableStateFlow<String?>(null)
    val selectedWarehouseId = _selectedWarehouseId.asStateFlow()

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val warehouses: StateFlow<List<Warehouse>> = combine(
        warehouseRepository.getWarehouses(),
        currentUser
    ) { list, user ->
        val active = list.filter { it.isActive }
        val result = if (user?.role == com.batterysales.data.models.User.ROLE_SELLER && user.warehouseId != null) {
            active.filter { it.id == user.warehouseId }
        } else {
            active
        }
        result.sortedBy { it.name }
    }.onEach { list ->
        if (_selectedWarehouseId.value == null && list.isNotEmpty()) {
            _selectedWarehouseId.value = list.first().id
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val stockLevels: Flow<PagingData<WarehouseStockItem>> = combine(
        currentUser,
        _searchQuery,
        _selectedWarehouseId.filterNotNull() // Wait for a warehouse to be selected
    ) { user, query, whId ->
        Triple(user, query, whId)
    }.flatMapLatest { (user, query, whId) ->
        _isLoading.value = true
        val targetWhId = if (user?.role == com.batterysales.data.models.User.ROLE_SELLER) user.warehouseId else whId

        Pager(PagingConfig(pageSize = 25)) {
            com.batterysales.data.paging.VariantPagingSource(
                firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                productRepository = productRepository,
                searchQuery = query,
                warehouseId = targetWhId,
                onlyWithStock = true
            )
        }.flow.map { pagingData ->
            val allWarehouses = warehouses.value
            _isLoading.value = false
            pagingData.map { item ->
                val warehouse = allWarehouses.find { it.id == (targetWhId ?: item.warehouseQuantities.keys.firstOrNull()) }
                             ?: Warehouse(id = targetWhId ?: "", name = "المستودع المختار")

                WarehouseStockItem(
                    product = item.product,
                    variant = item.variant,
                    warehouse = warehouse,
                    quantity = item.totalQuantity
                )
            }
        }.cachedIn(viewModelScope)
    }

    fun onWarehouseSelected(id: String) {
        _selectedWarehouseId.value = id
    }

    fun toggleWarehouseStatus(warehouse: Warehouse) {
        viewModelScope.launch {
            warehouseRepository.updateWarehouse(warehouse.copy(isActive = !warehouse.isActive))
        }
    }

    fun toggleMainWarehouse(warehouse: Warehouse) {
        viewModelScope.launch {
            val isCurrentlyMain = warehouse.isMain
            if (!isCurrentlyMain) {
                // Ensure only one is main
                val all = warehouseRepository.getWarehousesOnce()
                all.forEach { 
                    if (it.isMain) warehouseRepository.updateWarehouse(it.copy(isMain = false))
                }
            }
            warehouseRepository.updateWarehouse(warehouse.copy(isMain = !isCurrentlyMain))
        }
    }

    fun addWarehouse(name: String, location: String, isMain: Boolean) {
        viewModelScope.launch {
            if (isMain) {
                val all = warehouseRepository.getWarehousesOnce()
                all.forEach { 
                    if (it.isMain) warehouseRepository.updateWarehouse(it.copy(isMain = false))
                }
            }
            val warehouse = Warehouse(name = name, location = location, isMain = isMain)
            warehouseRepository.addWarehouse(warehouse)
        }
    }

    fun deleteWarehouse(warehouseId: String) {
        viewModelScope.launch {
            warehouseRepository.deleteWarehouse(warehouseId)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
