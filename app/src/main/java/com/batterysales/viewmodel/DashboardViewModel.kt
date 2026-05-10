package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import android.util.Log
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
    val systemStats: com.batterysales.data.models.SystemStats = com.batterysales.data.models.SystemStats(),
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
    private val paymentRepository: PaymentRepository,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)

    init {
        loadDashboardData()
    }

    fun refresh() {
        refreshTrigger.value += 1
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadDashboardData() {
        // Broad listeners are replaced with Targeted fetches triggered by specific small events or manual refresh
        combine(
            userRepository.getCurrentUserFlow(),
            refreshTrigger
        ) { user, _ ->
            user
        }.flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(DashboardUiState(isLoading = false))

            flow {
                emit(DashboardUiState(isLoading = true))
                try {
                    coroutineScope {
                        val isAdmin = user.role == "admin"
                        val userWarehouseId = user.warehouseId

                        // 1. Efficient server-side counts and stats (Aggregations)
                        val systemStatsJob = async {
                            firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID).get().await()
                                .toObject(SystemStats::class.java) ?: SystemStats()
                        }
                        val pendingEntriesCountJob = async { stockEntryRepository.getPendingCount() }
                        val pendingRequestsJob = async { approvalRepository.getPendingRequestsFlow().take(1).first() }
                        val warehousesJob = async { warehouseRepository.getWarehousesOnce() }

                        val systemStats = systemStatsJob.await()
                        val pendingEntriesCount = pendingEntriesCountJob.await()
                        val pendingRequests = pendingRequestsJob.await()
                        val warehouses = warehousesJob.await()

                        val pendingCount = pendingEntriesCount + pendingRequests.size

                        // 2. Today's Collections (Server-side aggregation)
                        val today = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                        val startOfToday = today.time.time
                        val relevantWarehouses = if (isAdmin) warehouses
                                                else warehouses.filter { it.id == userWarehouseId && it.isActive }

                        val warehouseStatsList = relevantWarehouses.map { warehouse ->
                            val (collection, count) = paymentRepository.getTodayStats(warehouse.id, startOfToday)
                            WarehouseStats(warehouse.id, warehouse.name, collection, count)
                        }.filter { if (isAdmin) it.todayCollection > 0 || it.todayCollectionCount > 0 else true }

                        // 3. Upcoming Bills (Optimized: only fetch relevant bills)
                        val nextWeek = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 7)
                            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                        }

                        val upcoming = if (user.role == User.ROLE_SELLER) emptyList()
                        else {
                            firestore.collection(Bill.COLLECTION_NAME)
                                .whereNotEqualTo("status", BillStatus.PAID)
                                .whereLessThanOrEqualTo("dueDate", nextWeek.time)
                                .get().await()
                                .documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
                                .sortedBy { it.dueDate }
                        }

                        // 4. Targeted Low Stock (Using pre-aggregated currentStock)
                        // Optimization: Instead of fetching all variants, we could query where any warehouse in currentStock is <= minQuantity
                        // But Firestore doesn't support complex map comparisons. We fetch active variants but limit data processing.
                        val activeVariants = productVariantRepository.getAllVariants().filter { !it.archived }
                        val productsMap = productRepository.getProductsOnce().associateBy { it.id }

                        val lowStockItems = mutableListOf<LowStockItem>()
                        for (variant in activeVariants) {
                            val targetWhs = if (isAdmin) warehouses.filter { it.isActive }
                                            else warehouses.filter { it.id == userWarehouseId && it.isActive }

                            for (wh in targetWhs) {
                                val currentQty = variant.currentStock?.get(wh.id) ?: 0
                                val threshold = variant.minQuantities[wh.id] ?: variant.minQuantity
                                if (threshold > 0 && currentQty <= threshold) {
                                    lowStockItems.add(LowStockItem(variant.id, productsMap[variant.productId]?.name ?: "منتج غير معروف", variant.capacity, currentQty, threshold, wh.name))
                                }
                            }
                        }

                        // Construct Notifications
                        val allNotifications = mutableListOf<AppNotification>()

                        // Bills
                        upcoming.forEach { bill ->
                            val isOverdue = bill.dueDate.before(today.time)
                            allNotifications.add(AppNotification(
                                "bill_${bill.id}",
                                if (isOverdue) "كمبيالة متأخرة" else "موعد استحقاق قريب",
                                "الكمبيالة: ${bill.description} تستحق بتاريخ ${java.text.SimpleDateFormat("yyyy/MM/dd").format(bill.dueDate)}",
                                if (isOverdue) NotificationType.OVERDUE_BILL else NotificationType.UPCOMING_BILL,
                                "bills"
                            ))
                        }
                        allNotifications.sortBy { if (it.type == NotificationType.OVERDUE_BILL) 0 else 1 }

                        // Approvals
                        if (pendingCount > 0) {
                            allNotifications.add(AppNotification("pending_approvals", "موافقات معلقة", "لديك $pendingCount طلبات بانتظار الموافقة", NotificationType.PENDING_APPROVAL, "approvals"))
                        }

                        // Low Stock
                        lowStockItems.forEach { item ->
                            val route = "product_ledger/${item.variantId}/${item.productName}/${item.capacity}/no_spec"
                            allNotifications.add(AppNotification("low_stock_${item.variantId}_${item.warehouseName}", "مخزون منخفض: ${item.productName}", "${item.capacity}A | المتبقي: ${item.currentQuantity} (الحد: ${item.minQuantity}) في ${item.warehouseName}", NotificationType.LOW_STOCK, route))
                        }

                        emit(DashboardUiState(
                            pendingApprovalsCount = pendingCount,
                            lowStockVariants = lowStockItems,
                            upcomingBills = upcoming,
                            warehouseStats = warehouseStatsList,
                            notifications = allNotifications,
                            systemStats = systemStats,
                            isLoading = false
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("DashboardViewModel", "Error loading dashboard", e)
                    emit(DashboardUiState(isLoading = false))
                }
            }
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }
}
