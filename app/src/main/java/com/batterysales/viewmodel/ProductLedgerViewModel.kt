package com.batterysales.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.UserRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedgerItem(
    val entry: StockEntry,
    val warehouseName: String
)

enum class LedgerCategory(val label: String) {
    ALL("الكل"),
    PURCHASES("مشتريات"),
    SALES("مبيعات"),
    TRANSFERS("تحويلات"),
    RETURNS("مرتجعات")
}

@HiltViewModel
class ProductLedgerViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    private val userRepository: UserRepository,
    warehouseRepository: WarehouseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productVariantId: String = savedStateHandle.get<String>("variantId") ?: ""
    val productName: String = savedStateHandle.get<String>("productName") ?: "سجل المنتج"
    val variantCapacity: String = savedStateHandle.get<String>("variantCapacity") ?: ""
    val variantSpecification: String = (savedStateHandle.get<String>("variantSpecification") ?: "").let { if(it == "no_spec") "" else it }

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _userRole = MutableStateFlow("seller")
    val userRole = _userRole.asStateFlow()

    private val _selectedCategory = MutableStateFlow(LedgerCategory.ALL)
    val selectedCategory = _selectedCategory.asStateFlow()

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _userRole.value = user?.role ?: "seller"
        }
    }

    val ledgerItems: StateFlow<List<LedgerItem>> = if (productVariantId.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(
            stockEntryRepository.getEntriesForVariant(productVariantId),
            warehouseRepository.getWarehouses(),
            _selectedCategory
        ) { entries, warehouses, category ->
            val warehouseMap = warehouses.associateBy { it.id }
            val items = entries.mapNotNull { entry ->
                warehouseMap[entry.warehouseId]?.let { warehouse ->
                    LedgerItem(entry = entry, warehouseName = warehouse.name)
                }
            }

            when (category) {
                LedgerCategory.ALL -> items
                LedgerCategory.PURCHASES -> items.filter { it.entry.quantity > 0 && it.entry.supplier != "Sale" && it.entry.costPrice > 0 && !it.entry.supplier.contains("Reversal") }
                LedgerCategory.SALES -> items.filter { it.entry.supplier == "Sale" }
                LedgerCategory.TRANSFERS -> items.filter { it.entry.costPrice == 0.0 && !it.entry.supplier.contains("Reversal") }
                LedgerCategory.RETURNS -> items.filter { it.entry.supplier.contains("Reversal") }
            }
        }
    }.onStart { _isLoading.value = true }
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: LedgerCategory) {
        _selectedCategory.value = category
    }

    fun deleteStockEntry(entryId: String) {
        viewModelScope.launch {
            try {
                stockEntryRepository.deleteStockEntry(entryId)
                // No need to manually refresh, the Flow will do it automatically.
            } catch (e: Exception) {
                // Handle error (e.g., show a snackbar)
            }
        }
    }
}
