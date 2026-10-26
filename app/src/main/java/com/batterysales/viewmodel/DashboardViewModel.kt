package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import android.util.Log
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.launch

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
    val upcomingBills: List<Bill> = emptyList(),
    val warehouseStats: List<WarehouseStats> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val systemStats: SystemStats = SystemStats(),
    val isLoading: Boolean = true
)

data class LowStockItem(
    val variantId: String,
    val productName: String,
    val capacity: Int,
    val specification: String,
    val currentQuantity: Int,
    val minQuantity: Int,
    val warehouseName: String,
    val warehouseId: String = ""
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val approvalRepository: ApprovalRepository,
    private val systemAlertRepository: SystemAlertRepository,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    private val _alerts = MutableStateFlow<List<SystemAlert>>(emptyList())

    private val upcomingBillsFlow: Flow<List<Bill>> = callbackFlow {
        val listener = firestore.collection(Bill.COLLECTION_NAME)
            .whereNotEqualTo("status", BillStatus.PAID.name)
            .limit(20)
            .addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                val nextWeek = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 7)
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                }
                val bills = snap?.documents?.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
                    ?.filter { it.dueDate != null && !it.dueDate.after(nextWeek.time) }
                    ?.sortedBy { it.dueDate } ?: emptyList()
                trySend(bills)
            }
        awaitClose { listener.remove() }
    }

    private val pendingCountFlow: Flow<Int> = combine(
        stockEntryRepository.getPendingEntriesFlow().catch { emit(emptyList()) },
        approvalRepository.getPendingRequestsFlow().catch { emit(emptyList()) }
    ) { entries, reqs ->
        entries.size + reqs.size
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        userRepository.getCurrentUserFlow(),
        summaryRepository.getDashboardHubFlow(),
        _warehouses,
        _alerts,
        upcomingBillsFlow,
        pendingCountFlow
    ) { args: Array<Any?> ->
        val user = args[0] as? User
        val hub = args[1] as? DashboardHub ?: DashboardHub()
        @Suppress("UNCHECKED_CAST")
        val warehouses = args[2] as? List<Warehouse> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val alerts = args[3] as? List<SystemAlert> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val bills = args[4] as? List<Bill> ?: emptyList()
        val pendingCount = args[5] as? Int ?: 0

        if (user == null) return@combine DashboardUiState(isLoading = false)

        val isAdmin = user.role == "admin"
        val userWarehouseId = user.warehouseId

        val systemStats = SystemStats(
            totalCashBalance = hub.financialStatus.totalCashBalance,
            totalBankBalance = hub.bankSummary.globalBankBalance,
            totalSupplierDebt = if (isAdmin) hub.supplierSummary.totalSupplierDebt else 0.0,
            totalCustomerDebt = 0.0,
            totalUnpaidBills = hub.bankSummary.totalUnpaidBills,
            totalUnpaidChecks = hub.bankSummary.totalUnpaidChecks,
            totalInventoryQuantity = hub.inventorySummary.totalItemsCount,
            totalInventoryValue = hub.inventorySummary.totalValue,
            updatedAt = hub.lastUpdated
        )

        val whStats = if (isAdmin) {
            warehouses.map { wh ->
                val bal = hub.financialStatus.subWarehouseBalances[wh.id] ?: 0.0
                WarehouseStats(wh.id, wh.name, bal, 0)
            }
        } else {
            val targetWhId = userWarehouseId ?: ""
            val bal = hub.financialStatus.subWarehouseBalances[targetWhId] ?: 0.0
            listOf(WarehouseStats(targetWhId, warehouses.find { it.id == targetWhId }?.name ?: "مخزن", bal, 0))
        }

        val lowStockItems = alerts.filter { isAdmin || it.warehouseId == userWarehouseId }.map { alert ->
            val capacity = (alert.data["capacity"] as? Number)?.toInt() ?: 0
            val specification = (alert.data["specification"] as? String) ?: ""
            val currentQty = (alert.data["currentStock"] as? Number)?.toInt() ?: 0
            val minQty = (alert.data["threshold"] as? Number)?.toInt() ?: 0

            LowStockItem(
                variantId = alert.relatedId,
                productName = alert.title.replace("مخزون منخفض: ", ""),
                capacity = capacity,
                specification = specification,
                currentQuantity = currentQty,
                minQuantity = minQty,
                warehouseName = warehouses.find { it.id == alert.warehouseId }?.name ?: alert.warehouseName ?: "مخزن",
                warehouseId = alert.warehouseId ?: ""
            )
        }

        val activeUpcomingBills = if (isAdmin) bills else emptyList()
        val activePendingCount = if (isAdmin) pendingCount else 0

        val notifications = constructNotifications(
            activeUpcomingBills,
            activePendingCount,
            lowStockItems,
            Date()
        )

        DashboardUiState(
            pendingApprovalsCount = activePendingCount,
            lowStockVariants = lowStockItems,
            upcomingBills = activeUpcomingBills,
            warehouseStats = whStats,
            systemStats = systemStats,
            notifications = notifications,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        loadWarehouses()
        loadAlerts(forceRefresh = false)
    }

    private fun loadWarehouses() {
        viewModelScope.launch {
            try {
                _warehouses.value = warehouseRepository.getWarehousesOnce()
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error loading warehouses", e)
            }
        }
    }

    fun loadAlerts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                val (alertsList, _) = systemAlertRepository.getAlertsPaginated(
                    limit = 20,
                    forceRefresh = forceRefresh
                )
                _alerts.value = alertsList
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error loading system alerts", e)
            }
        }
    }

    fun refresh() {
        loadWarehouses()
        loadAlerts(forceRefresh = true)
    }

    private fun constructNotifications(
        upcomingBills: List<Bill>,
        pendingCount: Int,
        lowStockItems: List<LowStockItem>,
        todayDate: Date
    ): List<AppNotification> {
        val allNotifications = mutableListOf<AppNotification>()

        upcomingBills.forEach { bill ->
            val isOverdue = bill.dueDate?.before(todayDate) ?: false
            allNotifications.add(
                AppNotification(
                    "bill_${bill.id}",
                    if (isOverdue) "كمبيالة متأخرة" else "موعد استحقاق قريب",
                    "الكمبيالة: ${bill.description} تستحق بتاريخ ${java.text.SimpleDateFormat("yyyy/MM/dd").format(bill.dueDate ?: Date())}",
                    if (isOverdue) NotificationType.OVERDUE_BILL else NotificationType.UPCOMING_BILL,
                    "bills?highlightBillId=${bill.id}"
                )
            )
        }

        allNotifications.sortBy { if (it.type == NotificationType.OVERDUE_BILL) 0 else 1 }

        if (pendingCount > 0) {
            allNotifications.add(
                AppNotification(
                    "pending_approvals",
                    "موافقات معلقة",
                    "لديك $pendingCount طلبات بانتظار الموافقة",
                    NotificationType.PENDING_APPROVAL,
                    "approvals"
                )
            )
        }

        lowStockItems.forEach { item ->
            val specLabel = if (item.specification.isNotBlank()) "${item.specification}|" else ""
            val message = "(${item.warehouseName}: $specLabel${item.capacity} أمبير) | المتاح: ${item.currentQuantity} | الحد الأدنى: ${item.minQuantity}"
            val route = "product_ledger/${item.variantId}/${item.productName}/${item.capacity}/${item.specification.ifEmpty { "no_spec" }}"

            allNotifications.add(
                AppNotification(
                    "low_stock_${item.variantId}_${item.warehouseId.ifBlank { item.warehouseName }}",
                    "مخزون منخفض: ${item.productName}",
                    message,
                    NotificationType.LOW_STOCK,
                    route
                )
            )
        }

        return allNotifications
    }
}
