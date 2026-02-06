//package com.batterysales.ui.screens
//
//import androidx.compose.runtime.mutableStateOf
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.batterysales.data.models.*
//import com.batterysales.data.repositories.*
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.launch
//import java.util.Date
//import javax.inject.Inject
//
//@HiltViewModel
//class SalesViewModel @Inject constructor(
//    private val productRepository: ProductRepository,
//    private val warehouseRepository: WarehouseRepository,
//    private val stockEntryRepository: StockEntryRepository,
//    private val invoiceRepository: InvoiceRepository
//) : ViewModel() {
//
//    val products = mutableStateOf<List<Product>>(emptyList())
//    val warehouses = mutableStateOf<List<Warehouse>>(emptyList())
//    val stockLevels = mutableStateOf<Map<Pair<String, String>, Int>>(emptyMap())
//
//    val selectedProduct = mutableStateOf<Product?>(null)
//    val selectedWarehouse = mutableStateOf<Warehouse?>(null)
//    val quantity = mutableStateOf("1")
//    val sellingPrice = mutableStateOf("")
//
//    private val _errorMessage = MutableStateFlow<String?>(null)
//    val errorMessage = _errorMessage.asStateFlow()
//
//    init {
//        fetchData()
//    }
//
//    fun onProductSelected(product: Product) {
//        selectedProduct.value = product
//        sellingPrice.value = product.sellingPrice.toString()
//    }
//
//    private fun fetchData() {
//        viewModelScope.launch {
//            try {
//                products.value = productRepository.getProducts()
//                warehouses.value = warehouseRepository.getWarehouses()
//                val stockEntries = stockEntryRepository.getStockEntries()
//
//                val stockMap = mutableMapOf<Pair<String, String>, Int>()
//                for (entry in stockEntries) {
//                    val key = Pair(entry.productId, entry.warehouseId)
//                    stockMap[key] = (stockMap[key] ?: 0) + entry.quantity
//                }
//                stockLevels.value = stockMap
//            } catch (e: Exception) {
//                _errorMessage.value = "Failed to fetch data: ${e.message}"
//            }
//        }
//    }
//
//    fun getAvailableQuantity(productId: String, warehouseId: String): Int {
//        return stockLevels.value[Pair(productId, warehouseId)] ?: 0
//    }
//
//    fun createSale(customerName: String, customerPhone: String, paidAmount: Double) {
//        viewModelScope.launch {
//            try {
//                val product = selectedProduct.value ?: throw IllegalStateException("Product not selected")
//                val warehouse = selectedWarehouse.value ?: throw IllegalStateException("Warehouse not selected")
//                val qty = quantity.value.toIntOrNull() ?: 0
//                val price = sellingPrice.value.toDoubleOrNull() ?: 0.0
//                val total = qty * price
//
//                val available = getAvailableQuantity(product.id, warehouse.id)
//                if (qty <= 0 || qty > available) {
//                    throw IllegalStateException("Insufficient stock. Available: $available, Requested: $qty")
//                }
//
//                // Calculate weighted average cost
//                val entries = stockEntryRepository.getStockEntries()
//                    .filter { it.productId == product.id && it.warehouseId == warehouse.id && it.quantity > 0 }
//                val totalCost = entries.sumOf { it.costPrice * it.quantity }
//                val totalQuantity = entries.sumOf { it.quantity }
//                val weightedAverageCost = if (totalQuantity > 0) totalCost / totalQuantity else 0.0
//
//                // 1. Create negative stock entry
//                val stockEntry = StockEntry(
//                    productId = product.id,
//                    warehouseId = warehouse.id,
//                    quantity = -qty,
//                    costPrice = weightedAverageCost,
//                    timestamp = Date()
//                )
//                stockEntryRepository.addStockEntry(stockEntry)
//
//                // 2. Create invoice
//                val invoice = Invoice(
//                    customerName = customerName,
//                    customerPhone = customerPhone,
//                    items = listOf(InvoiceItem(
//                        productName = product.name,
//                        quantity = qty,
//                        unitPrice = price,
//                        totalPrice = total
//                    )),
//                    totalAmount = total,
//                    paidAmount = paidAmount,
//                    remainingAmount = total - paidAmount,
//                    status = if (paidAmount >= total) "paid" else "pending"
//                )
//                invoiceRepository.createInvoice(invoice)
//
//                // 3. Clear fields
//                selectedProduct.value = null
//                selectedWarehouse.value = null
//                quantity.value = "1"
//                sellingPrice.value = ""
//                fetchData() // Refresh stock levels
//            } catch (e: Exception) {
//                _errorMessage.value = "Failed to create sale: ${e.message}"
//            }
//        }
//    }
//
//    fun clearError() {
//        _errorMessage.value = null
//    }
//}
