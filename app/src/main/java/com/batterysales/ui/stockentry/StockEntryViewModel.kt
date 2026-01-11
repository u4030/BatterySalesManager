package com.batterysales.ui.stockentry

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
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class StockEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    val products = mutableStateOf<List<Product>>(emptyList())
    val warehouses = mutableStateOf<List<Warehouse>>(emptyList())

    init {
        fetchProducts()
        fetchWarehouses()
    }

    private fun fetchProducts() {
        viewModelScope.launch {
            products.value = productRepository.getProducts()
        }
    }

    private fun fetchWarehouses() {
        viewModelScope.launch {
            warehouses.value = warehouseRepository.getWarehouses()
        }
    }

    fun addStockEntry(
        productId: String,
        warehouseId: String,
        quantity: Int,
        costPrice: Double
    ) {
        viewModelScope.launch {
            val stockEntry = StockEntry(
                productId = productId,
                warehouseId = warehouseId,
                quantity = quantity,
                costPrice = costPrice,
                timestamp = Date()
            )
            stockEntryRepository.addStockEntry(stockEntry)
        }
    }
}
