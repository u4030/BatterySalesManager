package com.batterysales.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Warehouse
import com.google.firebase.firestore.DocumentSnapshot
import com.batterysales.data.repositories.*
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.batterysales.data.paging.StockEntryPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

data class LedgerItem(
    val entry: StockEntry,
    val warehouseName: String
)

enum class LedgerCategory(val label: String) {
    ALL("الكل"), PURCHASES("مشتريات"), SALES("مبيعات"), TRANSFERS("تحويلات"), RETURNS("مرتجعات")
}

@HiltViewModel
class ProductLedgerViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    private val billRepository: BillRepository,
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productVariantId: String = savedStateHandle.get<String>("variantId") ?: ""
    private val targetWarehouseId: String? = savedStateHandle.get<String>("warehouseId")
    val productName: String = savedStateHandle.get<String>("productName") ?: "سجل المنتج"
    val variantCapacity: String = savedStateHandle.get<String>("variantCapacity") ?: ""
    val variantSpecification: String = (savedStateHandle.get<String>("variantSpecification") ?: "").let { if(it == "no_spec") "" else it }

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _userRole = MutableStateFlow("seller")
    val userRole = _userRole.asStateFlow()

    private val _selectedCategory = MutableStateFlow(LedgerCategory.ALL)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)
    private val _isDataLoaded = MutableStateFlow(false)
    private var allWarehouses: List<Warehouse> = emptyList()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ledgerItems: Flow<PagingData<LedgerItem>> = combine(_selectedCategory, _searchQuery, userRepository.getCurrentUserFlow(), refreshTrigger, _isDataLoaded) { category, query, user, _, loaded ->
        if (!loaded && query.isEmpty()) return@combine null
        Triple(category, query, user)
    }.filterNotNull().flatMapLatest { (category, query, user) ->
        val warehouseMap = allWarehouses.associateBy { it.id }
        val warehouseFilter = targetWarehouseId ?: if (user?.role == "seller") user.warehouseId else null
        
        Pager(PagingConfig(pageSize = 20)) { StockEntryPagingSource(stockEntryRepository, productVariantId, warehouseFilter) }
            .flow.map { pagingData ->
                pagingData.map { entry -> LedgerItem(entry = entry, warehouseName = warehouseMap[entry.warehouseId]?.name ?: "Unknown") }
                    .filter { item ->
                        val categoryMatch = when (category) {
                            LedgerCategory.ALL -> true
                            LedgerCategory.PURCHASES -> item.entry.quantity > 0 && item.entry.supplier != "Sale" && item.entry.costPrice > 0 && !item.entry.supplier.contains("Reversal")
                            LedgerCategory.SALES -> item.entry.supplier == "Sale"
                            LedgerCategory.TRANSFERS -> item.entry.costPrice == 0.0 && !item.entry.supplier.contains("Reversal")
                            LedgerCategory.RETURNS -> item.entry.supplier.contains("Reversal") || item.entry.returnedQuantity > 0
                        }
                        val searchMatch = if (query.isBlank()) true else item.entry.supplier.contains(query, ignoreCase = true) || item.warehouseName.contains(query, ignoreCase = true) || item.entry.createdByUserName.contains(query, ignoreCase = true) || item.entry.invoiceNumber.contains(query, ignoreCase = true)
                        categoryMatch && searchMatch
                    }
            }.cachedIn(viewModelScope)
    }

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _userRole.value = user?.role ?: "seller"
            allWarehouses = warehouseRepository.getWarehousesOnce()
        }
    }

    fun loadData(reset: Boolean = false) {
        _isDataLoaded.value = true
        if (reset) refreshTrigger.value += 1
    }

    fun selectCategory(category: LedgerCategory) { _selectedCategory.value = category }
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query; if(query.isNotEmpty()) _isDataLoaded.value = true }

    suspend fun findVariantByBarcode(barcode: String): com.batterysales.data.models.ProductVariant? {
        return productVariantRepository.getVariantByBarcode(barcode)
    }

    suspend fun getProductName(productId: String): String? {
        return productRepository.getProduct(productId)?.name
    }

    fun processReturn(entry: StockEntry, quantity: Int, mode: String, notes: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                stockEntryRepository.processReturn(entry, quantity, mode, notes)
                loadData(reset = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteStockEntry(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                stockEntryRepository.deleteStockEntry(id)
                loadData(reset = true)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
