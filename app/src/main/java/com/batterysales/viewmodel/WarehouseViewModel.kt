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
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val warehouses: StateFlow<List<Warehouse>> = warehouseRepository.getWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockLevels: StateFlow<List<WarehouseStockItem>> = combine(
        productRepository.getProducts(),
        productVariantRepository.getAllVariantsFlow(),
        warehouseRepository.getWarehouses(),
        stockEntryRepository.getAllStockEntriesFlow()
    ) { products, allVariants, warehouses, allStockEntries ->
        _isLoading.value = true
        val activeProducts = products.filter { !it.archived }
        val productMap = activeProducts.associateBy { it.id }
        val activeVariants = allVariants.filter { !it.archived }.associateBy { it.id }

        val stockMap = mutableMapOf<Pair<String, String>, Int>()
        for (entry in allStockEntries) {
            if (entry.status == "approved") {
                val key = Pair(entry.productVariantId, entry.warehouseId)
                stockMap[key] = (stockMap[key] ?: 0) + (entry.quantity - entry.returnedQuantity)
            }
        }

        val stockList = stockMap.mapNotNull { (key, quantity) ->
            val variantId = key.first
            val warehouseId = key.second
            val variant = activeVariants[variantId]
            val warehouse = warehouses.find { it.id == warehouseId }
            if (variant != null && warehouse != null) {
                val product = productMap[variant.productId]
                if (product != null && quantity > 0) {
                    WarehouseStockItem(product, variant, warehouse, quantity)
                } else {
                    null
                }
            } else {
                null
            }
        }
        _isLoading.value = false
        stockList.sortedWith(compareBy({ it.warehouse.name }, { it.product.name }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleWarehouseStatus(warehouse: Warehouse) {
        viewModelScope.launch {
            warehouseRepository.updateWarehouse(warehouse.copy(isActive = !warehouse.isActive))
        }
    }

    fun deleteWarehouse(warehouseId: String) {
        viewModelScope.launch {
            warehouseRepository.deleteWarehouse(warehouseId)
        }
    }
}
