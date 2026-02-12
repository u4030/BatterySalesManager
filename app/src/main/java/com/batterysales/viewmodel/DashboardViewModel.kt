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
    private val invoiceRepository: InvoiceRepository,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        stockEntryRepository.getAllStockEntriesFlow(),
        productVariantRepository.getAllVariantsFlow(),
        productRepository.getProducts(),
        billRepository.getAllBillsFlow(),
        combine(
            warehouseRepository.getWarehouses(),
            invoiceRepository.getAllInvoices(),
            userRepository.getCurrentUserFlow(),
            paymentRepository.getAllPayments()
        ) { w, i, u, p -> Quadruple(w, i, u, p) }
    ) { allEntries, allVariants, allProducts, allBills, quad ->
        val allWarehouses = quad.first
        val allInvoices = quad.second
        val currentUser = quad.third
        val allPayments = quad.fourth
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

        // Calculate Today's Collection
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val relevantWarehouses = if (isAdmin) allWarehouses 
                                else allWarehouses.filter { it.id == userWarehouseId }

        val warehouseStatsList = relevantWarehouses.map { warehouse ->
            val allWhInvoices = allInvoices.filter { it.warehouseId == warehouse.id }
            val invoiceMap = allWhInvoices.associateBy { it.id }
            
            val paymentsToday = allPayments.filter { payment ->
                val invoice = invoiceMap[payment.invoiceId]
                invoice != null && !payment.timestamp.before(startOfToday)
            }

            val uniqueInvoicesCollected = paymentsToday.map { it.invoiceId }.distinct().size

            WarehouseStats(
                warehouseId = warehouse.id,
                warehouseName = warehouse.name,
                todayCollection = paymentsToday.sumOf { it.amount },
                todayCollectionCount = uniqueInvoicesCollected
            )
        }.filter { if (isAdmin) it.todayCollection > 0 else true }

        DashboardUiState(
            pendingApprovalsCount = pendingCount,
            lowStockVariants = lowStock,
            upcomingBills = upcoming,
            warehouseStats = warehouseStatsList,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
