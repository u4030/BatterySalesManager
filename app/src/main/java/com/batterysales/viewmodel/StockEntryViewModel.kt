package com.batterysales.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

// Represents the state of the UI
data class StockEntryUiState(
    val products: List<Product> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedVariant: ProductVariant? = null,
    val selectedWarehouse: Warehouse? = null,
    val selectedSupplier: Supplier? = null,
    val quantity: String = "",
    val returnedQuantity: String = "0",
    val invoiceNumber: String = "",
    val invoiceDate: Date? = null,
    val costInputMode: CostInputMode = CostInputMode.BY_AMPERE,
    val costValue: String = "",
    val minQuantity: String = "",
    val supplierName: String = "",
    val paymentAmount: String = "",
    val paymentMethod: String = "cash", // cash, transfer
    val stockItems: List<StockEntryItem> = emptyList(),
    val userRole: String = "seller",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isFinished: Boolean = false
) {
    val isAdmin: Boolean get() = userRole == "admin"
}

// Represents a single item in the stock entry list
data class StockEntryItem(
    val id: String = UUID.randomUUID().toString(),
    val productVariant: ProductVariant,
    val quantity: Int,
    val minQuantity: Int,
    val productName: String,
    val costPrice: Double,
    val costPerAmpere: Double,
    val totalAmperes: Int,
    val totalCost: Double
)

enum class CostInputMode {
    BY_AMPERE,
    BY_ITEM
}

@HiltViewModel
class StockEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val summaryRepository: com.batterysales.data.repositories.SummaryRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val supplierRepository: SupplierRepository,
    private val billRepository: BillRepository,
    private val userRepository: UserRepository,
    private val networkHelper: com.batterysales.utils.NetworkHelper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockEntryUiState())
    val uiState: StateFlow<StockEntryUiState> = _uiState.asStateFlow()

    private val _selectedSupplier = MutableStateFlow<Supplier?>(null)

    private val editingEntryId: String? = savedStateHandle.get<String>("entryId")
    private var currentUser: User? = null

    init {
        val isEditMode = editingEntryId != null
        _uiState.update { it.copy(isEditMode = isEditMode) }

        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            currentUser = user
            
            // One-time fetch for initialization instead of persistent listeners
            val products = productRepository.getProductsOnce()
            val warehouses = warehouseRepository.getWarehousesOnce()
            val suppliers = supplierRepository.getSuppliersOnce()

            val activeProducts = products.filter { !it.archived }.sortedBy { it.name }
            val selectedWH = warehouses.find { it.id == user?.warehouseId }

            _uiState.update {
                it.copy(
                    products = activeProducts,
                    warehouses = warehouses.filter { w -> w.isActive },
                    suppliers = suppliers,
                    userRole = user?.role ?: "seller",
                    selectedWarehouse = if (user?.role == "seller") selectedWH else it.selectedWarehouse
                )
            }

            if (isEditMode) {
                loadEntryForEdit(editingEntryId!!)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadEntryForEdit(entryId: String) {
        _uiState.update { it.copy(isLoading = true) }
        val entry = stockEntryRepository.getStockEntryById(entryId)
        if (entry == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "لم يتم العثور على القيد") }
            return
        }

        val variant = productVariantRepository.getVariant(entry.productVariantId)
        val product = variant?.let { uiState.value.products.find { p -> p.id == it.productId } }
        val warehouse = uiState.value.warehouses.find { it.id == entry.warehouseId }
        val supplier = uiState.value.suppliers.find { it.id == entry.supplierId }

        if (variant == null || product == null || warehouse == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل تفاصيل القيد") }
            return
        }

        val variantsForProduct = productVariantRepository.getVariantsForProduct(product.id)

        _uiState.update {
            it.copy(
                selectedProduct = product,
                variants = variantsForProduct,
                selectedVariant = variant,
                selectedWarehouse = warehouse,
                selectedSupplier = supplier,
                invoiceNumber = entry.invoiceNumber,
                invoiceDate = entry.invoiceDate,
                quantity = entry.quantity.toString(),
                returnedQuantity = entry.returnedQuantity.toString(),
                costValue = entry.costPrice.toString(),
                minQuantity = variant.minQuantity.toString(),
                costInputMode = CostInputMode.BY_ITEM,
                supplierName = entry.supplier,
                stockItems = listOf(
                    StockEntryItem(
                        id = entry.id,
                        productVariant = variant,
                        quantity = entry.quantity,
                        minQuantity = variant.minQuantity,
                        productName = product.name,
                        costPrice = entry.costPrice,
                        costPerAmpere = entry.costPerAmpere,
                        totalAmperes = entry.totalAmperes,
                        totalCost = entry.totalCost
                    )
                ),
                isLoading = false
            )
        }
    }

    fun onProductSelected(product: Product) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedProduct = product,
                    selectedVariant = null,
                    variants = emptyList(),
                    isLoading = false,
                    quantity = if (it.isEditMode) it.quantity else "",
                    costValue = if (it.isEditMode) it.costValue else ""
                )
            }
            try {
                val variants = productVariantRepository.getVariantsForProduct(product.id).filter { !it.archived }.sortedBy { it.capacity }
                _uiState.update { it.copy(variants = variants) }
            } catch (e: Exception) {
                Log.e("StockEntryViewModel", "Error fetching variants", e)
                _uiState.update { it.copy(errorMessage = "فشل تحميل السعات", isLoading = false) }
            }
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { it.copy(selectedVariant = variant, minQuantity = variant.minQuantity.toString()) }
    }
    fun onWarehouseSelected(warehouse: Warehouse) { _uiState.update { it.copy(selectedWarehouse = warehouse) } }
    fun onSupplierSelected(supplier: Supplier?) {
        _selectedSupplier.value = supplier
        _uiState.update {
            it.copy(
                selectedSupplier = supplier,
                supplierName = supplier?.name ?: "",
                selectedProduct = if (it.isEditMode) it.selectedProduct else null,
                selectedVariant = if (it.isEditMode) it.selectedVariant else null,
                variants = if (it.isEditMode) it.variants else emptyList()
            )
        }
    }
    fun onQuantityChanged(quantity: String) { _uiState.update { it.copy(quantity = quantity) } }
    fun onReturnedQuantityChanged(qty: String) { _uiState.update { it.copy(returnedQuantity = qty) } }
    fun onMinQuantityChanged(minQty: String) { _uiState.update { it.copy(minQuantity = minQty) } }
    fun onCostInputModeChanged(mode: CostInputMode) { _uiState.update { it.copy(costInputMode = mode, costValue = "") } }
    fun onCostValueChanged(cost: String) {
        _uiState.update { it.copy(costValue = cost) }
    }

    private fun parseCurrency(input: String): Double {
        if (input.count { it == '.' } > 1) {
            val parts = input.split(".")
            val jd = parts.getOrNull(0) ?: "0"
            val qirsh = (parts.getOrNull(1) ?: "00").padStart(2, '0')
            val fils = (parts.getOrNull(2) ?: "00").padStart(2, '0')

            val sanitized = "$jd.${qirsh}${fils}"
            return sanitized.toDoubleOrNull() ?: 0.0
        }
        return input.toDoubleOrNull() ?: 0.0
    }
    fun onSupplierNameChanged(name: String) { _uiState.update { it.copy(supplierName = name) } }
    fun onInvoiceNumberChanged(number: String) { _uiState.update { it.copy(invoiceNumber = number) } }
    fun onInvoiceDateChanged(date: Date) { _uiState.update { it.copy(invoiceDate = date) } }
    fun onPaymentAmountChanged(amount: String) { _uiState.update { it.copy(paymentAmount = amount) } }
    fun onPaymentMethodChanged(method: String) { _uiState.update { it.copy(paymentMethod = method) } }
    fun onRemoveItemClicked(item: StockEntryItem) { _uiState.update { it.copy(stockItems = it.stockItems - item) } }
    fun onDismissError() { _uiState.update { it.copy(errorMessage = null) } }

    fun onBarcodeScanned(barcode: String) {
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

    fun onAddItemClicked() {
        val state = uiState.value
        val variant = state.selectedVariant ?: return
        val newItem = calculateItemFromState(state, variant)

        if (newItem.quantity <= 0) {
            _uiState.update { it.copy(errorMessage = "الرجاء إدخال كمية صحيحة") }
            return
        }

        if (state.isAdmin && (newItem.costPrice <= 0 || newItem.costPerAmpere <= 0)) {
            _uiState.update { it.copy(errorMessage = "الرجاء إدخال التكلفة") }
            return
        }

        _uiState.update { it.copy(stockItems = it.stockItems + newItem, quantity = "", costValue = "") }
    }

    fun onSaveClicked() {
        if (uiState.value.isSubmitting) return

        val state = uiState.value
        // Validation
        if (state.selectedWarehouse == null) {
            _uiState.update { it.copy(errorMessage = "الرجاء اختيار مستودع") }
            return
        }
        if (state.isEditMode) {
            if (state.quantity.isBlank() || (state.quantity.toIntOrNull() ?: 0) <= 0) {
                _uiState.update { it.copy(errorMessage = "الرجاء إدخال كمية صحيحة") }
                return
            }
        } else {
            if (state.stockItems.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "الرجاء إضافة أصناف للقائمة") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            try {
                if (state.selectedWarehouse?.isActive == false) {
                    _uiState.update { it.copy(errorMessage = "عذراً، هذا المستودع متوقف حالياً ولا يمكن إجراء عمليات عليه.", isSubmitting = false) }
                    return@launch
                }

                if (!networkHelper.isNetworkConnected()) {
                    _uiState.update { it.copy(errorMessage = "لا يوجد اتصال بالإنترنت. يرجى المحاولة لاحقاً.", isSubmitting = false) }
                    return@launch
                }

                if (state.isEditMode) {
                    val originalEntry = stockEntryRepository.getStockEntryById(editingEntryId!!)!!
                    val updatedItem = calculateItemFromState(state, state.selectedVariant!!)

                    if (state.isAdmin && updatedItem.costPrice <= 0) {
                        _uiState.update { it.copy(errorMessage = "الرجاء إدخال سعر الأمبير أو تكلفة القطعة") }
                        return@launch
                    }

                    val returnedQty = state.returnedQuantity.toIntOrNull() ?: 0

                    val updatedEntry = originalEntry.copy(
                        quantity = updatedItem.quantity,
                        productName = updatedItem.productName,
                        capacity = state.selectedVariant!!.capacity,
                        returnedQuantity = returnedQty,
                        returnDate = if (returnedQty > 0) (originalEntry.returnDate ?: Date()) else null,
                        costPrice = updatedItem.costPrice,
                        costPerAmpere = updatedItem.costPerAmpere,
                        totalAmperes = updatedItem.totalAmperes,
                        totalCost = updatedItem.totalCost,
                        timestamp = originalEntry.timestamp,
                        invoiceDate = state.invoiceDate,
                        supplier = state.supplierName,
                        supplierId = state.selectedSupplier?.id ?: "",
                        invoiceNumber = state.invoiceNumber
                    )
                    stockEntryRepository.updateStockEntry(updatedEntry)
                    
                    // تحديث الروابط التلقائية للمورد
                    if (updatedEntry.supplierId.isNotEmpty()) {
                        billRepository.autoLinkBillsForSupplier(updatedEntry.supplierId)
                    }

                    // Update variant minQuantity if Admin
                    if (state.isAdmin) {
                        val updatedVariant = state.selectedVariant!!.copy(minQuantity = updatedItem.minQuantity)
                        productVariantRepository.updateVariant(updatedVariant, summaryRepository)
                    }
                } else {
                    val grandTotalAmperes = state.stockItems.sumOf { it.totalAmperes }
                    val grandTotalCost = state.stockItems.sumOf { it.totalCost }
                    val orderId = UUID.randomUUID().toString()
                    val now = Date()
                    val entries = state.stockItems.map { item ->
                        StockEntry(
                            productVariantId = item.productVariant.id,
                            productName = item.productName,
                            capacity = item.productVariant.capacity,
                            warehouseId = state.selectedWarehouse.id,
                            quantity = item.quantity,
                            costPrice = item.costPrice,
                            costPerAmpere = item.costPerAmpere,
                            totalAmperes = item.totalAmperes,
                            totalCost = item.totalCost,
                            grandTotalAmperes = grandTotalAmperes,
                            grandTotalCost = grandTotalCost,
                            timestamp = now,
                            invoiceDate = state.invoiceDate,
                            supplier = state.supplierName,
                            supplierId = state.selectedSupplier?.id ?: "",
                            invoiceNumber = state.invoiceNumber,
                            orderId = orderId,
                            status = if (currentUser?.role == "seller") "pending" else "approved",
                            createdBy = currentUser?.id ?: "",
                            createdByUserName = currentUser?.displayName ?: ""
                        )
                    }
                    stockEntryRepository.addStockEntries(entries)
                    
                    // Handle immediate payment if specified (Admin only or based on UI)
                    val payAmount = state.paymentAmount.toDoubleOrNull() ?: 0.0
                    val supplierId = entries.firstOrNull()?.supplierId
                    if (payAmount > 0.001 && !supplierId.isNullOrEmpty()) {
                        val bill = Bill(
                            description = "دفعة نقدية مع الطلبية: ${state.invoiceNumber}",
                            amount = payAmount,
                            dueDate = state.invoiceDate ?: now,
                            billType = if (state.paymentMethod == "transfer") BillType.TRANSFER else BillType.CASH,
                            supplierId = supplierId,
                            warehouseId = state.selectedWarehouse.id,
                            status = BillStatus.PAID,
                            paidAmount = payAmount,
                            paidDate = now,
                            referenceNumber = state.invoiceNumber
                        )
                        billRepository.addBill(bill)
                    } else if (!supplierId.isNullOrEmpty()) {
                        // Trigger FIFO anyway to use existing credits
                        billRepository.autoLinkBillsForSupplier(supplierId)
                    }

                    // Update minQuantity for all added variants if Admin
                    if (state.isAdmin) {
                        state.stockItems.forEach { item ->
                            val updatedVariant = item.productVariant.copy(minQuantity = item.minQuantity)
                            productVariantRepository.updateVariant(updatedVariant, summaryRepository)
                        }
                    }
                }
                _uiState.update { it.copy(isFinished = true, isSubmitting = false) }
            } catch (e: Exception) {
                Log.e("StockEntryViewModel", "Error saving stock data", e)
                _uiState.update { it.copy(errorMessage = "فشل حفظ البيانات: ${e.message}", isSubmitting = false) }
            }
        }
    }

    private fun calculateItemFromState(state: StockEntryUiState, variant: ProductVariant): StockEntryItem {
        val originalQuantity = state.quantity.toIntOrNull() ?: 0
        val returnedQty = state.returnedQuantity.toIntOrNull() ?: 0
        val netQuantity = originalQuantity - returnedQty

        val minQuantity = state.minQuantity.toIntOrNull() ?: 0
        val cost = parseCurrency(state.costValue)

        val costPerItem = if (state.costInputMode == CostInputMode.BY_ITEM) cost else if (variant.capacity > 0) cost * variant.capacity else 0.0
        val costPerAmpere = if (state.costInputMode == CostInputMode.BY_AMPERE) cost else if (variant.capacity > 0) cost / variant.capacity else 0.0
        
        // totalCost should represent the GROSS value of the purchase for accounting purposes
        // Inventory levels are handled separately via quantity and returnedQuantity
        val totalAmperes = originalQuantity * variant.capacity
        val totalCost = originalQuantity * costPerItem

        return StockEntryItem(
            id = if(state.isEditMode) state.stockItems.first().id else UUID.randomUUID().toString(),
            productVariant = variant,
            quantity = originalQuantity,
            minQuantity = minQuantity,
            productName = state.selectedProduct?.name ?: "",
            costPrice = costPerItem,
            costPerAmpere = costPerAmpere,
            totalAmperes = totalAmperes,
            totalCost = totalCost
        )
    }

    fun onAddWarehouse(name: String) {
        viewModelScope.launch {
            try {
                warehouseRepository.addWarehouse(Warehouse(name = name, location = ""))
            } catch (e: Exception) {
                Log.e("StockEntryViewModel", "Error adding warehouse", e)
                _uiState.update { it.copy(errorMessage = "فشل إضافة المستودع") }
            }
        }
    }

    fun onAddSupplier(name: String, target: Double) {
        viewModelScope.launch {
            try {
                supplierRepository.addSupplier(Supplier(name = name, yearlyTarget = target))
            } catch (e: Exception) {
                Log.e("StockEntryViewModel", "Error adding supplier", e)
                _uiState.update { it.copy(errorMessage = "فشل إضافة المورد") }
            }
        }
    }
}
 
