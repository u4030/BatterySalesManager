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
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose

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
    val specification: String,
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
    private val summaryRepository: SummaryRepository,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    private data class HeavyData(
        val pendingCount: Int,
        val warehouses: List<Warehouse>,
        val upcomingBills: List<Bill>
    )

    private val _heavyData = MutableStateFlow<HeavyData?>(null)

    private val alertsFlow: Flow<List<SystemAlert>> = callbackFlow {
        val listener = firestore.collection(SystemAlert.COLLECTION_NAME)
            .whereEqualTo("type", SystemAlert.TYPE_LOW_STOCK)
            .addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                val alerts = snap?.documents?.mapNotNull { it.toObject(SystemAlert::class.java) } ?: emptyList()
                trySend(alerts)
            }
        awaitClose { listener.remove() }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        userRepository.getCurrentUserFlow(),
        summaryRepository.getFinancialStatusFlow(),
        summaryRepository.getSuppliersOverviewFlow(),
        alertsFlow,
        _heavyData
    ) { user: User?, financial: FinancialStatus, suppliers: SuppliersOverview, alerts: List<SystemAlert>, heavy: HeavyData? ->
        if (user == null) return@combine DashboardUiState(isLoading = false)
        if (heavy == null) return@combine DashboardUiState(isLoading = true)

        val isAdmin = user.role == "admin"
        val userWarehouseId = user.warehouseId

        // Financial Stats
        val whStats = if (isAdmin) {
            heavy.warehouses.map { wh ->
                val bal = financial.warehouseBalances[wh.id]
                WarehouseStats(wh.id, wh.name, bal?.todayCollection ?: 0.0, bal?.todayCollectionCount ?: 0)
            }.filter { it.todayCollection > 0 || it.todayCollectionCount > 0 }
        } else {
            val targetWhId = userWarehouseId ?: ""
            val bal = financial.warehouseBalances[targetWhId]
            listOf(WarehouseStats(targetWhId, heavy.warehouses.find { it.id == targetWhId }?.name ?: "مخزن", bal?.todayCollection ?: 0.0, bal?.todayCollectionCount ?: 0))
        }

        val systemStats = SystemStats(
            totalCashBalance = financial.globalCashBalance,
            totalBankBalance = financial.globalBankBalance,
            totalSupplierDebt = if (isAdmin) suppliers.totalSupplierDebt else 0.0,
            totalCustomerDebt = if (isAdmin) financial.warehouseBalances.values.sumOf { it.pendingCollection } else 0.0,
            totalUnpaidBills = financial.totalUnpaidBills,
            totalUnpaidChecks = financial.totalUnpaidChecks
        )

        // Alerts / Low Stock
        val filteredAlerts = alerts.filter { isAdmin || it.warehouseId == userWarehouseId }
        val lowStockItems = filteredAlerts.map { alert ->
            LowStockItem(
                variantId = alert.relatedId,
                productName = alert.title.replace("مخزون منخفض: ", ""),
                capacity = (alert.data["capacity"] as? Number)?.toInt() ?: 0,
                specification = (alert.data["specification"] as? String) ?: "",
                currentQuantity = (alert.data["currentStock"] as? Number)?.toInt() ?: 0,
                minQuantity = (alert.data["threshold"] as? Number)?.toInt() ?: 0,
                warehouseName = heavy.warehouses.find { it.id == alert.warehouseId }?.name ?: alert.warehouseName ?: "مخزن"
            )
        }

        DashboardUiState(
            pendingApprovalsCount = heavy.pendingCount,
            lowStockVariants = lowStockItems,
            upcomingBills = heavy.upcomingBills,
            warehouseStats = whStats,
            systemStats = systemStats,
            notifications = constructNotifications(heavy.upcomingBills, heavy.pendingCount, lowStockItems, Date()),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        loadHeavyDataOnce()
    }

    fun refresh() {
        loadHeavyDataOnce()
        refreshTrigger.value += 1
    }

    private fun loadHeavyDataOnce() {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser() ?: return@launch
                val isAdmin = user.role == "admin"

                val pendingCount = if (isAdmin) {
                    val pEntries = stockEntryRepository.getPendingCount()
                    val pReqs = approvalRepository.getPendingRequestsFlow().take(1).first()
                    pEntries + pReqs.size
                } else 0

                val warehouses = warehouseRepository.getWarehousesOnce()

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

                _heavyData.value = HeavyData(pendingCount, warehouses, upcoming)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error loading heavy data", e)
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
            allNotifications.add(AppNotification(
                "bill_${bill.id}",
                if (isOverdue) "كمبيالة متأخرة" else "موعد استحقاق قريب",
                "الكمبيالة: ${bill.description} تستحق بتاريخ ${java.text.SimpleDateFormat("yyyy/MM/dd").format(bill.dueDate)}",
                if (isOverdue) NotificationType.OVERDUE_BILL else NotificationType.UPCOMING_BILL,
                "bills?highlightBillId=${bill.id}"
            ))
        }
        
        allNotifications.sortBy { if (it.type == NotificationType.OVERDUE_BILL) 0 else 1 }
        
        if (pendingCount > 0) {
            allNotifications.add(AppNotification("pending_approvals", "موافقات معلقة", "لديك $pendingCount طلبات بانتظار الموافقة", NotificationType.PENDING_APPROVAL, "approvals"))
        }
        
        lowStockItems.forEach { item ->
            val specLabel = if (item.specification.isNotBlank()) "${item.specification}|" else ""
            val message = "(${item.warehouseName}: $specLabel${item.capacity}A)"
            val route = "product_ledger/${item.variantId}/${item.productName}/${item.capacity}/${item.specification.ifEmpty { "no_spec" }}"

            allNotifications.add(AppNotification(
                "low_stock_${item.variantId}_${item.warehouseName}", 
                "مخزون منخفض: ${item.productName}", 
                message,
                NotificationType.LOW_STOCK, 
                route
            ))
        }
        
        return allNotifications
    }
}
