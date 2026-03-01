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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.batterysales.data.paging.StockEntryPagingSource
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ledgerItems: Flow<PagingData<LedgerItem>> = combine(
        _selectedCategory,
        _searchQuery
    ) { category, query ->
        category to query
    }.flatMapLatest { (category, query) ->
        val warehouseMap = allWarehouses.associateBy { it.id }
        Pager(PagingConfig(pageSize = 20)) {
            StockEntryPagingSource(stockEntryRepository, productVariantId)
        }.flow.map { pagingData ->
            val mapped: PagingData<LedgerItem> = pagingData.map { entry ->
                LedgerItem(
                    entry = entry,
                    warehouseName = warehouseMap[entry.warehouseId]?.name ?: "Unknown"
                )
            }
            mapped.filter { item: LedgerItem ->
                // Apply category filter
                val categoryMatch = when (category) {
                    LedgerCategory.ALL -> true
                    LedgerCategory.PURCHASES -> item.entry.quantity > 0 && item.entry.supplier != "Sale" && item.entry.costPrice > 0 && !item.entry.supplier.contains("Reversal")
                    LedgerCategory.SALES -> item.entry.supplier == "Sale"
                    LedgerCategory.TRANSFERS -> item.entry.costPrice == 0.0 && !item.entry.supplier.contains("Reversal")
                    LedgerCategory.RETURNS -> item.entry.supplier.contains("Reversal") || item.entry.returnedQuantity > 0
                }

                // Apply search filter
                val searchMatch = if (query.isBlank()) true
                else item.entry.supplier.contains(query, ignoreCase = true) ||
                        item.warehouseName.contains(query, ignoreCase = true) ||
                        item.entry.createdByUserName.contains(query, ignoreCase = true) ||
                        item.entry.invoiceNumber.contains(query, ignoreCase = true)

                categoryMatch && searchMatch
            }
        }.cachedIn(viewModelScope)
    }

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
        if (reset) {
            _isLoading.value = true
            // Trigger refresh
            _selectedCategory.value = _selectedCategory.value
        }
        _isLoading.value = false
    }

    fun selectCategory(category: LedgerCategory) {
        _selectedCategory.value = category
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
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
