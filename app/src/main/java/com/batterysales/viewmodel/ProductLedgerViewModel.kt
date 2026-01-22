package com.batterysales.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedgerItem(
    val entry: StockEntry,
    val warehouseName: String
)

@HiltViewModel
class ProductLedgerViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    warehouseRepository: WarehouseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productVariantId: String = savedStateHandle.get<String>("variantId") ?: ""
    val productName: String = savedStateHandle.get<String>("productName") ?: "سجل المنتج"
    val variantCapacity: String = savedStateHandle.get<String>("variantCapacity") ?: ""

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val ledgerItems: StateFlow<List<LedgerItem>> = if (productVariantId.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(
            stockEntryRepository.getEntriesForVariant(productVariantId),
            warehouseRepository.getWarehouses()
        ) { entries, warehouses ->
            val warehouseMap = warehouses.associateBy { it.id }
            entries.mapNotNull { entry ->
                warehouseMap[entry.warehouseId]?.let { warehouse ->
                    LedgerItem(entry = entry, warehouseName = warehouse.name)
                }
            }
        }
    }.onStart { _isLoading.value = true }
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
