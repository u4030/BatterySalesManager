package com.batterysales.viewmodel

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
import javax.inject.Inject

data class ApprovalItem(
    val entry: StockEntry,
    val productName: String,
    val variantCapacity: String,
    val warehouseName: String
)

@HiltViewModel
class ApprovalsViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _approvalItems = MutableStateFlow<List<ApprovalItem>>(emptyList())
    val approvalItems: StateFlow<List<ApprovalItem>> = _approvalItems.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadPendingEntries()
    }

    private fun loadPendingEntries() {
        viewModelScope.launch {
            combine(
                stockEntryRepository.getPendingEntriesFlow(),
                productRepository.getProducts(),
                productVariantRepository.getAllVariantsFlow(),
                warehouseRepository.getWarehouses()
            ) { pendingEntries, products, variants, warehouses ->
                pendingEntries.map { entry ->
                    val variant = variants.find { it.id == entry.productVariantId }
                    val product = products.find { it.id == variant?.productId }
                    val warehouse = warehouses.find { it.id == entry.warehouseId }

                    ApprovalItem(
                        entry = entry,
                        productName = product?.name ?: "منتج غير معروف",
                        variantCapacity = variant?.capacity?.toString() ?: "",
                        warehouseName = warehouse?.name ?: "مستودع غير معروف"
                    )
                }
            }.collect { items ->
                _approvalItems.value = items
                _isLoading.value = false
            }
        }
    }

    fun approveEntry(entryId: String) {
        viewModelScope.launch {
            stockEntryRepository.approveEntry(entryId)
        }
    }

    fun rejectEntry(entryId: String) {
        viewModelScope.launch {
            stockEntryRepository.deleteStockEntry(entryId)
        }
    }
}
