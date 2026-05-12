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
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
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

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val user = userRepository.getCurrentUser()
                currentUser = user

                val isSeller = user?.role == User.ROLE_SELLER
                val userWarehouseId = user?.warehouseId

                // --- ELITE STRATEGY: Single Read for Inventory ---
                cachedInventorySummary = summaryRepository.getInventorySummary(if (isSeller) userWarehouseId else null)
                val warehouses = warehouseRepository.getWarehousesOnce()
                val products = productRepository.getProductsOnce()

                val summaryItems = cachedInventorySummary?.items?.values ?: emptyList()
                val availableProductIds = summaryItems.filter { it.currentStock > 0 }.map { it.productId }.toSet()

                val filteredProducts = if (isSeller) {
                    products.filter { !it.archived && availableProductIds.contains(it.id) }
                } else {
                    products.filter { !it.archived }
                }

                _uiState.update {
                    it.copy(
                        products = filteredProducts.sortedBy { p -> p.name },
                        warehouses = warehouses.filter { w -> w.isActive },
                        selectedWarehouse = if (isSeller) warehouses.find { w -> w.id == userWarehouseId } else null,
                        isWarehouseFixed = isSeller,
                        userRole = user?.role ?: "",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("SalesViewModel", "Error loading initial data", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل البيانات") }
            }
        }
    }

    fun onProductSelected(product: Product) {
        // --- ELITE STRATEGY: Get variants from pre-loaded summary map ---
        val summaryItems = cachedInventorySummary?.items?.values ?: emptyList()
        val variantsForProduct = summaryItems.filter { it.productId == product.id }
            .map { item ->
                ProductVariant(
                    id = item.variantId,
                    productId = item.productId,
                    capacity = item.capacity,
                    barcode = item.barcode,
                    sellingPrice = item.sellingPrice,
                    weightedAverageCost = item.weightedAverageCost,
                    productName = item.productName,
                    currentStock = mapOf((cachedInventorySummary?.warehouseId ?: "global") to item.currentStock)
                )
            }.sortedBy { it.capacity }

        val newStockMap = _uiState.value.stockLevels.toMutableMap()
        variantsForProduct.forEach { v ->
            newStockMap[Pair(v.id, cachedInventorySummary?.warehouseId ?: "global")] = v.currentStock?.values?.first() ?: 0
        }

        _uiState.update {
            it.copy(
                selectedProduct = product,
                selectedVariant = null,
                sellingPrice = "",
                variants = variantsForProduct,
                stockLevels = newStockMap
            )
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

                _uiState.update { it.copy(isFinished = true, isSubmitting = false) }
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
        // --- ELITE STRATEGY: Search in pre-loaded summary cache (ZERO Firestore Reads) ---
        val item = cachedInventorySummary?.items?.values?.find { it.barcode == barcode }
        if (item != null) {
            val product = _uiState.value.products.find { it.id == item.productId }
            if (product != null) {
                onProductSelected(product)
                val variant = _uiState.value.variants.find { it.id == item.variantId }
                if (variant != null) onVariantSelected(variant)
                return
            }
        }
        _uiState.update { it.copy(errorMessage = "لم يتم العثور على منتج بهذا الباركود محلياً") }
    }
}
