package com.batterysales.ui.stocktransfer

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockTransferViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository
) : ViewModel() {

    val products = mutableStateOf<List<Product>>(emptyList())
    val variants = mutableStateOf<List<ProductVariant>>(emptyList())
    val warehouses = mutableStateOf<List<Warehouse>>(emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    init {
        fetchProducts()
        fetchWarehouses()
    }

    private fun fetchProducts() {
        viewModelScope.launch {
            try {
                products.value = productRepository.getProducts().filter { !it.isArchived }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch products: ${e.message}"
            }
        }
    }

    fun fetchVariantsForProduct(productId: String) {
        viewModelScope.launch {
            try {
                variants.value = productVariantRepository.getVariantsForProduct(productId).filter { !it.isArchived }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch variants: ${e.message}"
                variants.value = emptyList()
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
        productVariantId: String,
        sourceWarehouseId: String,
        destinationWarehouseId: String,
        quantity: Int
    ) {
        if (sourceWarehouseId == destinationWarehouseId) {
            _errorMessage.value = "لا يمكن ترحيل المخزون إلى نفس المستودع."
            return
        }
        if (quantity <= 0) {
            _errorMessage.value = "الكمية يجب أن تكون أكبر من صفر."
            return
        }

        viewModelScope.launch {
            try {
                stockEntryRepository.transferStock(
                    productVariantId = productVariantId,
                    sourceWarehouseId = sourceWarehouseId,
                    destinationWarehouseId = destinationWarehouseId,
                    quantity = quantity
                )
                _successMessage.value = "تم ترحيل المخزون بنجاح!"
            } catch (e: Exception) {
                _errorMessage.value = "فشل ترحيل المخزون: ${e.message}"
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
