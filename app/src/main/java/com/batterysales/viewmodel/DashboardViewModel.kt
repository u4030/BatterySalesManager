package com.batterysales.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val salesRepository: SalesRepository,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val lowStockAlertsFlow: Flow<List<SystemAlert>> = userRepository.getCurrentUserFlow().flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            val isAdmin = user.role == "admin"
            if (isAdmin) {
                // Admins observe all active system alerts via systemAlertRepository
                systemAlertRepository.getSystemAlertsFlow()
            } else {
                // Sellers / warehouse managers observe their warehouse low stock alerts atomically with 0ms latency
                val whId = user.warehouseId ?: ""
                salesRepository.listenToLowStockAlerts(whId).map { rawList ->
                    rawList.map { map ->
                        val dataMap = (map["data"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value ?: "" } ?: emptyMap<String, Any>()
                        SystemAlert(
                            id = map["id"]?.toString() ?: "",
                            title = map["title"]?.toString() ?: "",
                            message = map["message"]?.toString() ?: "",
                            type = map["type"]?.toString() ?: "LOW_STOCK",
                            relatedId = map["relatedId"]?.toString() ?: map["variantId"]?.toString() ?: "",
                            variantId = map["variantId"]?.toString() ?: map["relatedId"]?.toString() ?: "",
                            warehouseId = map["warehouseId"]?.toString(),
                            warehouseName = map["warehouseName"]?.toString(),
                            currentStock = (map["currentStock"] as? Number)?.toInt(),
                            threshold = (map["threshold"] as? Number)?.toInt(),
                            capacity = (map["capacity"] as? Number)?.toInt(),
                            specification = map["specification"]?.toString() ?: "",
                            resolved = (map["resolved"] as? Boolean) ?: false,
                            status = map["status"]?.toString() ?: "active",
                            data = dataMap
                        )
                    }
                }
            }
        }
    }

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
                    ?.filter { b -> b.dueDate?.after(nextWeek.time) == false }
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
        summaryRepository.getFinancialStatusFlow(),
        _warehouses,
        lowStockAlertsFlow,
        upcomingBillsFlow,
        pendingCountFlow
    ) { args: Array<Any?> ->
        val user = args[0] as? User
        val hub = args[1] as? DashboardHub ?: DashboardHub()
        val financialStatus = args[2] as? FinancialStatus ?: FinancialStatus()
        @Suppress("UNCHECKED_CAST")
        val warehouses = args[3] as? List<Warehouse> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val alerts = args[4] as? List<SystemAlert> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val bills = args[5] as? List<Bill> ?: emptyList()
        val pendingCount = args[6] as? Int ?: 0

        if (user == null) return@combine DashboardUiState(isLoading = false)

        val isAdmin = user.role == "admin"
        val userWarehouseId = user.warehouseId

        val totalCustomerDebt = financialStatus.warehouseBalances.values.sumOf { it.pendingCollection }

        val systemStats = SystemStats(
            totalCashBalance = if (financialStatus.globalCashBalance != 0.0) financialStatus.globalCashBalance else hub.financialStatus.totalCashBalance,
            totalBankBalance = if (financialStatus.globalBankBalance != 0.0) financialStatus.globalBankBalance else hub.bankSummary.globalBankBalance,
            totalSupplierDebt = if (isAdmin) hub.supplierSummary.totalSupplierDebt else 0.0,
            totalCustomerDebt = totalCustomerDebt,
            totalUnpaidBills = if (financialStatus.totalUnpaidBills != 0.0) financialStatus.totalUnpaidBills else hub.bankSummary.totalUnpaidBills,
            totalUnpaidChecks = if (financialStatus.totalUnpaidChecks != 0.0) financialStatus.totalUnpaidChecks else hub.bankSummary.totalUnpaidChecks,
            totalInventoryQuantity = hub.inventorySummary.totalItemsCount,
            totalInventoryValue = hub.inventorySummary.totalValue,
            updatedAt = if (financialStatus.lastUpdated.after(hub.lastUpdated)) financialStatus.lastUpdated else hub.lastUpdated
        )

        // Local Jordan timezone format (YYYY-MM-DD)
        val jordanDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Amman")
        }
        val currentJordanDateStr = jordanDateFormat.format(Date())

        val whStats = if (isAdmin) {
            warehouses.map { wh ->
                val whFinancial = financialStatus.warehouseBalances[wh.id]
                // Instant check: if lastInvoiceDate matches today OR if todayCollection > 0, reflect it immediately
                val hasTodayActivity = whFinancial?.lastInvoiceDate == currentJordanDateStr ||
                    (whFinancial?.lastInvoiceDate.isNullOrEmpty() && (whFinancial?.todayCollection ?: 0.0) > 0.0)
                val todayColl = if (hasTodayActivity) (whFinancial?.todayCollection ?: 0.0) else 0.0
                val todayCount = if (hasTodayActivity) (whFinancial?.todayCollectionCount ?: 0) else 0
                WarehouseStats(wh.id, wh.name, todayColl, todayCount)
            }
        } else {
            val targetWhId = userWarehouseId ?: ""
            val whFinancial = financialStatus.warehouseBalances[targetWhId]
            val hasTodayActivity = whFinancial?.lastInvoiceDate == currentJordanDateStr ||
                (whFinancial?.lastInvoiceDate.isNullOrEmpty() && (whFinancial?.todayCollection ?: 0.0) > 0.0)
            val todayColl = if (hasTodayActivity) (whFinancial?.todayCollection ?: 0.0) else 0.0
            val todayCount = if (hasTodayActivity) (whFinancial?.todayCollectionCount ?: 0) else 0
            listOf(WarehouseStats(targetWhId, warehouses.find { it.id == targetWhId }?.name ?: "مخزن", todayColl, todayCount))
        }

        val lowStockItems = alerts.filter { isAdmin || it.warehouseId == userWarehouseId }.map { alert ->
            val capacity = when (val cap = alert.capacity ?: alert.data["capacity"]) {
                is Number -> cap.toInt()
                is String -> cap.toIntOrNull() ?: 0
                else -> 0
            }
            val specification = alert.specification.ifEmpty { (alert.data["specification"] as? String) ?: "" }
            val currentQty = when (val stock = alert.currentStock ?: alert.data["currentStock"]) {
                is Number -> stock.toInt()
                is String -> stock.toIntOrNull() ?: 0
                else -> 0
            }
            val minQty = when (val threshold = alert.threshold ?: alert.data["threshold"]) {
                is Number -> threshold.toInt()
                is String -> threshold.toIntOrNull() ?: 0
                else -> 0
            }

            val displayName = if (alert.title.startsWith("مخزون منخفض: ")) {
                alert.title.removePrefix("مخزون منخفض: ")
            } else if (alert.message.contains("الصنف ") && alert.message.contains(" شارف على النفاد")) {
                alert.message.substringAfter("الصنف ").substringBefore(" شارف على النفاد").trim()
            } else {
                alert.title.ifBlank { "منتج" }
            }

            LowStockItem(
                variantId = alert.relatedId,
                productName = displayName,
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

    fun refresh() {
        viewModelScope.launch {
            try {
                _warehouses.value = warehouseRepository.getWarehousesOnce()
                summaryRepository.getDashboardHubOnce()
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error in refresh", e)
            }
        }
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
                    "low_stock_${item.variantId}_${item.warehouseId}",
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
