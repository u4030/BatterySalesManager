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
    val highlightEntryId: String? = savedStateHandle.get<String>("highlightEntryId")

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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun setErrorMessage(msg: String) { _errorMessage.value = msg }
    fun clearErrorMessage() { _errorMessage.value = null }

    private val refreshTrigger = MutableStateFlow(0)
    private val _isDataLoaded = MutableStateFlow(false)

    // التحكم بحجم الصفحة الحالية محلياً لمنع جلطات الكاش الفايرستوري
    private val _currentLimit = MutableStateFlow(20)

    private data class LedgerFilters(
        val category: LedgerCategory,
        val query: String,
        val user: com.batterysales.data.models.User?,
        val warehouses: List<Warehouse>,
        val limit: Int
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ledgerItems: StateFlow<List<LedgerItem>> = combine(
        listOf(
            _selectedCategory, 
            _searchQuery, 
            userRepository.getCurrentUserFlow(), 
            warehouseRepository.getWarehouses(),
            refreshTrigger, 
            _isDataLoaded,
            _currentLimit
        )
    ) { args: Array<Any?> ->
        val category = args[0] as LedgerCategory
        val query = args[1] as String
        val user = args[2] as com.batterysales.data.models.User?
        val warehouses = args[3] as List<Warehouse>
        val loaded = args[5] as Boolean
        val limit = args[6] as Int
        
        if (!loaded && query.isEmpty()) null
        else LedgerFilters(category, query, user, warehouses, limit)
    }.filterNotNull().flatMapLatest { filters ->
        val warehouseMap = filters.warehouses.associateBy { it.id }
        val user = filters.user
        val warehouseFilter = targetWarehouseId ?: if (user?.role == "seller") user.warehouseId else null

        stockEntryRepository.listenToStockEntriesReactive(
            variantId = productVariantId,
            warehouseId = warehouseFilter,
            limit = filters.limit
        ).map { entries ->
            entries.map { entry ->
                LedgerItem(entry = entry, warehouseName = warehouseMap[entry.warehouseId]?.name ?: "المستودع الإمبراطوري")
            }.filter { item ->
                // نفتح الفلترة لتظهر القيود حياً بغض النظر عن الفلاتر الفرعية المكسورة
                if (filters.category == LedgerCategory.ALL) true
                else when (filters.category) {
                    LedgerCategory.PURCHASES -> item.entry.quantity > 0
                    LedgerCategory.SALES -> item.entry.quantity < 0
                    LedgerCategory.TRANSFERS -> item.entry.orderId.startsWith("transfer_")
                    LedgerCategory.RETURNS -> item.entry.returnedQuantity > 0
                    else -> true
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _userRole.value = user?.role ?: "seller"
            loadData()
        }
    }

    /**
     * توسيع نطاق الكاش تلقائياً عند الاقتراب من نهاية القائمة
     */
    fun loadMoreEntries() {
        if (_isLoadingMore.value) return
        _currentLimit.value += 20
    }

    fun loadData(reset: Boolean = false) {
        _isDataLoaded.value = true
        if (reset) {
            _currentLimit.value = 20
            refreshTrigger.value += 1
        }
    }

    fun selectCategory(category: LedgerCategory) { _selectedCategory.value = category }
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query; if(query.isNotEmpty()) _isDataLoaded.value = true }

    suspend fun findVariantByBarcode(barcode: String): com.batterysales.data.models.ProductVariant? {
        return productVariantRepository.getVariantByBarcode(barcode)
    }

    suspend fun getProductName(productId: String): String? {
        return productRepository.getProduct(productId)?.name
    }

    suspend fun getAvailableStockForVariant(variantId: String, warehouseId: String): Int {
        val variant = productVariantRepository.getVariant(variantId)
        val stockMap = variant?.currentStock ?: emptyMap()
        return stockMap[warehouseId] ?: 0
    }

    fun processReturn(entry: StockEntry, quantity: Int, mode: String, notes: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                stockEntryRepository.processReturn(entry, quantity, mode, notes)
                loadData(reset = true)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "حدث خطأ أثناء إرجاع المواد"
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
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "حدث خطأ أثناء حذف قيد المخزون"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
 
