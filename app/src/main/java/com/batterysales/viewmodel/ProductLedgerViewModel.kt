package com.batterysales.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedgerItem(
    val entry: StockEntry,
    val warehouseName: String
)

@HiltViewModel
class ProductLedgerViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    private val warehouseRepository: WarehouseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productVariantId: String = savedStateHandle.get<String>("variantId") ?: ""
    val productName: String = savedStateHandle.get<String>("productName") ?: "سجل المنتج"
    val variantCapacity: String = savedStateHandle.get<String>("variantCapacity") ?: ""

    private val _ledgerItems = MutableStateFlow<List<LedgerItem>>(emptyList())
    val ledgerItems = _ledgerItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        fetchLedger()
    }

    fun deleteStockEntry(entryId: String) {
        viewModelScope.launch {
            try {
                stockEntryRepository.deleteStockEntry(entryId)
                fetchLedger() // Refresh the list
            } catch (e: Exception) {
                // Handle error (e.g., show a snackbar)
            }
        }
    }

    private fun fetchLedger() {
        if (productVariantId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entries = stockEntryRepository.getEntriesForVariant(productVariantId)
                val warehouses = warehouseRepository.getWarehouses().associateBy { it.id }

                val items = entries.mapNotNull { entry ->
                    warehouses[entry.warehouseId]?.let { warehouse ->
                        LedgerItem(entry = entry, warehouseName = warehouse.name)
                    }
                }
                _ledgerItems.value = items

            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
}
