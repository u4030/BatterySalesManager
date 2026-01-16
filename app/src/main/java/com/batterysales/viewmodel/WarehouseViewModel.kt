package com.batterysales.viewmodel

import androidx.compose.runtime.mutableStateOf
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

    val stockLevels = mutableStateOf<List<WarehouseStockItem>>(emptyList())

    init {
        fetchStockLevels()
    }

    private fun fetchStockLevels() {
        viewModelScope.launch {
            try {
                val products = productRepository.getProducts().filter { !it.isArchived }
                val warehouses = warehouseRepository.getWarehouses()
                val allStockEntries = stockEntryRepository.getAllStockEntries()

                // Pre-fetch all variants to reduce database calls
                val allVariants = products.flatMap { product ->
                    productVariantRepository.getVariantsForProduct(product.id).filter { !it.isArchived }
                }.associateBy { it.id }

                val stockMap = mutableMapOf<Pair<String, String>, Int>()

                for (entry in allStockEntries) {
                    val key = Pair(entry.productVariantId, entry.warehouseId)
                    stockMap[key] = (stockMap[key] ?: 0) + entry.quantity
                }

                val stockList = stockMap.mapNotNull { (key, quantity) ->
                    val variant = allVariants[key.first]
                    val warehouse = warehouses.find { it.id == key.second }
                    if (variant != null && warehouse != null) {
                        val product = products.find { it.id == variant.productId }
                        if (product != null) {
                            WarehouseStockItem(product, variant, warehouse, quantity)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

                stockLevels.value = stockList.sortedWith(compareBy({ it.warehouse.name }, { it.product.name }))

            } catch (e: Exception) {
                // Handle error appropriately
            }
        }
    }
}
