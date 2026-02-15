package com.batterysales.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Warehouse
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.UserRepository
import android.util.Log
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
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
    private val productRepository: com.batterysales.data.repositories.ProductRepository,
    private val productVariantRepository: com.batterysales.data.repositories.ProductVariantRepository,
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

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _isLastPage = MutableStateFlow(false)
    val isLastPage = _isLastPage.asStateFlow()

    private var lastDocument: DocumentSnapshot? = null

    private val _userRole = MutableStateFlow("seller")
    val userRole = _userRole.asStateFlow()

    private val _selectedCategory = MutableStateFlow(LedgerCategory.ALL)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _ledgerItems = MutableStateFlow<List<LedgerItem>>(emptyList())
    val ledgerItems: StateFlow<List<LedgerItem>> = _ledgerItems.asStateFlow()

    private var allEntries: List<StockEntry> = emptyList()
    private var allWarehouses: List<Warehouse> = emptyList()

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _userRole.value = user?.role ?: "seller"

            warehouseRepository.getWarehouses().collect { warehouses ->
                allWarehouses = warehouses
                loadData(reset = true)
            }
        }
    }

    fun loadData(reset: Boolean = false) {
        if (productVariantId.isEmpty()) return
        if (reset) {
            lastDocument = null
            allEntries = emptyList()
            _isLastPage.value = false
            _isLoading.value = true
        }

        if (_isLastPage.value || _isLoadingMore.value) return

        viewModelScope.launch {
            try {
                if (!reset) _isLoadingMore.value = true

                val result = stockEntryRepository.getEntriesPaginated(
                    productVariantId = productVariantId,
                    lastDocument = lastDocument,
                    limit = 20
                )

                val newEntries = result.first
                lastDocument = result.second

                allEntries = if (reset) newEntries else allEntries + newEntries
                _isLastPage.value = newEntries.size < 20

                applyFilters()

            } catch (e: Exception) {
                Log.e("ProductLedgerViewModel", "Error loading ledger data", e)
            } finally {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    private fun applyFilters() {
        val warehouseMap = allWarehouses.associateBy { it.id }
        val category = _selectedCategory.value
        val query = _searchQuery.value

        val items = allEntries.mapNotNull { entry ->
            warehouseMap[entry.warehouseId]?.let { warehouse ->
                LedgerItem(
                    entry = entry,
                    warehouseName = warehouse.name
                )
            }
        }.filter {
            if (query.isBlank()) true
            else it.entry.supplier.contains(query, ignoreCase = true) ||
                    it.warehouseName.contains(query, ignoreCase = true) ||
                    it.entry.createdByUserName.contains(query, ignoreCase = true) ||
                    it.entry.invoiceNumber.contains(query, ignoreCase = true)
        }

        val filtered = when (category) {
            LedgerCategory.ALL -> items
            LedgerCategory.PURCHASES -> items.filter { it.entry.quantity > 0 && it.entry.supplier != "Sale" && it.entry.costPrice > 0 && !it.entry.supplier.contains("Reversal") }
            LedgerCategory.SALES -> items.filter { it.entry.supplier == "Sale" }
            LedgerCategory.TRANSFERS -> items.filter { it.entry.costPrice == 0.0 && !it.entry.supplier.contains("Reversal") }
            LedgerCategory.RETURNS -> items.filter { it.entry.supplier.contains("Reversal") || it.entry.returnedQuantity > 0 }
        }

        _ledgerItems.value = filtered
    }

    fun selectCategory(category: LedgerCategory) {
        _selectedCategory.value = category
        applyFilters()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    suspend fun findVariantByBarcode(barcode: String): com.batterysales.data.models.ProductVariant? {
        return productVariantRepository.getAllVariants().find { it.barcode == barcode }
    }

    suspend fun getProductName(productId: String): String? {
        return productRepository.getProduct(productId)?.name
    }

    fun deleteStockEntry(entryId: String) {
        viewModelScope.launch {
            try {
                stockEntryRepository.deleteStockEntry(entryId)
                loadData(reset = true)
            } catch (e: Exception) {
                Log.e("ProductLedgerViewModel", "Error deleting stock entry", e)
            }
        }
    }
}
