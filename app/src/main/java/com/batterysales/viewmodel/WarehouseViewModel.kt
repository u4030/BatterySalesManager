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

    val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val warehouses: StateFlow<List<Warehouse>> = combine(
        warehouseRepository.getWarehouses(),
        currentUser
    ) { list, user ->
        if (user?.role == com.batterysales.data.models.User.ROLE_SELLER && user.warehouseId != null) {
            list.filter { it.id == user.warehouseId }
        } else {
            list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockLevels: StateFlow<List<WarehouseStockItem>> = combine(
        productRepository.getProducts(),
        productVariantRepository.getAllVariantsFlow(),
        warehouseRepository.getWarehouses(),
        stockEntryRepository.getAllStockEntriesFlow(),
        currentUser,
        _searchQuery
    ) { args ->
        args
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).map { args ->
        val products = args[0] as List<Product>
        val allVariants = args[1] as List<ProductVariant>
        val allWarehouses = args[2] as List<Warehouse>
        val allStockEntries = args[3] as List<com.batterysales.data.models.StockEntry>
        val user = args[4] as com.batterysales.data.models.User?
        val query = args[5] as String

        _isLoading.value = true
        val activeProducts = products.filter { !it.archived }
        val productMap = activeProducts.associateBy { it.id }
        val activeVariants = allVariants.filter { !it.archived }
        val activeVariantsMap = activeVariants.associateBy { it.id }

        val stockMap = mutableMapOf<Pair<String, String>, Int>()
        val approvedEntries = allStockEntries.filter { it.status == "approved" }

        // Process all active variants in one pass to avoid leftovers or duplicates
        for (variant in activeVariants) {
            if (variant.currentStock != null) {
                // Use denormalized stock
                variant.currentStock.forEach { (warehouseId, quantity) ->
                    if (user?.role == com.batterysales.data.models.User.ROLE_SELLER && warehouseId != user.warehouseId) return@forEach
                    stockMap[Pair(variant.id, warehouseId)] = quantity
                }
            } else {
                // Fallback to historical calculation for this specific variant
                val variantEntries = approvedEntries.filter { it.productVariantId == variant.id }
                val whGroups = variantEntries.groupBy { it.warehouseId }
                whGroups.forEach { (whId, entries) ->
                    if (user?.role == com.batterysales.data.models.User.ROLE_SELLER && whId != user.warehouseId) return@forEach
                    stockMap[Pair(variant.id, whId)] = entries.sumOf { it.quantity - it.returnedQuantity }
                }
            }
        }

        val stockList = stockMap.mapNotNull { (key, quantity) ->
            val variantId = key.first
            val warehouseId = key.second
            val variant = activeVariantsMap[variantId]
            val warehouse = allWarehouses.find { it.id == warehouseId }
            if (variant != null && warehouse != null) {
                val product = productMap[variant.productId]
                if (product != null && quantity > 0) {
                    if (query.isNotBlank() && !product.name.contains(query, ignoreCase = true)) {
                        return@mapNotNull null
                    }
                    WarehouseStockItem(product, variant, warehouse, quantity)
                } else {
                    null
                }
            } else {
                null
            }
        }
        _isLoading.value = false
        stockList.sortedWith(compareByDescending<WarehouseStockItem> { it.warehouse.name }.thenBy { it.product.name }.thenBy { it.variant.capacity })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
