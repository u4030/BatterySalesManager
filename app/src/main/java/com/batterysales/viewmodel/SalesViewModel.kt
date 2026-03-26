package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import android.util.Log
import com.batterysales.data.repositories.OldBatteryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val oldBatteriesQuantity: String = "",
    val oldBatteriesTotalAmps: String = "",
    val oldBatteriesValue: String = "",
    val paymentMethod: String = "cash",
    val isWarehouseFixed: Boolean = false,
    val userRole: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isFinished: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    private val _selectedVariant = MutableStateFlow<ProductVariant?>(null)
    private val _selectedWarehouse = MutableStateFlow<Warehouse?>(null)
    private val _quantity = MutableStateFlow("1")
    private val _sellingPrice = MutableStateFlow("")
    private val _oldBatteriesQuantity = MutableStateFlow("")
    private val _oldBatteriesTotalAmps = MutableStateFlow("")
    private val _oldBatteriesValue = MutableStateFlow("")
    private val _paymentMethod = MutableStateFlow("cash")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isFinished = MutableStateFlow(false)

    private var allStockEntries: List<StockEntry> = emptyList()
    private var currentUser: User? = null

    val uiState: StateFlow<SalesUiState> = combine(
        productRepository.getProducts(),
        warehouseRepository.getWarehouses(),
        stockEntryRepository.getAllStockEntriesFlow(),
        userRepository.getCurrentUserFlow(),
        _selectedProduct,
        _selectedVariant,
        _selectedWarehouse,
        _quantity,
        _sellingPrice,
        _oldBatteriesQuantity,
        _oldBatteriesTotalAmps,
        _oldBatteriesValue,
        _paymentMethod,
        _errorMessage,
        _isLoading,
        _isFinished,
        _selectedProduct.flatMapLatest { product ->
            if (product == null) flowOf(emptyList())
            else productVariantRepository.getVariantsForProductFlow(product.id)
                .map { variants -> variants.filter { !it.archived }.sortedBy { it.capacity } }
        }
    ) { args ->
        val products = args[0] as List<Product>
        val warehouses = args[1] as List<Warehouse>
        val stockEntries = args[2] as List<StockEntry>
        val user = args[3] as User?
        val selectedProduct = args[4] as Product?
        val selectedVariant = args[5] as ProductVariant?
        val selectedWarehouse = args[6] as Warehouse?
        val quantity = args[7] as String
        val sellingPrice = args[8] as String
        val oldBatteriesQuantity = args[9] as String
        val oldBatteriesTotalAmps = args[10] as String
        val oldBatteriesValue = args[11] as String
        val paymentMethod = args[12] as String
        val errorMessage = args[13] as String?
        val isLoading = args[14] as Boolean
        val isFinished = args[15] as Boolean
        val variants = args[16] as List<ProductVariant>

        allStockEntries = stockEntries
        currentUser = user

        val approvedEntries = stockEntries.filter { it.status == "approved" }
        val stockMap = mutableMapOf<Pair<String, String>, Int>()
        for (entry in approvedEntries) {
            val key = Pair(entry.productVariantId, entry.warehouseId)
            stockMap[key] = (stockMap[key] ?: 0) + (entry.quantity - entry.returnedQuantity)
        }

        val isSeller = user?.role == "seller"
        val autoSelectedWarehouse = if (isSeller && selectedWarehouse == null) {
            warehouses.find { it.id == user?.warehouseId }
        } else {
            selectedWarehouse
        }

        SalesUiState(
            products = products.filter { !it.archived },
            variants = variants,
            warehouses = warehouses.filter { it.isActive },
            stockLevels = stockMap,
            selectedProduct = selectedProduct,
            selectedVariant = selectedVariant,
            selectedWarehouse = autoSelectedWarehouse,
            quantity = quantity,
            sellingPrice = sellingPrice,
            oldBatteriesQuantity = oldBatteriesQuantity,
            oldBatteriesTotalAmps = oldBatteriesTotalAmps,
            oldBatteriesValue = oldBatteriesValue,
            paymentMethod = paymentMethod,
            isWarehouseFixed = isSeller,
            userRole = user?.role ?: "",
            isLoading = isLoading,
            errorMessage = errorMessage,
            isFinished = isFinished
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SalesUiState(isLoading = true))

    fun onProductSelected(product: Product) {
        _selectedProduct.value = product
        _selectedVariant.value = null
        _sellingPrice.value = ""
    }

    fun onVariantSelected(variant: ProductVariant) {
        _selectedVariant.value = variant
        _sellingPrice.value = if (variant.sellingPrice > 0.0) variant.sellingPrice.toString() else ""
    }

    fun onWarehouseSelected(warehouse: Warehouse) {
        _selectedWarehouse.value = warehouse
    }

    fun onQuantityChanged(quantity: String) {
        _quantity.value = quantity
    }

    fun onSellingPriceChanged(price: String) {
        _sellingPrice.value = price
    }

    fun onOldBatteriesQuantityChanged(qty: String) {
        _oldBatteriesQuantity.value = qty
    }

    fun onOldBatteriesTotalAmpsChanged(amps: String) {
        _oldBatteriesTotalAmps.value = amps
    }

    fun onOldBatteriesValueChanged(value: String) {
        _oldBatteriesValue.value = value
    }

    fun onPaymentMethodChanged(method: String) {
        _paymentMethod.value = method
    }

    fun createSale(customerName: String, customerPhone: String, paidAmount: Double) {
        viewModelScope.launch {
            val state = uiState.value
            val product = state.selectedProduct ?: return@launch
            val variant = state.selectedVariant ?: return@launch
            val warehouse = state.selectedWarehouse ?: return@launch
            val qty = state.quantity.toIntOrNull() ?: 0
            val price = state.sellingPrice.toDoubleOrNull() ?: 0.0

            val available = state.stockLevels[Pair(variant.id, warehouse.id)] ?: 0
            if (qty <= 0 || qty > available) {
                _errorMessage.value = "Insufficient stock. Available: $available, Requested: $qty"
                return@launch
            }

            _isLoading.value = true
            try {
                if (!warehouse.isActive) {
                    _errorMessage.value = "عذراً، هذا المستودع متوقف حالياً ولا يمكن إجراء عمليات عليه."
                    _isLoading.value = false
                    return@launch
                }

                val positiveEntries = allStockEntries.filter {
                    it.productVariantId == variant.id && it.warehouseId == warehouse.id && it.quantity > 0
                }
                val totalCostOfPurchases = positiveEntries.sumOf { it.totalCost }
                val totalItemsPurchased = positiveEntries.sumOf { it.quantity - it.returnedQuantity }
                val weightedAverageCost = if (totalItemsPurchased > 0) totalCostOfPurchases / totalItemsPurchased else 0.0

                val total = qty * price
                val oldBatteriesVal = state.oldBatteriesValue.toDoubleOrNull() ?: 0.0
                val finalTotal = (total - oldBatteriesVal).coerceAtLeast(0.0)

                if (paidAmount > finalTotal) {
                    _errorMessage.value = "المبلغ المدفوع (JD $paidAmount) لا يمكن أن يتجاوز صافي الإجمالي (JD $finalTotal)"
                    _isLoading.value = false
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
                    status = "approved",
                    createdBy = currentUser?.id ?: ""
                )
                stockEntryRepository.addStockEntry(stockEntry)

                _isFinished.value = true
            } catch (e: Exception) {
                Log.e("SalesViewModel", "Error creating sale", e)
                _errorMessage.value = "Failed to create sale: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun onDismissError() {
        _errorMessage.value = null
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
                _errorMessage.value = "لم يتم العثور على منتج بهذا الباركود"
            }
        }
    }
}
