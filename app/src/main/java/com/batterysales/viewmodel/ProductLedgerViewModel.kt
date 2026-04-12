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
import kotlinx.coroutines.tasks.await
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

    private val refreshTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ledgerItems: Flow<PagingData<LedgerItem>> = combine(
        _selectedCategory,
        _searchQuery,
        refreshTrigger
    ) { category, query, _ ->
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

        // Reactively refresh when any stock action happens (Lightweight listener)
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection(com.batterysales.data.models.StockEntry.COLLECTION_NAME)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                if (e == null && snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    refreshTrigger.value += 1
                }
            }
    }

    fun loadData(reset: Boolean = false) {
        if (reset) {
            _isLoading.value = true
            refreshTrigger.value += 1
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

    fun processReturn(
        entry: StockEntry,
        returnQty: Int,
        returnMode: String, // "supplier_balance" or "treasury_cash"
        notes: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // 1. Update Stock Entry with returned quantity
                val updatedEntry = entry.copy(
                    returnedQuantity = entry.returnedQuantity + returnQty,
                    returnDate = Date()
                )
                stockEntryRepository.updateStockEntry(updatedEntry)

                // 2. Handle Financials
                val returnAmount = returnQty * entry.costPrice
                if (returnAmount > 0) {
                    if (returnMode == "supplier_balance") {
                        // Create a "Reversal" payment to reduce supplier debt
                        // This is effectively a credit note.
                        // We'll use a Bill with a negative amount or a specialized Transaction.
                        // Since we don't have a direct "CreditNote" model, let's use a Bill with PAID status
                        // and a linked transaction that reflects the return.

                        // Actually, a simpler way is to just add a Transaction of type INCOME to the treasury
                        // but marked as "Supplier Return".
                        // Wait, if it's "supplier_balance", it means we don't take cash, we just reduce what we owe him.
                        // Our Supplier Report calculates: totalDebit (entries) - totalCredit (bills).
                        // Increasing totalCredit or decreasing totalDebit works.
                        // Let's create a "Bill" of type CASH, marked as PAID, with description "Material Return".

                        val bill = com.batterysales.data.models.Bill(
                            description = "إرجاع مواد لـ: $productName - $notes",
                            amount = returnAmount,
                            dueDate = Date(),
                            billType = com.batterysales.data.models.BillType.CASH,
                            supplierId = entry.supplierId,
                            relatedEntryId = entry.id,
                            warehouseId = entry.warehouseId,
                            status = com.batterysales.data.models.BillStatus.PAID,
                            paidAmount = returnAmount,
                            paidDate = Date(),
                            referenceNumber = "RETURN-${entry.id.takeLast(4)}"
                        )
                        // This will increase totalCredit in Supplier Report, thus reducing balance.
                        // We do NOT want this to affect Treasury Cash if it's "supplier_balance".
                        // But BillRepository.addBill doesn't automatically add a transaction.
                        // BillViewModel does.
                        // So we'll just use the repository directly.
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection(com.batterysales.data.models.Bill.COLLECTION_NAME)
                            .add(bill).await()

                    } else if (returnMode == "treasury_cash") {
                        // Pay from Main Warehouse Treasury
                        val allWh = allWarehouses
                        val mainWh = allWh.find { it.isMain && it.isActive }
                        if (mainWh != null) {
                            val transaction = com.batterysales.data.models.Transaction(
                                type = com.batterysales.data.models.TransactionType.INCOME, // Money coming BACK to us
                                amount = returnAmount,
                                description = "إرجاع نقدي من مورد: $productName - $notes",
                                warehouseId = mainWh.id,
                                paymentMethod = "cash",
                                relatedId = entry.id
                            )
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
                                .add(transaction).await()
                        }
                    }
                }

                refreshTrigger.value += 1
            } catch (e: Exception) {
                Log.e("ProductLedgerViewModel", "Error processing return", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
