package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class SalesUiState(
    val products: List<Product> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val stockLevels: Map<Pair<String, String>, Int> = emptyMap(),
    val selectedProduct: Product? = null,
    val selectedVariant: ProductVariant? = null,
    val selectedWarehouse: Warehouse? = null,
    val quantity: String = "1",
    val sellingPrice: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isFinished: Boolean = false
)

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState(isLoading = true))
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    private var allStockEntries: List<StockEntry> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                productRepository.getProducts(),
                warehouseRepository.getWarehouses(),
                stockEntryRepository.getAllStockEntriesFlow()
            ) { products, warehouses, stockEntries ->

                allStockEntries = stockEntries // Cache for COGS calculation
                val stockMap = mutableMapOf<Pair<String, String>, Int>()
                for (entry in stockEntries) {
                    val key = Pair(entry.productVariantId, entry.warehouseId)
                    stockMap[key] = (stockMap[key] ?: 0) + entry.quantity
                }

                _uiState.update {
                    it.copy(
                        products = products.filter { p -> !p.isArchived },
                        warehouses = warehouses,
                        stockLevels = stockMap,
                        isLoading = false
                    )
                }
            }.collect()
        }
    }

    fun onProductSelected(product: Product) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProduct = product, selectedVariant = null, variants = emptyList(), isLoading = true) }
            productVariantRepository.getVariantsForProductFlow(product.id)
                .map { variants -> variants.filter { !it.isArchived } }
                .onEach { variants ->
                    _uiState.update { it.copy(variants = variants, isLoading = false) }
                }
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = "Failed to fetch variants", isLoading = false) }
                }
                .collect()
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { it.copy(selectedVariant = variant, sellingPrice = variant.sellingPrice.toString()) }
    }

    fun onWarehouseSelected(warehouse: Warehouse) {
        _uiState.update { it.copy(selectedWarehouse = warehouse) }
    }

    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(quantity = quantity) }
    }

    fun onSellingPriceChanged(price: String) {
        _uiState.update { it.copy(sellingPrice = price) }
    }

    fun createSale(customerName: String, customerPhone: String, paidAmount: Double) {
        viewModelScope.launch {
            val state = _uiState.value
            val product = state.selectedProduct ?: return@launch
            val variant = state.selectedVariant ?: return@launch
            val warehouse = state.selectedWarehouse ?: return@launch
            val qty = state.quantity.toIntOrNull() ?: 0
            val price = state.sellingPrice.toDoubleOrNull() ?: 0.0

            val available = state.stockLevels[Pair(variant.id, warehouse.id)] ?: 0
            if (qty <= 0 || qty > available) {
                _uiState.update { it.copy(errorMessage = "Insufficient stock. Available: $available, Requested: $qty") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }
            try {
                // --- Correct COGS Calculation ---
                val positiveEntries = allStockEntries.filter {
                    it.productVariantId == variant.id && it.warehouseId == warehouse.id && it.quantity > 0
                }
                val totalCostOfPurchases = positiveEntries.sumOf { it.totalCost }
                val totalItemsPurchased = positiveEntries.sumOf { it.quantity }
                val weightedAverageCost = if (totalItemsPurchased > 0) totalCostOfPurchases / totalItemsPurchased else 0.0

                // First, create the invoice to get an ID
                val total = qty * price
                val newInvoice = Invoice(
                    customerName = customerName,
                    customerPhone = customerPhone,
                    items = listOf(InvoiceItem(
                        productId = variant.id,
                        productName = "${product.name} - ${variant.capacity} Amp",
                        quantity = qty,
                        price = price,
                        total = total,
                        unitPrice = price,
                        totalPrice = total
                    )),
                    subtotal = total,
                    totalAmount = total,
                    finalAmount = total,
                    paidAmount = paidAmount,
                    remainingAmount = total - paidAmount,
                    status = if (paidAmount >= total) "paid" else "pending"
                )
                val createdInvoice = invoiceRepository.createInvoice(newInvoice)

                // If there's a paid amount, record it as a payment
                if (paidAmount > 0) {
                    val payment = Payment(
                        invoiceId = createdInvoice.id,
                        amount = paidAmount,
                        timestamp = Date(),
                        paymentMethod = "cash",
                        notes = "الدفعة الأولى عند البيع"
                    )
                    paymentRepository.addPayment(payment)
                }

                // Now, create a single stock entry linked to the new invoice
                val stockEntry = StockEntry(
                    productVariantId = variant.id,
                    warehouseId = warehouse.id,
                    quantity = -qty,
                    costPrice = weightedAverageCost,
                    supplier = "Sale",
                    timestamp = Date(),
                    invoiceId = createdInvoice.id
                )
                stockEntryRepository.addStockEntry(stockEntry)

                _uiState.update { it.copy(isFinished = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to create sale: ${e.message}", isLoading = false) }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun findProductByBarcode(barcode: String) {
        viewModelScope.launch {
            val allVariants = productVariantRepository.getAllVariants()
            val variant = allVariants.find { it.barcode == barcode }
            if (variant != null) {
                val product = productRepository.getProduct(variant.productId)
                if (product != null) {
                    onProductSelected(product)
                    onVariantSelected(variant)
                }
            } else {
                _uiState.update { it.copy(errorMessage = "لم يتم العثور على منتج بهذا الباركود") }
            }
        }
    }
}
