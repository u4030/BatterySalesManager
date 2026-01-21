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
import javax.inject.Inject

data class WarehouseStockItem(
    val product: Product,
    val variant: ProductVariant,
    val warehouse: Warehouse,
    val quantity: Int
)

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    productRepository: ProductRepository,
    productVariantRepository: ProductVariantRepository,
    warehouseRepository: WarehouseRepository,
    stockEntryRepository: StockEntryRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val stockLevels: StateFlow<List<WarehouseStockItem>> = combine(
        productRepository.getProducts(),
        warehouseRepository.getWarehouses(),
        stockEntryRepository.getAllStockEntriesFlow()
    ) { products, warehouses, allStockEntries ->
        _isLoading.value = true
        val activeProducts = products.filter { !it.isArchived }
        val productMap = activeProducts.associateBy { it.id }

        // Pre-fetch all variants to reduce database calls
        val allVariants = activeProducts.flatMap { product ->
            productVariantRepository.getVariantsForProduct(product.id).filter { !it.isArchived }
        }.associateBy { it.id }

        val stockMap = mutableMapOf<Pair<String, String>, Int>()
        for (entry in allStockEntries) {
            val key = Pair(entry.productVariantId, entry.warehouseId)
            stockMap[key] = (stockMap[key] ?: 0) + entry.quantity
        }

        val stockList = stockMap.mapNotNull { (key, quantity) ->
            val variantId = key.first
            val warehouseId = key.second
            val variant = allVariants[variantId]
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
}
