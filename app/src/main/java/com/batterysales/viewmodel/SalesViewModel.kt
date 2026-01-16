package com.batterysales.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val invoiceRepository: InvoiceRepository
) : ViewModel() {

    val products = mutableStateOf<List<Product>>(emptyList())
    val variants = mutableStateOf<List<ProductVariant>>(emptyList())
    val warehouses = mutableStateOf<List<Warehouse>>(emptyList())
    val stockLevels = mutableStateOf<Map<Pair<String, String>, Int>>(emptyMap())

    val selectedProduct = mutableStateOf<Product?>(null)
    val selectedVariant = mutableStateOf<ProductVariant?>(null)
    val selectedWarehouse = mutableStateOf<Warehouse?>(null)
    val quantity = mutableStateOf("1")
    val sellingPrice = mutableStateOf("")

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        fetchInitialData()
    }

    fun onProductSelected(product: Product) {
        selectedProduct.value = product
        selectedVariant.value = null // Reset variant
        viewModelScope.launch {
            try {
                variants.value = productVariantRepository.getVariantsForProduct(product.id).filter { !it.isArchived }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch variants: ${e.message}"
            }
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        selectedVariant.value = variant
        sellingPrice.value = variant.sellingPrice.toString()
    }

    private fun fetchInitialData() {
        viewModelScope.launch {
            try {
                products.value = productRepository.getProducts().filter { !it.isArchived }
                warehouses.value = warehouseRepository.getWarehouses()
                updateStockLevels()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch data: ${e.message}"
            }
        }
    }

    private suspend fun updateStockLevels() {
        val stockEntries = stockEntryRepository.getAllStockEntries()
        val stockMap = mutableMapOf<Pair<String, String>, Int>()
        for (entry in stockEntries) {
            val key = Pair(entry.productVariantId, entry.warehouseId)
            stockMap[key] = (stockMap[key] ?: 0) + entry.quantity
        }
        stockLevels.value = stockMap
    }

    fun getAvailableQuantity(variantId: String, warehouseId: String): Int {
        return stockLevels.value[Pair(variantId, warehouseId)] ?: 0
    }

    fun createSale(customerName: String, customerPhone: String, paidAmount: Double) {
        viewModelScope.launch {
            try {
                val product = selectedProduct.value ?: throw IllegalStateException("Product not selected")
                val variant = selectedVariant.value ?: throw IllegalStateException("Variant not selected")
                val warehouse = selectedWarehouse.value ?: throw IllegalStateException("Warehouse not selected")
                val qty = quantity.value.toIntOrNull() ?: 0
                val price = sellingPrice.value.toDoubleOrNull() ?: 0.0
                val total = qty * price

                val available = getAvailableQuantity(variant.id, warehouse.id)
                if (qty <= 0 || qty > available) {
                    throw IllegalStateException("Insufficient stock. Available: $available, Requested: $qty")
                }

                val entries = stockEntryRepository.getAllStockEntries()
                    .filter { it.productVariantId == variant.id && it.warehouseId == warehouse.id && it.quantity > 0 }
                val totalCost = entries.sumOf { it.costPrice * it.quantity }
                val totalQuantity = entries.sumOf { it.quantity }
                val weightedAverageCost = if (totalQuantity > 0) totalCost / totalQuantity else 0.0

                val stockEntry = StockEntry(
                    productVariantId = variant.id,
                    warehouseId = warehouse.id,
                    quantity = -qty,
                    costPrice = weightedAverageCost,
                    timestamp = Date()
                )
                stockEntryRepository.addStockEntry(stockEntry)

                val invoice = Invoice(
                    customerName = customerName,
                    customerPhone = customerPhone,
                    items = listOf(InvoiceItem(
                        productName = "${product.name} - ${variant.capacity} Amp",
                        quantity = qty,
                        unitPrice = price,
                        totalPrice = total
                    )),
                    totalAmount = total,
                    paidAmount = paidAmount,
                    remainingAmount = total - paidAmount,
                    status = if (paidAmount >= total) "paid" else "pending"
                )
                invoiceRepository.createInvoice(invoice)

                selectedProduct.value = null
                selectedVariant.value = null
                selectedWarehouse.value = null
                quantity.value = "1"
                sellingPrice.value = ""
                updateStockLevels()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create sale: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
