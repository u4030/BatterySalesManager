package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import android.util.Log
import com.batterysales.data.repositories.OldBatteryRepository
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
    val oldBatteriesQuantity: String = "0",
    val oldBatteriesTotalAmps: String = "0.0",
    val oldBatteriesValue: String = "0.0",
    val paymentMethod: String = "cash",
    val isWarehouseFixed: Boolean = false,
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
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val accountingRepository: AccountingRepository,
    private val oldBatteryRepository: OldBatteryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState(isLoading = true))
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    private var allStockEntries: List<StockEntry> = emptyList()
    private var currentUser: User? = null

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            currentUser = user

            combine(
                productRepository.getProducts(),
                warehouseRepository.getWarehouses(),
                stockEntryRepository.getAllStockEntriesFlow()
            ) { products, warehouses, stockEntries ->

                allStockEntries = stockEntries // Cache for COGS calculation

                // Only count approved entries for available stock
                val approvedEntries = stockEntries.filter { it.status == "approved" }

                val stockMap = mutableMapOf<Pair<String, String>, Int>()
                for (entry in approvedEntries) {
                    val key = Pair(entry.productVariantId, entry.warehouseId)
                    stockMap[key] = (stockMap[key] ?: 0) + (entry.quantity - entry.returnedQuantity)
                }

                val selectedWH = warehouses.find { it.id == user?.warehouseId }

                _uiState.update {
                    it.copy(
                        products = products.filter { p -> !p.archived },
                        warehouses = warehouses.filter { w -> w.isActive },
                        selectedWarehouse = if (user?.role == "seller") selectedWH else it.selectedWarehouse,
                        isWarehouseFixed = user?.role == "seller",
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
                .map { variants -> variants.filter { !it.archived } }
                .onEach { variants ->
                    _uiState.update { it.copy(variants = variants, isLoading = false) }
                }
                .catch { e ->
                    Log.e("SalesViewModel", "Error fetching variants", e)
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

    fun onOldBatteriesQuantityChanged(qty: String) {
        _uiState.update { it.copy(oldBatteriesQuantity = qty) }
    }

    fun onOldBatteriesTotalAmpsChanged(amps: String) {
        _uiState.update { it.copy(oldBatteriesTotalAmps = amps) }
    }

    fun onOldBatteriesValueChanged(value: String) {
        _uiState.update { it.copy(oldBatteriesValue = value) }
    }

    fun onPaymentMethodChanged(method: String) {
        _uiState.update { it.copy(paymentMethod = method) }
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
                if (!warehouse.isActive) {
                    _uiState.update { it.copy(errorMessage = "عذراً، هذا المستودع متوقف حالياً ولا يمكن إجراء عمليات عليه.", isLoading = false) }
                    return@launch
                }

                // --- Correct COGS Calculation ---
                val positiveEntries = allStockEntries.filter {
                    it.productVariantId == variant.id && it.warehouseId == warehouse.id && it.quantity > 0
                }
                val totalCostOfPurchases = positiveEntries.sumOf { it.totalCost }
                val totalItemsPurchased = positiveEntries.sumOf { it.quantity - it.returnedQuantity }
                val weightedAverageCost = if (totalItemsPurchased > 0) totalCostOfPurchases / totalItemsPurchased else 0.0

                // First, create the invoice to get an ID
                val total = qty * price
                val oldBatteriesVal = state.oldBatteriesValue.toDoubleOrNull() ?: 0.0
                val finalTotal = (total - oldBatteriesVal).coerceAtLeast(0.0)

                if (paidAmount > finalTotal) {
                    _uiState.update { it.copy(errorMessage = "المبلغ المدفوع (JD $paidAmount) لا يمكن أن يتجاوز صافي الإجمالي (JD $finalTotal)", isLoading = false) }
                    return@launch
                }

                val newInvoice = Invoice(
                    customerName = customerName,
                    customerPhone = customerPhone,
                    items = listOf(InvoiceItem(
                        productId = variant.id,
                        productName = "${product.name}${if(product.specification.isNotEmpty()) " (${product.specification})" else ""} - ${variant.capacity}A${if(variant.specification.isNotEmpty()) " (${variant.specification})" else ""}",
                        quantity = qty,
                        price = price,
                        total = total,
                        unitPrice = price,
                        totalPrice = total
                    )),
                    subtotal = total,
                    oldBatteriesValue = oldBatteriesVal,
                    oldBatteriesQuantity = state.oldBatteriesQuantity.toIntOrNull() ?: 0,
                    oldBatteriesTotalAmperes = state.oldBatteriesTotalAmps.toDoubleOrNull() ?: 0.0,
                    totalAmount = finalTotal,
                    finalAmount = finalTotal,
                    paidAmount = paidAmount,
                    remainingAmount = finalTotal - paidAmount,
                    status = if (paidAmount >= finalTotal) "paid" else "pending",
                    paymentMethod = state.paymentMethod,
                    warehouseId = warehouse.id,
                    invoiceDate = Date()
                )
                val createdInvoice = invoiceRepository.createInvoice(newInvoice)

                // Record old batteries intake if any
                if (newInvoice.oldBatteriesQuantity > 0) {
                    oldBatteryRepository.addTransaction(
                        OldBatteryTransaction(
                            invoiceId = createdInvoice.id,
                            quantity = newInvoice.oldBatteriesQuantity,
                            warehouseId = warehouse.id,
                            totalAmperes = newInvoice.oldBatteriesTotalAmperes,
                            type = OldBatteryTransactionType.INTAKE,
                            notes = "مستلم من فاتورة: $customerName",
                            createdByUserName = currentUser?.displayName ?: ""
                        )
                    )
                }

                // If there's a paid amount, record it as a payment
                if (paidAmount > 0) {
                    val payment = Payment(
                        invoiceId = createdInvoice.id,
                        warehouseId = warehouse.id,
                        amount = paidAmount,
                        timestamp = Date(),
                        paymentMethod = state.paymentMethod,
                        notes = "الدفعة الأولى عند البيع"
                    )
                    paymentRepository.addPayment(payment)

                    // Record in treasury
                    val transaction = Transaction(
                        type = TransactionType.INCOME,
                        amount = paidAmount,
                        description = "دفعة مبيعات: $customerName",
                        relatedId = createdInvoice.id,
                        warehouseId = warehouse.id,
                        paymentMethod = state.paymentMethod
                    )
                    accountingRepository.addTransaction(transaction)
                }

                // Now, create a single stock entry linked to the new invoice
                val stockEntry = StockEntry(
                    productVariantId = variant.id,
                    productName = product.name,
                    capacity = variant.capacity,
                    warehouseId = warehouse.id,
                    quantity = -qty,
                    costPrice = weightedAverageCost,
                    supplier = "Sale",
                    timestamp = Date(),
                    invoiceId = createdInvoice.id,
                    status = "approved", // Sales auto-approved as requested
                    createdBy = currentUser?.id ?: ""
                )
                stockEntryRepository.addStockEntry(stockEntry)

                _uiState.update { it.copy(isFinished = true) }
            } catch (e: Exception) {
                Log.e("SalesViewModel", "Error creating sale", e)
                _uiState.update { it.copy(errorMessage = "Failed to create sale: ${e.message}", isLoading = false) }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun findProductByBarcode(barcode: String) {
        viewModelScope.launch {
            val variant = productVariantRepository.getVariantByBarcode(barcode)
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
