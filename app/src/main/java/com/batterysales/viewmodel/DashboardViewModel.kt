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
    val todaySales: Double,
    val todayInvoicesCount: Int
)

data class DashboardUiState(
    val pendingApprovalsCount: Int = 0,
    val lowStockVariants: List<LowStockItem> = emptyList(),
    val upcomingBills: List<com.batterysales.data.models.Bill> = emptyList(),
    val todaySales: Double = 0.0,
    val todayInvoicesCount: Int = 0,
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
    private val invoiceRepository: InvoiceRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        stockEntryRepository.getAllStockEntriesFlow(),
        productVariantRepository.getAllVariantsFlow(),
        productRepository.getProducts(),
        billRepository.getAllBillsFlow(),
        combine(
            warehouseRepository.getWarehouses(),
            invoiceRepository.getAllInvoices(),
            userRepository.getCurrentUserFlow()
        ) { w, i, u -> Triple(w, i, u) }
    ) { allEntries, allVariants, allProducts, allBills, triple ->
        val allWarehouses = triple.first
        val allInvoices = triple.second
        val currentUser = triple.third
        val isAdmin = currentUser?.role == "admin"
        val userWarehouseId = currentUser?.warehouseId

        val pendingCount = allEntries.count { it.status == StockEntry.STATUS_PENDING }

        val productMap = allProducts.associateBy { it.id }

        // Optimize: Group approved entries by Pair(variantId, warehouseId)
        val stockMap = allEntries.filter { it.status == StockEntry.STATUS_APPROVED }
            .groupBy { Pair(it.productVariantId, it.warehouseId) }
            .mapValues { entry -> entry.value.sumOf { it.quantity - it.returnedQuantity } }

        val lowStock = mutableListOf<LowStockItem>()

        val activeVariants = allVariants.filter { !it.archived && it.minQuantity > 0 }

        for (variant in activeVariants) {
            val product = productMap[variant.productId] ?: continue

            for (warehouse in allWarehouses) {
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

        val upcoming = allBills.filter {
            it.status != BillStatus.PAID &&
                    !it.dueDate.before(today.time) &&
                    !it.dueDate.after(nextWeek.time)
        }.sortedBy { it.dueDate }

        // Calculate Today's Sales and Invoices
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val todayInvoices = allInvoices.filter {
            !it.createdAt.before(startOfToday)
        }

        val filteredTodayInvoices = if (isAdmin) todayInvoices
                                   else todayInvoices.filter { it.warehouseId == userWarehouseId }

        val todaySalesSum = filteredTodayInvoices.sumOf { it.finalAmount }
        val todayCount = filteredTodayInvoices.size

        val warehouseStatsList = allWarehouses.map { warehouse ->
            val invoices = todayInvoices.filter { it.warehouseId == warehouse.id }
            WarehouseStats(
                warehouseId = warehouse.id,
                warehouseName = warehouse.name,
                todaySales = invoices.sumOf { it.finalAmount },
                todayInvoicesCount = invoices.size
            )
        }.filter { it.todayInvoicesCount > 0 || it.todaySales > 0 }

        DashboardUiState(
            pendingApprovalsCount = pendingCount,
            lowStockVariants = lowStock,
            upcomingBills = upcoming,
            todaySales = todaySalesSum,
            todayInvoicesCount = todayCount,
            warehouseStats = warehouseStatsList,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
