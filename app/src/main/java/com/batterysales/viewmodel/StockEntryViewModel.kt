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
    val costInputMode: CostInputMode = CostInputMode.BY_AMPERE,
    val costValue: String = "",
    val minQuantity: String = "",
    val supplierName: String = "",
    val stockItems: List<StockEntryItem> = emptyList(),
    val userRole: String = "seller",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
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
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val supplierRepository: SupplierRepository,
    private val userRepository: UserRepository,
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

        userRepository.getCurrentUserFlow()
            .onEach { user ->
                currentUser = user

                // Nest the combine collection inside user change to ensure it reacts to new user role/warehouse
                combine(
                    productRepository.getProducts(),
                    warehouseRepository.getWarehouses(),
                    supplierRepository.getSuppliers(),
                    _selectedSupplier
                ) { products, warehouses, suppliers, selectedSupplier ->
                    val activeProducts = products.filter { !it.archived }

                    val filteredBySupplier = if (selectedSupplier != null) {
                        activeProducts.filter { it.supplierId == selectedSupplier.id || it.supplierId.isBlank() }
                    } else {
                        activeProducts
                    }

                    val selectedWH = warehouses.find { it.id == user?.warehouseId }

                    _uiState.update {
                        it.copy(
                            products = filteredBySupplier,
                            warehouses = warehouses.filter { w -> w.isActive },
                            suppliers = suppliers,
                            userRole = user?.role ?: "seller",
                            selectedWarehouse = if (user?.role == "seller") selectedWH else it.selectedWarehouse,
                            selectedSupplier = selectedSupplier
                        )
                    }

                    if (isEditMode && uiState.value.selectedProduct == null) {
                        loadEntryForEdit(editingEntryId!!)
                    } else if (!isEditMode) {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }.take(1).collect() // Just take initial state for the combined flows per user change
            }.launchIn(viewModelScope)
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
            _uiState.update { it.copy(selectedProduct = product, selectedVariant = null, variants = emptyList(), isLoading = true) }
            try {
                val variants = productVariantRepository.getVariantsForProduct(product.id).filter { !it.archived }
                _uiState.update { it.copy(variants = variants, isLoading = false) }
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
    fun onSupplierSelected(supplier: Supplier) {
        _selectedSupplier.value = supplier
        _uiState.update {
            it.copy(
                supplierName = supplier.name,
                selectedProduct = null,
                selectedVariant = null,
                variants = emptyList()
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
    fun onRemoveItemClicked(item: StockEntryItem) { _uiState.update { it.copy(stockItems = it.stockItems - item) } }
    fun onDismissError() { _uiState.update { it.copy(errorMessage = null) } }

    fun onBarcodeScanned(barcode: String) {
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

    fun onAddItemClicked() {
        val state = uiState.value
        val variant = state.selectedVariant ?: return
        val newItem = calculateItemFromState(state, variant)

        if (newItem.quantity <= 0) {
            _uiState.update { it.copy(errorMessage = "الرجاء إدخال كمية صحيحة") }
            return
        }

        if (state.isAdmin && newItem.costPrice <= 0) {
            _uiState.update { it.copy(errorMessage = "الرجاء إدخال التكلفة") }
            return
        }

        _uiState.update { it.copy(stockItems = it.stockItems + newItem, quantity = "", costValue = "") }
    }

    fun onSaveClicked() {
        viewModelScope.launch {
            val state = uiState.value
            if ((state.isEditMode && state.quantity.isBlank()) || (!state.isEditMode && state.stockItems.isEmpty()) || state.selectedWarehouse == null) {
                _uiState.update { it.copy(errorMessage = "الرجاء اختيار مستودع وإضافة أصناف") }
                return@launch
            }
            try {
                if (state.selectedWarehouse?.isActive == false) {
                    _uiState.update { it.copy(errorMessage = "عذراً، هذا المستودع متوقف حالياً ولا يمكن إجراء عمليات عليه.") }
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
                        supplier = state.supplierName,
                        supplierId = state.selectedSupplier?.id ?: "",
                        invoiceNumber = state.invoiceNumber
                    )
                    stockEntryRepository.updateStockEntry(updatedEntry)

                    // Update variant minQuantity if Admin
                    if (state.isAdmin) {
                        val updatedVariant = state.selectedVariant!!.copy(minQuantity = updatedItem.minQuantity)
                        productVariantRepository.updateVariant(updatedVariant)
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

                    // Update minQuantity for all added variants if Admin
                    if (state.isAdmin) {
                        state.stockItems.forEach { item ->
                            val updatedVariant = item.productVariant.copy(minQuantity = item.minQuantity)
                            productVariantRepository.updateVariant(updatedVariant)
                        }
                    }
                }
                _uiState.update { it.copy(isFinished = true) }
            } catch (e: Exception) {
                Log.e("StockEntryViewModel", "Error saving stock data", e)
                _uiState.update { it.copy(errorMessage = "فشل حفظ البيانات: ${e.message}") }
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
        val totalAmperes = netQuantity * variant.capacity
        val totalCost = netQuantity * costPerItem

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
