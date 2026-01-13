package com.batterysales.ui.stocktransfer

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockTransferViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    val products = mutableStateOf<List<Product>>(emptyList())
    val warehouses = mutableStateOf<List<Warehouse>>(emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

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

    fun transferStock(
        productId: String,
        sourceWarehouseId: String,
        destinationWarehouseId: String,
        quantity: Int
    ) {
        viewModelScope.launch {
            try {
                stockEntryRepository.transferStock(
                    productId = productId,
                    sourceWarehouseId = sourceWarehouseId,
                    destinationWarehouseId = destinationWarehouseId,
                    quantity = quantity
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to transfer stock: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
