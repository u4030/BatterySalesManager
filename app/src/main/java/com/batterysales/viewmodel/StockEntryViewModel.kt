package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repository.ProductRepository
import com.batterysales.data.repository.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockEntryItem(
    val product: Product,
    var quantity: String = "",
    var costPrice: String = ""
)

@HiltViewModel
class StockEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses: StateFlow<List<Warehouse>> = _warehouses

    private val _allProducts = MutableStateFlow<List<Product>>(emptyList())
    val allProducts: StateFlow<List<Product>> = _allProducts

    private val _selectedWarehouse = MutableStateFlow<Warehouse?>(null)
    val selectedWarehouse: StateFlow<Warehouse?> = _selectedWarehouse

    private val _costPerAmpere = MutableStateFlow("")
    val costPerAmpere: StateFlow<String> = _costPerAmpere

    private val _entryItems = MutableStateFlow<List<StockEntryItem>>(emptyList())
    val entryItems: StateFlow<List<StockEntryItem>> = _entryItems

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            warehouseRepository.getAllWarehouses().onSuccess {
                _warehouses.value = it
                if (it.isNotEmpty()) { _selectedWarehouse.value = it.first() }
            }
            productRepository.getAllProducts().onSuccess { _allProducts.value = it }
        }
    }

    fun selectWarehouse(warehouse: Warehouse) { _selectedWarehouse.value = warehouse }

    fun setCostPerAmpere(cost: String) {
        _costPerAmpere.value = cost
        recalculateCosts()
    }

    fun addEntryItem(product: Product) {
        val newItem = StockEntryItem(product = product)
        _entryItems.value = _entryItems.value + newItem
        recalculateCosts()
    }

    fun updateItem(itemToUpdate: StockEntryItem, newQuantity: String, newCost: String) {
        _entryItems.value = _entryItems.value.map {
            if (it.product.id == itemToUpdate.product.id) {
                it.copy(quantity = newQuantity, costPrice = newCost)
            } else { it }
        }
    }

    private fun recalculateCosts() {
        val costPerAmpereDouble = _costPerAmpere.value.toDoubleOrNull()
        _entryItems.value = _entryItems.value.map { item ->
            if (costPerAmpereDouble != null) {
                item.copy(costPrice = (item.product.capacity * costPerAmpereDouble).toString())
            } else { item }
        }
    }
}
