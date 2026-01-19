package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    private val _inventoryReport = MutableStateFlow<List<InventoryReportItem>>(emptyList())
    val inventoryReport = _inventoryReport.asStateFlow()

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses = _warehouses.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        generateInventoryReport()
    }

    fun generateInventoryReport() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val products = productRepository.getProducts().filter { !it.isArchived }
                val allVariants = products.flatMap { product ->
                    productVariantRepository.getVariantsForProduct(product.id).filter { !it.isArchived }
                }
                val allStockEntries = stockEntryRepository.getAllStockEntries()
                _warehouses.value = warehouseRepository.getWarehouses()

                val reportItems = mutableListOf<InventoryReportItem>()

                for (variant in allVariants) {
                    val product = products.find { it.id == variant.productId }
                    if (product != null) {
                        val variantEntries = allStockEntries.filter { it.productVariantId == variant.id }
                        val warehouseQuantities = mutableMapOf<String, Int>()
                        var totalQuantity = 0

                        for (warehouse in _warehouses.value) {
                            val quantityInWarehouse = variantEntries
                                .filter { it.warehouseId == warehouse.id }
                                .sumOf { it.quantity }
                            warehouseQuantities[warehouse.id] = quantityInWarehouse
                            totalQuantity += quantityInWarehouse
                        }

                        // Calculate weighted average cost
                        val positiveEntries = variantEntries.filter { it.quantity > 0 }
                        val totalCostOfPurchases = positiveEntries.sumOf { it.totalCost }
                        val totalItemsPurchased = positiveEntries.sumOf { it.quantity }
                        val averageCost = if (totalItemsPurchased > 0) totalCostOfPurchases / totalItemsPurchased else 0.0
                        val totalCostValue = totalQuantity * averageCost

                        if (totalQuantity > 0) { // Only add items that are in stock
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
                    }
                }
                _inventoryReport.value = reportItems

            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
}
