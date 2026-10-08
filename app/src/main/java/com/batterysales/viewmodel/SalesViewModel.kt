package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import android.util.Log
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
    val oldBatteriesQuantity: String = "",
    val oldBatteriesTotalAmps: String = "",
    val oldBatteriesValue: String = "",
    val paymentMethod: String = "cash",
    val isWarehouseFixed: Boolean = false,
    val userRole: String = "",
    val userWarehouseId: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isFinished: Boolean = false
)

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val summaryRepository: SummaryRepository,
    private val invoiceRepository: InvoiceRepository,
    private val userRepository: UserRepository,
    private val oldBatteryRepository: OldBatteryRepository,
    private val networkHelper: com.batterysales.utils.NetworkHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState(isLoading = true))
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    private var currentUser: User? = null
    private var cachedInventorySummary: InventorySummary? = null
    private var summaryJob: kotlinx.coroutines.Job? = null

    init {
        loadInitialData()
    }

    private fun observeInventorySummary(warehouseId: String?) {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            summaryRepository.getInventorySummaryFlow(warehouseId)
                .onEach { summary ->
                    cachedInventorySummary = summary

                    val user = currentUser
                    val isSeller = user?.role == User.ROLE_SELLER
                    val summaryItems = summary.items.values
                    val availableProductIds = summaryItems.filter { it.currentStock > 0 }.map { it.productId }.toSet()

                    val currentProducts = productRepository.getProductsOnce()
                    val filteredProducts = if (isSeller && availableProductIds.isNotEmpty()) {
                        currentProducts.filter { !it.archived && availableProductIds.contains(it.id) }
                    } else {
                        currentProducts.filter { !it.archived }
                    }

                    _uiState.update { state ->
                        state.copy(
                            products = filteredProducts.sortedBy { p -> p.name }
                        )
                    }

                    val selectedProd = _uiState.value.selectedProduct
                    if (selectedProd != null) {
                        loadVariantsForProduct(selectedProd, _uiState.value.selectedVariant?.id)
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val user = userRepository.getCurrentUser()
                currentUser = user

                val isSeller = user?.role == User.ROLE_SELLER
                val userWarehouseId = user?.warehouseId ?: ""
                val warehouses = warehouseRepository.getWarehousesOnce()
                val products = productRepository.getProductsOnce()

                val activeWarehouses = warehouses.filter { it.isActive }
                val selectedWh = if (isSeller) activeWarehouses.find { w -> w.id == userWarehouseId } else activeWarehouses.firstOrNull()
                val initialWhId = selectedWh?.id ?: userWarehouseId.ifEmpty { null }

                _uiState.update {
                    it.copy(
                        products = products.filter { !it.archived }.sortedBy { p -> p.name },
                        warehouses = activeWarehouses,
                        selectedWarehouse = selectedWh,
                        isWarehouseFixed = isSeller,
                        userRole = user?.role ?: "",
                        userWarehouseId = userWarehouseId,
                        isLoading = false
                    )
                }

                observeInventorySummary(initialWhId)
            } catch (e: Exception) {
                Log.e("SalesViewModel", "Error loading initial data", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل البيانات") }
            }
        }
    }

    fun onProductSelected(product: Product) {
        viewModelScope.launch {
            loadVariantsForProduct(product)
        }
    }

    private suspend fun loadVariantsForProduct(product: Product, targetVariantId: String? = null) {
        try {
            val selectedWhId = _uiState.value.selectedWarehouse?.id ?: currentUser?.warehouseId ?: ""
            
            var variantsForProduct = productVariantRepository.getVariantsForProduct(product.id)
                .filter { !it.archived && !it.isDiscontinued }
                .sortedBy { it.capacity }

            val summaryItems = cachedInventorySummary?.items ?: emptyMap()

            val variantsWithStock = variantsForProduct.map { variant ->
                val stockFromSummary = summaryItems[variant.id]?.currentStock
                val stockFromVariantMap = (variant.currentStock?.get(selectedWhId) as? Number)?.toInt()
                val finalStock = stockFromSummary ?: stockFromVariantMap ?: 0

                val newStockMap = (variant.currentStock ?: emptyMap()).toMutableMap()
                newStockMap[selectedWhId] = finalStock

                variant.copy(
                    sellingPrice = if (variant.sellingPrice > 0.0) variant.sellingPrice else (summaryItems[variant.id]?.sellingPrice ?: 0.0),
                    weightedAverageCost = if (variant.weightedAverageCost > 0.0) variant.weightedAverageCost else (summaryItems[variant.id]?.weightedAverageCost ?: 0.0),
                    currentStock = newStockMap
                )
            }

            val userWhId = currentUser?.warehouseId ?: ""
            val filteredVariants = if (currentUser?.role == User.ROLE_SELLER && userWhId.isNotEmpty()) {
                variantsWithStock.filter { (it.currentStock?.get(userWhId) ?: 0) > 0 || variantsWithStock.size <= 2 }
            } else {
                variantsWithStock
            }

            val newStockMap = _uiState.value.stockLevels.toMutableMap()
            filteredVariants.forEach { v ->
                val qty = v.currentStock?.get(selectedWhId) ?: 0
                newStockMap[Pair(v.id, selectedWhId)] = qty
            }

            val currentSelected = _uiState.value.selectedVariant
            val selectedVar = when {
                targetVariantId != null -> filteredVariants.find { it.id == targetVariantId }
                currentSelected != null -> filteredVariants.find { it.id == currentSelected.id }
                else -> null
            }

            _uiState.update { 
                it.copy(
                    selectedProduct = product,
                    variants = filteredVariants, 
                    stockLevels = newStockMap,
                    selectedVariant = selectedVar,
                    sellingPrice = selectedVar?.let { sv -> if (sv.sellingPrice > 0.0) sv.sellingPrice.toString() else "" } ?: it.sellingPrice,
                    isLoading = false 
                ) 
            }
        } catch (e: Exception) {
            Log.e("SalesViewModel", "Error loading variants", e)
            _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل السعات") }
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { 
            it.copy(
                selectedVariant = variant,
                sellingPrice = if (variant.sellingPrice > 0.0) variant.sellingPrice.toString() else ""
            )
        }
    }

    fun onWarehouseSelected(warehouse: Warehouse) {
        if (_uiState.value.selectedWarehouse?.id == warehouse.id) return
        _uiState.update { it.copy(selectedWarehouse = warehouse) }
        observeInventorySummary(warehouse.id)
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
        if (uiState.value.isSubmitting) return

        val state = uiState.value
        val product = state.selectedProduct
        val variant = state.selectedVariant
        val warehouse = state.selectedWarehouse
        val qty = state.quantity.toIntOrNull() ?: 0
        val price = state.sellingPrice.toDoubleOrNull() ?: 0.0

        if (product == null || variant == null || warehouse == null) {
            _uiState.update { it.copy(errorMessage = "الرجاء اختيار المنتج والصنف والمستودع") }
            return
        }

        if (qty <= 0) {
            _uiState.update { it.copy(errorMessage = "الرجاء إدخال كمية صحيحة") }
            return
        }

        val available = state.stockLevels[Pair(variant.id, warehouse.id)] ?: 0
        if (qty > available) {
            _uiState.update { it.copy(errorMessage = "المخزون غير كافٍ في مستودع ${warehouse.name}. المتاح: $available، المطلوب: $qty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            try {
                if (!warehouse.isActive) {
                    _uiState.update { it.copy(errorMessage = "عذراً، هذا المستودع متوقف حالياً ولا يمكن إجراء عمليات عليه.", isSubmitting = false) }
                    return@launch
                }

                if (!networkHelper.isNetworkConnected()) {
                    _uiState.update { it.copy(errorMessage = "لا يوجد اتصال بالإنترنت. يرجى المحاولة لاحقاً.", isSubmitting = false) }
                    return@launch
                }

                val weightedAverageCost = variant.weightedAverageCost
                val total = qty * price
                val oldBatteriesVal = state.oldBatteriesValue.toDoubleOrNull() ?: 0.0
                val finalTotal = (total - oldBatteriesVal).coerceAtLeast(0.0)

                if (paidAmount > finalTotal) {
                    _uiState.update { it.copy(errorMessage = "المبلغ المدفوع (JD $paidAmount) لا يمكن أن يتجاوز صافي الإجمالي (JD $finalTotal)", isSubmitting = false) }
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

                _uiState.update { it.copy(successMessage = "تم الحفظ والمزامنة بنجاح ✅", isSubmitting = false) }
                kotlinx.coroutines.delay(1500)
                _uiState.update { it.copy(isFinished = true) }
            } catch (e: Exception) {
                Log.e("SalesViewModel", "Error creating sale", e)
                _uiState.update { it.copy(errorMessage = "Failed to create sale: ${e.message}", isSubmitting = false) }
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun findProductByBarcode(barcode: String) {
        viewModelScope.launch {
            val item = cachedInventorySummary?.items?.values?.find { it.barcode == barcode }
            if (item != null) {
                val product = _uiState.value.products.find { it.id == item.productId }
                if (product != null) {
                    loadVariantsForProduct(product, targetVariantId = item.variantId)
                    return@launch
                }
            }
            
            try {
                _uiState.update { it.copy(isLoading = true) }
                val variant = productVariantRepository.getVariantByBarcode(barcode)
                if (variant != null) {
                    val product = productRepository.getProduct(variant.productId)
                    if (product != null) {
                        loadVariantsForProduct(product, targetVariantId = variant.id)
                    } else {
                        _uiState.update { it.copy(errorMessage = "المنتج المرتبط بهذا الباركود غير موجود", isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = "لم يتم العثور على منتج بهذا الباركود محلياً أو في السحابة", isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "خطأ أثناء البحث عن الباركود", isLoading = false) }
            }
        }
    }
}
 
