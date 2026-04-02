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
    val isSubmitting: Boolean = false,
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
    private val oldBatteryRepository: OldBatteryRepository,
    private val networkHelper: com.batterysales.utils.NetworkHelper
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
    private val _isSubmitting = MutableStateFlow(false)
    private val _isFinished = MutableStateFlow(false)

    private var allStockEntries: List<StockEntry> = emptyList()
    private var currentUser: User? = null

    val uiState: StateFlow<SalesUiState> = combine(
        productRepository.getProducts(),
        warehouseRepository.getWarehouses(),
        userRepository.getCurrentUserFlow().flatMapLatest { user ->
            if (user?.role == User.ROLE_SELLER && user.warehouseId != null) {
                stockEntryRepository.getStockEntriesByWarehouseFlow(user.warehouseId)
            } else {
                stockEntryRepository.getAllStockEntriesFlow()
            }
        },
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
        _isSubmitting,
        _isFinished,
        productVariantRepository.getAllVariantsFlow(),
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
        val isSubmitting = args[15] as Boolean
        val isFinished = args[16] as Boolean
        val allVariants = args[17] as List<ProductVariant>
        val variants = args[18] as List<ProductVariant>

        allStockEntries = stockEntries
        currentUser = user

        val approvedEntries = stockEntries.filter { it.status == "approved" }
        val entriesByVariant = approvedEntries.groupBy { it.productVariantId }
        val stockMap = mutableMapOf<Pair<String, String>, Int>()

        allVariants.forEach { variant ->
            if (variant.currentStock != null) {
                // Use denormalized stock
                variant.currentStock.forEach { (warehouseId, qty) ->
                    stockMap[Pair(variant.id, warehouseId)] = qty
                }
            } else {
                // Fallback to calculation from entries
                val variantEntries = entriesByVariant[variant.id] ?: emptyList()
                val warehouseGroups = variantEntries.groupBy { it.warehouseId }
                warehouseGroups.forEach { (warehouseId, entries) ->
                    stockMap[Pair(variant.id, warehouseId)] = entries.sumOf { it.quantity - it.returnedQuantity }
                }
            }
        }

        val isSeller = user?.role == User.ROLE_SELLER
        val autoSelectedWarehouse = if (isSeller && selectedWarehouse == null) {
            warehouses.find { it.id == user?.warehouseId }
        } else {
            selectedWarehouse
        }

        val filteredProducts = if (isSeller && user?.warehouseId != null) {
            // Filter products that have any approved stock entry in this warehouse with quantity > 0
            val availableVariantIds = approvedEntries
                .filter { it.warehouseId == user.warehouseId }
                .groupBy { it.productVariantId }
                .filter { (_, entries) -> entries.sumOf { it.quantity - it.returnedQuantity } > 0 }
                .keys

            val availableProductIds = allVariants.filter { availableVariantIds.contains(it.id) }.map { it.productId }.toSet()
            products.filter { !it.archived && availableProductIds.contains(it.id) }
        } else {
            products.filter { !it.archived }
        }

        SalesUiState(
            products = filteredProducts,
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
            isSubmitting = isSubmitting,
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
        if (uiState.value.isSubmitting) return

        val state = uiState.value
        val product = state.selectedProduct
        val variant = state.selectedVariant
        val warehouse = state.selectedWarehouse
        val qty = state.quantity.toIntOrNull() ?: 0
        val price = state.sellingPrice.toDoubleOrNull() ?: 0.0

        // Explicit Field Validation
        if (product == null || variant == null || warehouse == null) {
            _errorMessage.value = "الرجاء اختيار المنتج والصنف والمستودع"
            return
        }
//        if (customerName.isBlank()) {
//            _errorMessage.value = "الرجاء إدخال اسم العميل"
//            return
//        }

        if (qty <= 0) {
            _errorMessage.value = "الرجاء إدخال كمية صحيحة"
            return
        }
//        if (price <= 0) {
//            _errorMessage.value = "الرجاء إدخال سعر البيع"
//            return
//        }

        val available = state.stockLevels[Pair(variant.id, warehouse.id)] ?: 0
        if (qty > available) {
            _errorMessage.value = "المخزون غير كافٍ. المتاح: $available، المطلوب: $qty"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _isSubmitting.value = true

            try {
                if (!warehouse.isActive) {
                    _errorMessage.value = "عذراً، هذا المستودع متوقف حالياً ولا يمكن إجراء عمليات عليه."
                    _isLoading.value = false
                    _isSubmitting.value = false
                    return@launch
                }

                if (!networkHelper.isNetworkConnected()) {
                     _errorMessage.value = "لا يوجد اتصال بالإنترنت. يرجى المحاولة لاحقاً."
                    _isLoading.value = false
                    _isSubmitting.value = false
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

                val stockEntry = StockEntry(
                    productVariantId = variant.id,
                    productName = product.name,
                    capacity = variant.capacity,
                    warehouseId = warehouse.id,
                    quantity = -qty,
                    costPrice = weightedAverageCost,
                    supplier = "Sale",
                    timestamp = Date(),
                    status = "approved",
                    createdBy = currentUser?.id ?: ""
                )

                val payment = if (paidAmount > 0) {
                    Payment(
                        warehouseId = warehouse.id,
                        amount = paidAmount,
                        timestamp = Date(),
                        paymentMethod = state.paymentMethod,
                        notes = "الدفعة الأولى عند البيع"
                    )
                } else null

                val treasuryTransaction = if (paidAmount > 0) {
                    Transaction(
                        type = TransactionType.INCOME,
                        amount = paidAmount,
                        description = "دفعة مبيعات: $customerName",
                        warehouseId = warehouse.id,
                        paymentMethod = state.paymentMethod
                    )
                } else null

                val oldBatteryTransaction = if (newInvoice.oldBatteriesQuantity > 0) {
                    OldBatteryTransaction(
                        quantity = newInvoice.oldBatteriesQuantity,
                        warehouseId = warehouse.id,
                        totalAmperes = newInvoice.oldBatteriesTotalAmperes,
                        type = OldBatteryTransactionType.INTAKE,
                        notes = "مستلم من فاتورة: $customerName",
                        createdByUserName = currentUser?.displayName ?: ""
                    )
                } else null

                invoiceRepository.createFullSale(
                    invoice = newInvoice,
                    stockEntry = stockEntry,
                    payment = payment,
                    treasuryTransaction = treasuryTransaction,
                    oldBatteryTransaction = oldBatteryTransaction
                )

                _isFinished.value = true
                _isSubmitting.value = false
            } catch (e: Exception) {
                Log.e("SalesViewModel", "Error creating sale", e)
                _errorMessage.value = "Failed to create sale: ${e.message}"
                _isLoading.value = false
                _isSubmitting.value = false
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
