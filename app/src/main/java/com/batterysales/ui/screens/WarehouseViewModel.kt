//package com.batterysales.ui.screens
//
//import androidx.compose.runtime.mutableStateOf
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.batterysales.data.models.Product
//import com.batterysales.data.models.Warehouse
//import com.batterysales.data.repositories.ProductRepository
//import com.batterysales.data.repositories.StockEntryRepository
//import com.batterysales.data.repositories.WarehouseRepository
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
//data class WarehouseStockItem(
//    val product: Product,
//    val warehouse: Warehouse,
//    val quantity: Int
//)
//
//@HiltViewModel
//class WarehouseViewModel @Inject constructor(
//    private val productRepository: ProductRepository,
//    private val warehouseRepository: WarehouseRepository,
//    private val stockEntryRepository: StockEntryRepository
//) : ViewModel() {
//
//    val stockLevels = mutableStateOf<List<WarehouseStockItem>>(emptyList())
//    private val _errorMessage = MutableStateFlow<String?>(null)
//    val errorMessage = _errorMessage.asStateFlow()
//
//    init {
//        fetchStockLevels()
//    }
//
//    private fun fetchStockLevels() {
//        viewModelScope.launch {
//            try {
//                val products = productRepository.getProducts()
//                val warehouses = warehouseRepository.getWarehouses()
//                val stockEntries = stockEntryRepository.getStockEntries()
//
//                val stockMap = mutableMapOf<Pair<String, String>, Int>()
//
//                for (entry in stockEntries) {
//                    val key = Pair(entry.productId, entry.warehouseId)
//                    stockMap[key] = (stockMap[key] ?: 0) + entry.quantity
//                }
//
//                val stockList = stockMap.map { (key, quantity) ->
//                    val product = products.find { it.id == key.first }
//                    val warehouse = warehouses.find { it.id == key.second }
//                    if (product != null && warehouse != null) {
//                        WarehouseStockItem(product, warehouse, quantity)
//                    } else {
//                        null
//                    }
//                }.filterNotNull()
//
//                stockLevels.value = stockList
//            } catch (e: Exception) {
//                _errorMessage.value = "Failed to fetch stock levels: ${e.message}"
//            }
//        }
//    }
//
//    fun clearError() {
//        _errorMessage.value = null
//    }
//}
