package com.batterysales.ui.stockentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

// Represents the state of the UI
data class StockEntryUiState(
    val products: List<Product> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedVariant: ProductVariant? = null,
    val selectedWarehouse: Warehouse? = null,
    val quantity: String = "",
    val costInputMode: CostInputMode = CostInputMode.BY_AMPERE,
    val costValue: String = "",
    val supplierName: String = "",
    val stockItems: List<StockEntryItem> = emptyList(),
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isFinished: Boolean = false
)

// Represents a single item in the stock entry list
data class StockEntryItem(
    val id: String = UUID.randomUUID().toString(),
    val productVariant: ProductVariant,
    val quantity: Int,
    val productName: String,
    val costPrice: Double,
    val costPerAmpere: Double,
    val totalAmperes: Int,
    val totalCost: Double
)

@HiltViewModel
class StockEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockEntryUiState())
    val uiState: StateFlow<StockEntryUiState> = _uiState.asStateFlow()

    private val editingEntryId: String? = savedStateHandle.get<String>("entryId")

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val warehouses = warehouseRepository.getWarehouses()
                val products = productRepository.getProducts().filter { !it.isArchived }
                _uiState.update { it.copy(warehouses = warehouses, products = products) }

                if (editingEntryId != null) {
                    loadEntryForEdit(editingEntryId)
                } else {
                    _uiState.update { it.copy(isLoading = false, isEditMode = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل البيانات الأولية") }
            }
        }
    }

    private suspend fun loadEntryForEdit(entryId: String) {
        val entry = stockEntryRepository.getStockEntryById(entryId)
        if (entry == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "لم يتم العثور على القيد") }
            return
        }

        val variant = productVariantRepository.getVariant(entry.productVariantId)
        val product = variant?.let { productRepository.getProduct(it.productId) }
        val warehouse = uiState.value.warehouses.find { it.id == entry.warehouseId }

        if (variant == null || product == null || warehouse == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل تفاصيل القيد") }
            return
        }

        // Fetch variants for the selected product to have them ready
        val variantsForProduct = productVariantRepository.getVariantsForProduct(product.id)

        _uiState.update {
            it.copy(
                isEditMode = true,
                selectedProduct = product,
                variants = variantsForProduct,
                selectedVariant = variant,
                selectedWarehouse = warehouse,
                quantity = entry.quantity.toString(),
                costValue = entry.costPrice.toString(),
                costInputMode = CostInputMode.BY_ITEM, // Default to item cost for simplicity in edit mode
                supplierName = entry.supplier,
                stockItems = listOf(
                    StockEntryItem(
                        id = entry.id,
                        productVariant = variant,
                        quantity = entry.quantity,
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

    // --- Event Handlers for UI Actions ---

    fun onProductSelected(product: Product) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedProduct = product, selectedVariant = null, variants = emptyList()) }
            try {
                 val variants = productVariantRepository.getVariantsForProduct(product.id).filter { !it.isArchived }
                _uiState.update { it.copy(variants = variants) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تحميل السعات") }
            }
        }
    }

    fun onVariantSelected(variant: ProductVariant) {
        _uiState.update { it.copy(selectedVariant = variant) }
    }

    fun onWarehouseSelected(warehouse: Warehouse) {
        _uiState.update { it.copy(selectedWarehouse = warehouse) }
    }

    fun onQuantityChanged(quantity: String) {
        _uiState.update { it.copy(quantity = quantity) }
    }

    fun onCostInputModeChanged(mode: CostInputMode) {
        _uiState.update { it.copy(costInputMode = mode, costValue = "") }
    }

    fun onCostValueChanged(cost: String) {
        _uiState.update { it.copy(costValue = cost) }
    }

    fun onSupplierNameChanged(name: String) {
        _uiState.update { it.copy(supplierName = name) }
    }

    fun onAddItemClicked() {
        val state = uiState.value
        // Calculate derived values based on current state
        val quantity = state.quantity.toIntOrNull() ?: 0
        val cost = state.costValue.toDoubleOrNull() ?: 0.0
        val variant = state.selectedVariant ?: return

        val costPerItem = if (state.costInputMode == CostInputMode.BY_ITEM) cost else cost * variant.capacity
        val costPerAmpere = if (state.costInputMode == CostInputMode.BY_AMPERE) cost else if (variant.capacity > 0) cost / variant.capacity else 0.0
        val totalAmperes = quantity * variant.capacity
        val totalCost = quantity * costPerItem

        if (quantity <= 0 || costPerItem <= 0) {
            _uiState.update { it.copy(errorMessage = "الرجاء إدخال كمية وتكلفة صحيحة") }
            return
        }

        val newItem = StockEntryItem(
            productVariant = variant,
            quantity = quantity,
            productName = state.selectedProduct?.name ?: "",
            costPrice = costPerItem,
            costPerAmpere = costPerAmpere,
            totalAmperes = totalAmperes,
            totalCost = totalCost
        )
        _uiState.update { it.copy(stockItems = it.stockItems + newItem, quantity = "", costValue = "") }
    }

    fun onRemoveItemClicked(item: StockEntryItem) {
        _uiState.update { it.copy(stockItems = it.stockItems - item) }
    }

    fun onSaveClicked() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.stockItems.isEmpty() || state.selectedWarehouse == null) {
                _uiState.update { it.copy(errorMessage = "الرجاء اختيار مستودع وإضافة أصناف") }
                return@launch
            }

            try {
                if (state.isEditMode) {
                    val originalEntry = stockEntryRepository.getStockEntryById(editingEntryId!!)!!
                    val updatedItem = calculateUpdatedItem()
                    val updatedEntry = originalEntry.copy(
                        quantity = updatedItem.quantity,
                        costPrice = updatedItem.costPrice,
                        costPerAmpere = updatedItem.costPerAmpere,
                        totalAmperes = updatedItem.totalAmperes,
                        totalCost = updatedItem.totalCost,
                        supplier = state.supplierName
                    )
                    stockEntryRepository.updateStockEntry(updatedEntry)
                } else {
                    val grandTotalAmperes = state.stockItems.sumOf { it.totalAmperes }
                    val grandTotalCost = state.stockItems.sumOf { it.totalCost }
                    val entries = state.stockItems.map { item ->
                        StockEntry(
                            productVariantId = item.productVariant.id,
                            warehouseId = state.selectedWarehouse.id,
                            quantity = item.quantity,
                            costPrice = item.costPrice,
                            costPerAmpere = item.costPerAmpere,
                            totalAmperes = item.totalAmperes,
                            totalCost = item.totalCost,
                            grandTotalAmperes = grandTotalAmperes,
                            grandTotalCost = grandTotalCost,
                            timestamp = Date(),
                            supplier = state.supplierName
                        )
                    }
                    stockEntryRepository.addStockEntries(entries)
                }
                _uiState.update { it.copy(isFinished = true) } // Navigate back
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل حفظ البيانات: ${e.message}") }
            }
        }
    }

    // Helper to calculate the final state of the item being edited
    private fun calculateUpdatedItem(): StockEntryItem {
         val state = uiState.value
        val quantity = state.quantity.toIntOrNull() ?: 0
        val cost = state.costValue.toDoubleOrNull() ?: 0.0
        val variant = state.selectedVariant!!

        val costPerItem = if (state.costInputMode == CostInputMode.BY_ITEM) cost else cost * variant.capacity
        val costPerAmpere = if (state.costInputMode == CostInputMode.BY_AMPERE) cost else if (variant.capacity > 0) cost / variant.capacity else 0.0
        val totalAmperes = quantity * variant.capacity
        val totalCost = quantity * costPerItem

        return state.stockItems.first().copy(
            quantity = quantity,
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
                val warehouses = warehouseRepository.getWarehouses()
                _uiState.update { it.copy(warehouses = warehouses) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل إضافة المستودع") }
            }
        }
    }

     fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
