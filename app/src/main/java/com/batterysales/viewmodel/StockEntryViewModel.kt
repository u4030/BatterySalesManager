package com.batterysales.ui.stockentry

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class StockEntryItem(val product: Product, val quantity: Int)

@HiltViewModel
class StockEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    val products = mutableStateOf<List<Product>>(emptyList())
    val warehouses = mutableStateOf<List<Warehouse>>(emptyList())
    val stockItems = mutableStateListOf<StockEntryItem>()

    val totalCost = mutableStateOf("")
    val totalAmperes = mutableStateOf("")

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val costPerAmpere: Double
        get() {
            val cost = totalCost.value.toDoubleOrNull() ?: 0.0
            val amperes = totalAmperes.value.toDoubleOrNull() ?: 0.0
            return if (amperes > 0) cost / amperes else 0.0
        }

    init {
        fetchProducts()
        fetchWarehouses()
    }

    private fun fetchProducts() {
        viewModelScope.launch {
            try {
                products.value = productRepository.getProducts()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch products: ${e.message}"
            }
        }
    }

    private fun fetchWarehouses() {
        viewModelScope.launch {
            try {
                warehouses.value = warehouseRepository.getWarehouses()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch warehouses: ${e.message}"
            }
        }
    }

    fun addProductToEntry(product: Product, quantity: Int) {
        if (quantity > 0) {
            stockItems.add(StockEntryItem(product, quantity))
            recalculateTotalAmperes()
        }
    }

    fun removeProductFromEntry(item: StockEntryItem) {
        stockItems.remove(item)
        recalculateTotalAmperes()
    }

    private fun recalculateTotalAmperes() {
        totalAmperes.value = stockItems.sumOf { it.product.capacity * it.quantity }.toString()
    }

    fun saveStockEntry(warehouseId: String) {
        viewModelScope.launch {
            try {
                val entries = stockItems.map { item ->
                    StockEntry(
                        productId = item.product.id,
                        warehouseId = warehouseId,
                        quantity = item.quantity,
                        costPrice = item.product.capacity * costPerAmpere,
                        timestamp = Date()
                    )
                }
                stockEntryRepository.addStockEntries(entries)
                stockItems.clear()
                totalCost.value = ""
                totalAmperes.value = ""
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save stock entry: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
