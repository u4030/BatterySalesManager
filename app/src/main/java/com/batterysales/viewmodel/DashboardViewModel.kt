package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.BillStatus
import com.batterysales.data.models.Invoice
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.StockEntry
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

data class WarehouseStats(
    val warehouseId: String,
    val warehouseName: String,
    val todayCollection: Double, // Payments received today
    val todayCollectionCount: Int // Number of unique invoices collected today
)

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

data class DashboardUiState(
    val pendingApprovalsCount: Int = 0,
    val lowStockVariants: List<LowStockItem> = emptyList(),
    val upcomingBills: List<com.batterysales.data.models.Bill> = emptyList(),
    val warehouseStats: List<WarehouseStats> = emptyList(),
    val isLoading: Boolean = true
)

data class LowStockItem(
    val variantId: String,
    val productName: String,
    val capacity: Int,
    val currentQuantity: Int,
    val minQuantity: Int,
    val warehouseName: String
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productRepository: ProductRepository,
    private val billRepository: BillRepository,
    private val warehouseRepository: WarehouseRepository,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        combine(
                warehouseRepository.getWarehouses(),
                userRepository.getCurrentUserFlow(),
                billRepository.getAllBillsFlow(),
                productVariantRepository.getAllVariantsFlow(),
                productRepository.getProducts()
            ) { warehouses, user, bills, variants, products ->
                val isAdmin = user?.role == "admin"
                val userWarehouseId = user?.warehouseId

                // 1. Pending Approvals Count (Optimized)
                val pendingCount = stockEntryRepository.getPendingCount()

                // 2. Upcoming Bills
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val nextWeek = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 7)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val upcoming = bills.filter {
                    it.status != BillStatus.PAID &&
                            !it.dueDate.before(today.time) &&
                            !it.dueDate.after(nextWeek.time)
                }.sortedBy { it.dueDate }

                // 3. Today's Collections (Optimized via aggregation)
                val startOfToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                val relevantWarehouses = if (isAdmin) warehouses
                                        else warehouses.filter { it.id == userWarehouseId }

                val warehouseStatsList = relevantWarehouses.map { warehouse ->
                    val collection = paymentRepository.getTodayCollection(startOfToday, warehouse.id)
                    val count = paymentRepository.getTodayCollectionCount(startOfToday, warehouse.id)

                    WarehouseStats(
                        warehouseId = warehouse.id,
                        warehouseName = warehouse.name,
                        todayCollection = collection,
                        todayCollectionCount = count
                    )
                }.filter { if (isAdmin) it.todayCollection > 0 else true }

                // 4. Low Stock (Still needs optimization, but we'll use a limited fetch if possible)
                // For now, we still rely on full fetch but we should consider denormalizing 'currentStock'
                val lowStock = calculateLowStock(variants, products, warehouses)

                DashboardUiState(
                    pendingApprovalsCount = pendingCount,
                    lowStockVariants = lowStock,
                    upcomingBills = upcoming,
                    warehouseStats = warehouseStatsList,
                    isLoading = false
                )
            }.onEach { state ->
                _uiState.value = state
            }.launchIn(viewModelScope)
    }

    private suspend fun calculateLowStock(variants: List<ProductVariant>, products: List<com.batterysales.data.models.Product>, warehouses: List<com.batterysales.data.models.Warehouse>): List<LowStockItem> {
        // This is still heavy, but better than nothing.
        // Ideally, we'd have a cloud function updating a 'stock_summary' document.
        val allEntries = stockEntryRepository.getAllStockEntries()
        val stockMap = allEntries.filter { it.status == StockEntry.STATUS_APPROVED }
            .groupBy { Pair(it.productVariantId, it.warehouseId) }
            .mapValues { entry -> entry.value.sumOf { it.quantity - it.returnedQuantity } }

        val productMap = products.associateBy { it.id }
        val lowStock = mutableListOf<LowStockItem>()
        val activeVariants = variants.filter { !it.archived && it.minQuantity > 0 }

        for (variant in activeVariants) {
            val product = productMap[variant.productId] ?: continue
            for (warehouse in warehouses) {
                val currentQty = stockMap[Pair(variant.id, warehouse.id)] ?: 0
                if (currentQty <= variant.minQuantity) {
                    lowStock.add(
                        LowStockItem(
                            variantId = variant.id,
                            productName = product.name,
                            capacity = variant.capacity,
                            currentQuantity = currentQty,
                            minQuantity = variant.minQuantity,
                            warehouseName = warehouse.name
                        )
                    )
                }
            }
        }
        return lowStock
    }
}
