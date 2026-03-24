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

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val route: String? = null
)

enum class NotificationType {
    LOW_STOCK,
    PENDING_APPROVAL,
    UPCOMING_BILL,
    OVERDUE_BILL
}

data class DashboardUiState(
    val pendingApprovalsCount: Int = 0,
    val lowStockVariants: List<LowStockItem> = emptyList(),
    val upcomingBills: List<com.batterysales.data.models.Bill> = emptyList(),
    val warehouseStats: List<WarehouseStats> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
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
    private val approvalRepository: ApprovalRepository,
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
            productRepository.getProducts(),
            stockEntryRepository.getPendingEntriesFlow(),
            paymentRepository.getAllPaymentsFlow(),
            stockEntryRepository.getAllStockEntriesFlow(),
            approvalRepository.getPendingRequestsFlow()
        ) { array ->
            val warehouses = array[0] as List<com.batterysales.data.models.Warehouse>
            val user = array[1] as com.batterysales.data.models.User?
            val bills = array[2] as List<com.batterysales.data.models.Bill>
            val variants = array[3] as List<ProductVariant>
            val products = array[4] as List<com.batterysales.data.models.Product>
            val pendingEntries = array[5] as List<StockEntry>
            val allPayments = array[6] as List<com.batterysales.data.models.Payment>
            val allStockEntries = array[7] as List<StockEntry>
            val pendingRequests = array[8] as List<com.batterysales.data.models.ApprovalRequest>

            val isAdmin = user?.role == "admin"
            val userWarehouseId = user?.warehouseId

            // 1. Pending Approvals Count (Stock Entries + Product Requests)
            val pendingCount = pendingEntries.size + pendingRequests.size

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
                        !it.dueDate.after(nextWeek.time)
            }.sortedBy { it.dueDate }

            // 3. Today's Collections (Reactive from Flow)
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfToday = calendar.time

            val relevantWarehouses = if (isAdmin) warehouses
            else warehouses.filter { it.id == userWarehouseId && it.isActive }

            val warehouseStatsList = relevantWarehouses.map { warehouse ->
                val warehousePayments = allPayments.filter {
                    it.warehouseId == warehouse.id && (it.timestamp.after(startOfToday) || it.timestamp.equals(startOfToday))
                }
                val collection = warehousePayments.sumOf { it.amount }
                val count = warehousePayments.map { it.invoiceId }.distinct().size

                WarehouseStats(
                    warehouseId = warehouse.id,
                    warehouseName = warehouse.name,
                    todayCollection = collection,
                    todayCollectionCount = count
                )
            }.filter { if (isAdmin) it.todayCollection > 0 || it.todayCollectionCount > 0 else true }

            // 4. Low Stock Notifications
            val approvedEntries = allStockEntries.filter { it.status == "approved" }
            val entriesByVariant = approvedEntries.groupBy { it.productVariantId }
            val pMap = products.associateBy { it.id }

            val lowStockItems = mutableListOf<LowStockItem>()
            val activeVariants = variants.filter { !it.archived }

            for (variant in activeVariants) {
                val variantEntries = entriesByVariant[variant.id] ?: emptyList()
                val targetWarehouses = if (isAdmin) warehouses.filter { it.isActive }
                else warehouses.filter { it.id == userWarehouseId && it.isActive }

                for (warehouse in targetWarehouses) {
                    val whEntries = variantEntries.filter { it.warehouseId == warehouse.id }
                    val totalQty = whEntries.sumOf { it.quantity }
                    val totalRet = whEntries.sumOf { it.returnedQuantity }
                    val currentQty = totalQty - totalRet

                    val threshold = variant.minQuantities[warehouse.id] ?: variant.minQuantity

                    if (threshold > 0 && currentQty <= threshold) {
                        lowStockItems.add(
                            LowStockItem(
                                variantId = variant.id,
                                productName = pMap[variant.productId]?.name ?: "منتج غير معروف",
                                capacity = variant.capacity,
                                currentQuantity = currentQty,
                                minQuantity = threshold,
                                warehouseName = warehouse.name
                            )
                        )
                    }
                }
            }

            val allNotifications = mutableListOf<AppNotification>()

            // 1. PRIORITY: Bill Notifications (Overdue first, then upcoming)
            upcoming.forEach { bill ->
                val isOverdue = bill.dueDate.before(today.time)
                allNotifications.add(
                    AppNotification(
                        id = "bill_${bill.id}",
                        title = if (isOverdue) "كمبيالة متأخرة" else "موعد استحقاق قريب",
                        message = "الكمبيالة: ${bill.description} تستحق بتاريخ ${java.text.SimpleDateFormat("yyyy/MM/dd").format(bill.dueDate)}",
                        type = if (isOverdue) NotificationType.OVERDUE_BILL else NotificationType.UPCOMING_BILL,
                        route = "bills"
                    )
                )
            }
            allNotifications.sortBy { if (it.type == NotificationType.OVERDUE_BILL) 0 else 1 }

            // 2. Pending Approvals
            if (pendingCount > 0) {
                allNotifications.add(
                    AppNotification(
                        id = "pending_approvals",
                        title = "موافقات معلقة",
                        message = "لديك $pendingCount طلبات ترحيل مخزون بانتظار الموافقة",
                        type = NotificationType.PENDING_APPROVAL,
                        route = "approvals"
                    )
                )
            }

            // 3. Low Stock Notifications (Placed last)
            lowStockItems.forEach { item ->
                allNotifications.add(
                    AppNotification(
                        id = "low_stock_${item.variantId}_${item.warehouseName}",
                        title = "مخزون منخفض: ${item.productName}",
                        message = "السعة: ${item.capacity}A | الكمية المتبقية: ${item.currentQuantity} (الحد: ${item.minQuantity}) في ${item.warehouseName}",
                        type = NotificationType.LOW_STOCK,
                        route = "reports"
                    )
                )
            }

            DashboardUiState(
                pendingApprovalsCount = pendingCount,
                lowStockVariants = emptyList(),
                upcomingBills = upcoming,
                warehouseStats = warehouseStatsList,
                notifications = allNotifications,
                isLoading = false
            )
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

}
