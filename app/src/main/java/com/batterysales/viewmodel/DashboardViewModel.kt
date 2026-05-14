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

    private fun loadDashboardData() {
        combine(
            userRepository.getCurrentUserFlow(),
            refreshTrigger
        ) { user, _ -> user }.onEach { user ->
            if (user == null) {
                _uiState.value = DashboardUiState(isLoading = false)
                return@onEach
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
                try {
                    val isAdmin = user.role == "admin"
                    val userWarehouseId = user.warehouseId

                    // 1. Efficient server-side counts and stats (Aggregations)
                    val systemStats = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID).get().await()
                            .toObject(SystemStats::class.java) ?: SystemStats()
                    val pendingEntriesCount = stockEntryRepository.getPendingCount()
                    val pendingRequests = approvalRepository.getPendingRequestsFlow().take(1).first()
                    val warehouses = warehouseRepository.getWarehousesOnce()

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
                            .get().await()
                            .documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
                            .filter { it.dueDate != null && !it.dueDate.after(nextWeek.time) }
                            .sortedBy { it.dueDate }
                    }

                    // 4. Targeted Low Stock (Event-Driven from system_alerts)
                    val alertsSnap = firestore.collection(com.batterysales.data.models.SystemAlert.COLLECTION_NAME)
                        .whereEqualTo("type", com.batterysales.data.models.SystemAlert.TYPE_LOW_STOCK)
                        .get().await()

                    val filteredAlerts = alertsSnap.documents.mapNotNull { it.toObject(com.batterysales.data.models.SystemAlert::class.java) }
                        .filter { isAdmin || it.warehouseId == userWarehouseId }

                    val variantIds = filteredAlerts.map { it.relatedId }.distinct()
                    val variantsMap = if (variantIds.isEmpty()) emptyMap() else {
                        variantIds.chunked(30).map { chunk ->
                            firestore.collection(ProductVariant.COLLECTION_NAME)
                                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                .get().await()
                                .documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
                        }.flatten().associateBy { it.id }
                    }

                    val lowStockItems = filteredAlerts.mapNotNull { alert ->
                        val variant = variantsMap[alert.relatedId] ?: return@mapNotNull null
                        val whName = warehouses.find { it.id == alert.warehouseId }?.name ?: "مخزن غير معروف"

                        LowStockItem(
                            variantId = alert.relatedId,
                            productName = variant.productName ?: alert.title.replace("مخزون منخفض: ", ""),
                            capacity = variant.capacity,
                            currentQuantity = variant.currentStock?.get(alert.warehouseId) ?: 0,
                            minQuantity = variant.minQuantities[alert.warehouseId] ?: variant.minQuantity,
                            warehouseName = whName
                        )
                    }

                    // Construct Notifications
                    val allNotifications = mutableListOf<AppNotification>()
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
                    if (pendingCount > 0) {
                        allNotifications.add(AppNotification("pending_approvals", "موافقات معلقة", "لديك $pendingCount طلبات بانتظار الموافقة", NotificationType.PENDING_APPROVAL, "approvals"))
                    }
                    lowStockItems.forEach { item ->
                        val route = "product_ledger/${item.variantId}/${item.productName}/${item.capacity}/no_spec"
                        allNotifications.add(AppNotification("low_stock_${item.variantId}_${item.warehouseName}", "مخزون منخفض: ${item.productName}", "${item.capacity}A في ${item.warehouseName}", NotificationType.LOW_STOCK, route))
                    }

                _uiState.value = DashboardUiState(
                    pendingApprovalsCount = pendingCount,
                    lowStockVariants = lowStockItems,
                    upcomingBills = upcoming,
                    warehouseStats = warehouseStatsList,
                    notifications = allNotifications,
                    systemStats = systemStats,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error loading dashboard", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }.launchIn(viewModelScope)
    }
}
