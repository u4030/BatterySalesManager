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

private data class SalesFormData(
    val product: Product?,
    val variant: ProductVariant?,
    val warehouse: Warehouse?,
    val qty: String,
    val price: String,
    val oldQty: String,
    val oldAmps: String,
    val oldVal: String,
    val payMethod: String
)

private data class SalesStatus(
    val error: String?,
    val loading: Boolean,
    val submitting: Boolean,
    val finished: Boolean
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

    private var currentUser: User? = null

    @Suppress("UNCHECKED_CAST")
    private val formData = combine(
        _selectedProduct, _selectedVariant, _selectedWarehouse,
        _quantity, _sellingPrice, _oldBatteriesQuantity,
        _oldBatteriesTotalAmps, _oldBatteriesValue, _paymentMethod
    ) { args ->
        SalesFormData(
            args[0] as Product?, args[1] as ProductVariant?, args[2] as Warehouse?,
            args[3] as String, args[4] as String, args[5] as String,
            args[6] as String, args[7] as String, args[8] as String
        )
    }

    private val statusData = combine(
        _errorMessage, _isLoading, _isSubmitting, _isFinished
    ) { err, load, sub, fin ->
        SalesStatus(err, load, sub, fin)
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<SalesUiState> = combine(
        productRepository.getProducts(),
        warehouseRepository.getWarehouses(),
        userRepository.getCurrentUserFlow(),
        productVariantRepository.getAllVariantsFlow(),
        formData,
        statusData,
        _selectedProduct.flatMapLatest { product ->
            if (product == null) flowOf(emptyList<ProductVariant>())
            else productVariantRepository.getVariantsForProductFlow(product.id)
                .map { variants -> variants.filter { !it.archived }.sortedBy { it.capacity } }
        }
    ) { args ->
        val products = args[0] as List<Product>
        val warehouses = args[1] as List<Warehouse>
        val user = args[2] as User?
        val allVariants = args[3] as List<ProductVariant>
        val form = args[4] as SalesFormData
        val status = args[5] as SalesStatus
        val productVariants = args[6] as List<ProductVariant>

        currentUser = user

        val stockMap = mutableMapOf<Pair<String, String>, Int>()
        allVariants.forEach { variant ->
            variant.currentStock?.forEach { (warehouseId, qty) ->
                stockMap[Pair(variant.id, warehouseId)] = qty
            }
        }

        val activeSelectedProduct = products.find { it.id == form.product?.id } ?: form.product
        val activeSelectedVariant = allVariants.find { it.id == form.variant?.id } ?: form.variant

        val isSeller = user?.role == User.ROLE_SELLER
        val autoSelectedWarehouse = if (isSeller) {
            warehouses.find { it.id == user?.warehouseId }
        } else {
            form.warehouse
        }

        val filteredProducts = if (isSeller && user?.warehouseId != null) {
            val availableVariantIds = allVariants.filter { v ->
                val qty = v.currentStock?.get(user.warehouseId) ?: 0
                qty > 0
            }.map { it.id }.toSet()

            val availableProductIds = allVariants.filter { availableVariantIds.contains(it.id) }.map { it.productId }.toSet()
            products.filter { !it.archived && (availableProductIds.contains(it.id) || it.id == form.product?.id) }
                .sortedBy { it.name }
        } else {
            products.filter { !it.archived }
                .sortedBy { it.name }
        }

        SalesUiState(
            products = filteredProducts,
            variants = productVariants,
            warehouses = warehouses.filter { it.isActive },
            stockLevels = stockMap,
            selectedProduct = activeSelectedProduct,
            selectedVariant = activeSelectedVariant,
            selectedWarehouse = autoSelectedWarehouse,
            quantity = form.qty,
            sellingPrice = form.price,
            oldBatteriesQuantity = form.oldQty,
            oldBatteriesTotalAmps = form.oldAmps,
            oldBatteriesValue = form.oldVal,
            paymentMethod = form.payMethod,
            isWarehouseFixed = isSeller,
            userRole = user?.role ?: "",
            isLoading = status.loading,
            isSubmitting = status.submitting,
            errorMessage = status.error,
            isFinished = status.finished
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SalesUiState(isLoading = true))

    fun onProductSelected(product: Product) {
        _selectedProduct.value = product
        if (_selectedVariant.value?.productId != product.id) {
            _selectedVariant.value = null
            _sellingPrice.value = ""
        }
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

        if (product == null || variant == null || warehouse == null) {
            _errorMessage.value = "الرجاء اختيار المنتج والصنف والمستودع"
            return
        }

        if (qty <= 0) {
            _errorMessage.value = "الرجاء إدخال كمية صحيحة"
            return
        }

        val available = state.stockLevels[Pair(variant.id, warehouse.id)] ?: 0
        if (qty > available) {
            _errorMessage.value = "المخزون غير كافٍ في مستودع ${warehouse.name}. المتاح: $available، المطلوب: $qty"
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

                val weightedAverageCost = variant.weightedAverageCost

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
            try {
                _isLoading.value = true
                val variant = productVariantRepository.getVariantByBarcode(barcode)
                if (variant != null) {
                    val product = productRepository.getProduct(variant.productId)
                    if (product != null) {
                        _selectedProduct.value = product
                        _selectedVariant.value = variant
                        _sellingPrice.value = if (variant.sellingPrice > 0.0) variant.sellingPrice.toString() else ""
                        _errorMessage.value = null
                    } else {
                        _errorMessage.value = "المنتج المرتبط بهذا الباركود غير موجود"
                    }
                } else {
                    _errorMessage.value = "لم يتم العثور على منتج بهذا الباركود: $barcode"
                }
            } catch (e: Exception) {
                _errorMessage.value = "خطأ أثناء البحث عن الباركود"
                Log.e("SalesViewModel", "Barcode error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
