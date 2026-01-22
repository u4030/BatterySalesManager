package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class InventoryReportItem(
    val product: Product,
    val variant: ProductVariant,
    val warehouseQuantities: Map<String, Int>, // WarehouseID to Quantity
    val totalQuantity: Int,
    val averageCost: Double,
    val totalCostValue: Double
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    productRepository: ProductRepository,
    productVariantRepository: ProductVariantRepository,
    warehouseRepository: WarehouseRepository,
    stockEntryRepository: StockEntryRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val warehouses: StateFlow<List<Warehouse>> = warehouseRepository.getWarehouses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryReport: StateFlow<List<InventoryReportItem>> = combine(
        productRepository.getProducts(),
        productVariantRepository.getAllVariantsFlow(),
        stockEntryRepository.getAllStockEntriesFlow(),
        warehouses
    ) { products, allVariants, allStockEntries, warehouseList ->

        val reportItems = mutableListOf<InventoryReportItem>()
        val activeProducts = products.filter { !it.isArchived }.associateBy { it.id }
        val activeVariants = allVariants.filter { !it.isArchived }

        for (variant in activeVariants) {
            val product = activeProducts[variant.productId] ?: continue

            val variantEntries = allStockEntries.filter { it.productVariantId == variant.id }
            if (variantEntries.isEmpty()) continue

            val warehouseQuantities = mutableMapOf<String, Int>()
            var totalQuantity = 0

            for (warehouse in warehouseList) {
                val quantityInWarehouse = variantEntries
                    .filter { it.warehouseId == warehouse.id }
                    .sumOf { it.quantity }
                warehouseQuantities[warehouse.id] = quantityInWarehouse
                totalQuantity += quantityInWarehouse
            }

            if (totalQuantity <= 0) continue

            val positiveEntries = variantEntries.filter { it.quantity > 0 }
            val totalCostOfPurchases = positiveEntries.sumOf { it.totalCost }
            val totalItemsPurchased = positiveEntries.sumOf { it.quantity }
            val averageCost = if (totalItemsPurchased > 0) totalCostOfPurchases / totalItemsPurchased else 0.0
            val totalCostValue = totalQuantity * averageCost

            reportItems.add(
                InventoryReportItem(
                    product = product,
                    variant = variant,
                    warehouseQuantities = warehouseQuantities,
                    totalQuantity = totalQuantity,
                    averageCost = averageCost,
                    totalCostValue = totalCostValue
                )
            )
        }
        reportItems
    }.onStart { _isLoading.value = true }
     .onEach { _isLoading.value = false }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
