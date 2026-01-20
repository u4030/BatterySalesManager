package com.batterysales.ui.stockentry

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class StockEntryItem(
    // Unique ID for the list item, can be the same as StockEntry.id in edit mode
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
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val products = mutableStateOf<List<Product>>(emptyList())
    val variants = mutableStateOf<List<ProductVariant>>(emptyList())
    val warehouses = mutableStateOf<List<Warehouse>>(emptyList())
    val stockItems = mutableStateListOf<StockEntryItem>()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val editingEntryId = savedStateHandle.get<String>("entryId")
    val isEditMode = editingEntryId != null

    init {
        fetchProducts()
        fetchWarehouses()
        if (isEditMode && editingEntryId != null) {
            loadEntryForEdit(editingEntryId)
        }
    }

    private fun loadEntryForEdit(entryId: String) {
        viewModelScope.launch {
            try {
                val entry = stockEntryRepository.getStockEntryById(entryId)
                if (entry != null) {
                    val variant = productVariantRepository.getVariant(entry.productVariantId)
                    val product = variant?.let { productRepository.getProduct(it.productId) }

                    if (variant != null && product != null) {
                        val item = StockEntryItem(
                            id = entry.id,
                            productVariant = variant,
                            quantity = entry.quantity,
                            productName = product.name,
                            costPrice = entry.costPrice,
                            costPerAmpere = entry.costPerAmpere,
                            totalAmperes = entry.totalAmperes,
                            totalCost = entry.totalCost
                        )
                        stockItems.add(item)
                    } else {
                        _errorMessage.value = "لم يتم العثور على المنتج المرتبط."
                    }
                } else {
                    _errorMessage.value = "لم يتم العثور على قيد المخزون."
                }
            } catch (e: Exception) {
                _errorMessage.value = "فشل تحميل القيد: ${e.message}"
            }
        }
    }


    private fun fetchProducts() {
        viewModelScope.launch {
            try {
                products.value = productRepository.getProducts().filter { !it.isArchived }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch products: ${e.message}"
            }
        }
    }

    fun fetchVariantsForProduct(productId: String) {
        viewModelScope.launch {
            try {
                variants.value = productVariantRepository.getVariantsForProduct(productId).filter { !it.isArchived }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch variants: ${e.message}"
                variants.value = emptyList()
            }
        }
    }

    private fun fetchWarehouses() {
        viewModelScope.launch {
            try {
                warehouses.value = warehouseRepository.getWarehouses()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch warehouses: ${e.message}"
            }
        }
    }

    fun addWarehouse(name: String) {
        viewModelScope.launch {
            try {
                warehouseRepository.addWarehouse(Warehouse(name = name, location = ""))
                fetchWarehouses()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add warehouse: ${e.message}"
            }
        }
    }

    fun addVariantToEntry(
        variant: ProductVariant,
        quantity: Int,
        productName: String,
        costPrice: Double,
        costPerAmpere: Double,
        totalAmperes: Int,
        totalCost: Double
    ) {
        if (isEditMode) {
            _errorMessage.value = "لا يمكن إضافة أصناف جديدة في وضع التعديل."
            return
        }

        if (quantity <= 0) {
            _errorMessage.value = "الكمية يجب أن تكون أكبر من صفر."
            return
        }
        if (costPrice <= 0) {
            _errorMessage.value = "الرجاء إدخال تكلفة صحيحة."
            return
        }

        stockItems.add(StockEntryItem(
            productVariant = variant,
            quantity = quantity,
            productName = productName,
            costPrice = costPrice,
            costPerAmpere = costPerAmpere,
            totalAmperes = totalAmperes,
            totalCost = totalCost
        ))
    }

    fun removeVariantFromEntry(item: StockEntryItem) {
        if (isEditMode) {
             _errorMessage.value = "لا يمكن إزالة الأصناف في وضع التعديل."
            return
        }
        stockItems.remove(item)
    }

    fun updateItemQuantity(item: StockEntryItem, newQuantity: Int) {
        val index = stockItems.indexOfFirst { it.id == item.id }
        if (index != -1 && newQuantity > 0) {
            // Recalculate totals when quantity changes
            val newTotalAmperes = newQuantity * item.productVariant.capacity
            val newTotalCost = newQuantity * item.costPrice
            stockItems[index] = item.copy(
                quantity = newQuantity,
                totalAmperes = newTotalAmperes,
                totalCost = newTotalCost
            )
        }
    }

    fun saveStockEntry(warehouseId: String, supplier: String) {
        viewModelScope.launch {
            if (stockItems.isEmpty()) {
                _errorMessage.value = "لا توجد أصناف للحفظ."
                return@launch
            }

            try {
                if (isEditMode) {
                    // --- Update Logic ---
                    val itemToUpdate = stockItems.first()
                    val originalEntry = stockEntryRepository.getStockEntryById(itemToUpdate.id)

                    if (originalEntry != null) {
                         val updatedEntry = originalEntry.copy(
                            quantity = itemToUpdate.quantity,
                            costPrice = itemToUpdate.costPrice,
                            costPerAmpere = itemToUpdate.costPerAmpere,
                            totalAmperes = itemToUpdate.totalAmperes,
                            totalCost = itemToUpdate.totalCost,
                            // Note: Grand totals might need recalculation if they depend on other entries.
                            // For a single entry edit, we can keep them as they were, or update them based on the change.
                            // Let's assume for now they don't need to change for a single item edit.
                            supplier = supplier
                        )
                        stockEntryRepository.updateStockEntry(updatedEntry)
                    } else {
                         _errorMessage.value = "القيد الأصلي غير موجود، لا يمكن التحديث."
                        return@launch
                    }

                } else {
                    // --- Add New Logic ---
                    val grandTotalAmperes = stockItems.sumOf { it.totalAmperes }
                    val grandTotalCost = stockItems.sumOf { it.totalCost }

                    val entries = stockItems.map { item ->
                        StockEntry(
                            productVariantId = item.productVariant.id,
                            warehouseId = warehouseId,
                            quantity = item.quantity,
                            costPrice = item.costPrice,
                            costPerAmpere = item.costPerAmpere,
                            totalAmperes = item.totalAmperes,
                            totalCost = item.totalCost,
                            grandTotalAmperes = grandTotalAmperes,
                            grandTotalCost = grandTotalCost,
                            timestamp = Date(),
                            supplier = supplier
                        )
                    }
                    stockEntryRepository.addStockEntries(entries)
                }
                stockItems.clear()
            } catch (e: Exception) {
                _errorMessage.value = "فشل حفظ إدخال المخزون: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
