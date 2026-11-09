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
    val blockingConflictError: String? = null,
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
    private val persistentSyncManager: com.batterysales.data.local.PersistentSyncManager,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    // حارس التنبيهات المطرودة محلياً في الذاكرة الحية (0ms) لمنع الـ Phantom Alerts والـ Metadata Influx
    private val suppressedAlertIds = MutableStateFlow<Set<String>>(emptySet())
    private val _locallySuppressedAlertIds = suppressedAlertIds
    private val _blockingConflictError = MutableStateFlow<String?>(null)

    /**
     * Programmatic barrier call: Prunes any variant or composite alert immediately in 0ms,
     * ignoring remote Firestore sync states and dual-stage metadata transitions.
     */
    fun evictAlertInstantly(variantId: String) {
        suppressedAlertIds.update { it + variantId }
        viewModelScope.launch {
            persistentSyncManager.recordPendingOperation(
                com.batterysales.data.local.PendingSyncOperation(
                    operationId = "suppress_${System.currentTimeMillis()}_$variantId",
                    type = "ALERT_SUPPRESS",
                    relatedId = variantId,
                    status = "PENDING_SYNC"
                )
            )
        }
    }

    fun clearAlertInstantly(variantId: String) {
        evictAlertInstantly(variantId)
    }

    fun acknowledgeConflictResolution() {
        _blockingConflictError.value = null
        viewModelScope.launch {
            persistentSyncManager.removePendingOperation("CRITICAL_CONFLICT_LOCK")
        }
    }

    fun dismissBlockingConflict() {
        acknowledgeConflictResolution()
    }

    fun setBlockingConflict(msg: String) {
        _blockingConflictError.value = msg
        viewModelScope.launch {
            persistentSyncManager.recordPendingOperation(
                com.batterysales.data.local.PendingSyncOperation(
                    operationId = "CRITICAL_CONFLICT_LOCK",
                    type = "CONFLICT_LOCK",
                    relatedId = "CRITICAL_CONFLICT",
                    payloadJson = msg,
                    status = "PENDING_SYNC"
                )
            )
        }
    }

    fun suppressAlertLocally(variantId: String, warehouseId: String) {
        val compositeId = "low_stock_${variantId}_${warehouseId}"
        _locallySuppressedAlertIds.update { it + compositeId + variantId + "bill_$variantId" }
        viewModelScope.launch {
            // التوطين الصلب لمنع التنبيهات الوهمية حتى لو انطفأ الهاتف
            persistentSyncManager.recordPendingOperation(
                com.batterysales.data.local.PendingSyncOperation(
                    operationId = "suppress_${System.currentTimeMillis()}_$compositeId",
                    type = "ALERT_SUPPRESS",
                    relatedId = compositeId,
                    status = "PENDING_SYNC"
                )
            )
        }
    }

    fun triggerOptimisticAlert(variantId: String, warehouseId: String, threshold: Int = 0) {
        val compositeId = "low_stock_${variantId}_${warehouseId}"
        _locallySuppressedAlertIds.update { it - compositeId - variantId }
        viewModelScope.launch {
            persistentSyncManager.removePendingOperation("suppress_${System.currentTimeMillis()}_$compositeId")
        }
    }

    /**
     * Optimistically prunes a low-stock alert immediately from local UI state in 0ms,
     * annihilating delayed viewport gaps without waiting for remote Firestore snapshot round-trips.
     */
    fun evictAlertImmediately(variantId: String, warehouseId: String = "") {
        val compositeId = if (warehouseId.isNotEmpty()) "low_stock_${variantId}_${warehouseId}" else "low_stock_$variantId"
        _locallySuppressedAlertIds.update { it + compositeId + variantId }
        viewModelScope.launch {
            persistentSyncManager.recordPendingOperation(
                com.batterysales.data.local.PendingSyncOperation(
                    operationId = "suppress_${System.currentTimeMillis()}_$compositeId",
                    type = "ALERT_SUPPRESS",
                    relatedId = compositeId,
                    status = "PENDING_SYNC"
                )
            )
        }
    }

    data class AlertStreamContext(
        val user: User?,
        val versionToken: Long,
        val suppressedIds: Set<String>,
        val pendingSuppressedIds: Set<String>
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val lowStockAlertsFlow: Flow<List<SystemAlert>> = combine(
        userRepository.getCurrentUserFlow(),
        summaryRepository.getFinancialStatusFlow(),
        _locallySuppressedAlertIds,
        persistentSyncManager.pendingOperationsFlow
    ) { user, financialStatus, suppressedIds, pendingOps ->
        val diskSuppressed = pendingOps
            .filter { it.type == "ALERT_SUPPRESS" }
            .map { it.relatedId }
            .toSet()
        AlertStreamContext(
            user = user,
            versionToken = financialStatus.version,
            suppressedIds = suppressedIds,
            pendingSuppressedIds = diskSuppressed
        )
    }.flatMapLatest { ctx ->
        val user = ctx.user
        val allSuppressed = ctx.suppressedIds + ctx.pendingSuppressedIds
        if (user == null) {
            flowOf(emptyList())
        } else {
            val isAdmin = user.role == "admin"
            val alertsSourceFlow = if (isAdmin) {
                // Admins observe all active system alerts via systemAlertRepository
                systemAlertRepository.getSystemAlertsFlow()
            } else {
                // Sellers / warehouse managers observe their warehouse low stock alerts atomically with 0ms latency
                val whId = user.warehouseId ?: ""
                salesRepository.listenToLowStockAlerts(whId).map { rawList ->
                    rawList.mapNotNull { map ->
                        val docId = map["id"]?.toString() ?: ""
                        if (docId.isBlank() || map.isEmpty()) return@mapNotNull null
                        
                        val isResolved = (map["resolved"] as? Boolean) ?: false
                        val status = map["status"]?.toString() ?: "active"
                        if (isResolved || status == "resolved" || status == "deleted") return@mapNotNull null

                        val dataMap = (map["data"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value ?: "" } ?: emptyMap<String, Any>()
                        SystemAlert(
                            id = docId,
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
                            resolved = false,
                            status = status,
                            data = dataMap
                        )
                    }
                }
            }

            // ربط قسري لتدفق التنبيهات مع رمز النسخة version وطرد المعزول محلياً بـ 0ms
            // حظر وبتر حتمي ضد Dual-Stage Metadata Influx: فحص قاطع للوثيقة والمعرفات بغض النظر عن ميتاداتا السيرفر وحالة hasPendingWrites
            alertsSourceFlow.map { list ->
                list.filter { alert ->
                    val docId = alert.id
                    val vId = alert.variantId.ifEmpty { alert.relatedId }
                    val rId = alert.relatedId.ifEmpty { alert.variantId }
                    val compositeId = "low_stock_${alert.relatedId}_${alert.warehouseId ?: ""}"
                    val fallbackCompositeId = "low_stock_${rId}_${alert.warehouseId ?: ""}"
                    val variantCompositeId = "low_stock_${vId}_${alert.warehouseId ?: ""}"

                    val isLocallySuppressed = (docId.isNotBlank() && allSuppressed.contains(docId)) ||
                            (alert.id.isNotBlank() && allSuppressed.contains(alert.id)) ||
                            (alert.variantId.isNotBlank() && allSuppressed.contains(alert.variantId)) ||
                            (alert.relatedId.isNotBlank() && allSuppressed.contains(alert.relatedId)) ||
                            (vId.isNotBlank() && allSuppressed.contains(vId)) ||
                            (rId.isNotBlank() && allSuppressed.contains(rId)) ||
                            allSuppressed.contains(compositeId) ||
                            allSuppressed.contains(fallbackCompositeId) ||
                            allSuppressed.contains(variantCompositeId)

                    if (isLocallySuppressed) {
                        return@filter false
                    }

                    !alert.resolved && 
                    alert.status != "resolved" && 
                    alert.status != "deleted"
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

    private val _optimisticApprovalDelta = MutableStateFlow(0)
    val optimisticApprovalDelta = _optimisticApprovalDelta.asStateFlow()

    fun decrementApprovalCount() {
        _optimisticApprovalDelta.update { it - 1 }
    }

    fun incrementApprovalCount() {
        _optimisticApprovalDelta.update { it + 1 }
    }

    private val pendingCountFlow: Flow<Int> = combine(
        stockEntryRepository.getPendingEntriesFlow().catch { emit(emptyList()) },
        approvalRepository.getPendingRequestsFlow().catch { emit(emptyList()) },
        _optimisticApprovalDelta
    ) { entries, reqs, delta ->
        val actualCount = entries.size + reqs.size
        (actualCount + delta).coerceAtLeast(0)
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
        
        // تأمين جلب الأرصدة خطيا بالدينار الأردني JOD
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

        // ضبط فوري للتوقيت المحلي للأردن لحساب الإحصاءات اليومية فورا (0ms)
        val jordanDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Amman")
        }
        val currentJordanDateStr = jordanDateFormat.format(Date())

        // Fallback snapshot directly from the repository's internal disk/memory cache to annihilate microsecond race conditions
        val repoCachedWarehouses = warehouseRepository.getCachedWarehousesSnapshot().ifEmpty {
            try {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    warehouseRepository.getWarehousesOnce()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        val combinedWarehouses = if (warehouses.isNotEmpty()) warehouses else repoCachedWarehouses
        val warehouseMap = combinedWarehouses.associateBy { it.id }.toMutableMap()

        // Local Join Resolver: إعطاء الأولوية المطلقة والكاملة لكاش المستودعات المحلي النظيف
        fun resolveWarehouseName(whId: String, candidateName: String? = null): String {
            if (whId.isBlank()) return "مستودع فرعي"

            // 1. Prioritize reactive flow state memory list
            val flowName = warehouses.find { it.id == whId }?.name
                ?.takeIf { it.isNotBlank() && it != whId && !it.equals("مستودع غير معرف", ignoreCase = true) }
                ?: _warehouses.value.find { it.id == whId }?.name
                ?.takeIf { it.isNotBlank() && it != whId && !it.equals("مستودع غير معرف", ignoreCase = true) }
                ?: combinedWarehouses.find { it.id == whId }?.name
                ?.takeIf { it.isNotBlank() && it != whId && !it.equals("مستودع غير معرف", ignoreCase = true) }
            if (!flowName.isNullOrBlank()) {
                return flowName
            }

            // 2. FORCE immediate synchronous hit to the repository's core local hardware memory cache
            val repoCacheName = warehouseRepository.getWarehouseByIdCached(whId)?.name
                ?.takeIf { it.isNotBlank() && it != whId && !it.equals("مستودع غير معرف", ignoreCase = true) }
            if (!repoCacheName.isNullOrBlank()) {
                return repoCacheName
            }

            // 3. Fallback to candidate name from server if available
            if (!candidateName.isNullOrBlank() && candidateName != whId && !candidateName.equals("مستودع غير معرف", ignoreCase = true)) {
                return candidateName
            }

            // 4. Structural safe guards for core treasury
            if (whId.contains("main", ignoreCase = true) || whId == "main_treasury") {
                return "الخزينة الرئيسية"
            }

            // Return the actual warehouse Id as a unique key descriptor rather than printing a false generic text
            return whId
        }

        // Rigid Local Join Resolver for Daily Ledgers:
        // Synchronously compares ledger's warehouseId against clean local cached warehouse list and re-stamps true Arabic name
        fun resolveDailyLedgerName(ledger: DailyLedger): DailyLedger {
            val targetWhId = ledger.resolvedWarehouseId
            val cleanArabicName = warehouses.find { it.id == targetWhId }?.name
                ?.takeIf { it.isNotBlank() && it != targetWhId && !it.equals("مستودع غير معرف", ignoreCase = true) }
                ?: warehouseMap[targetWhId]?.name
                ?.takeIf { it.isNotBlank() && it != targetWhId && !it.equals("مستودع غير معرف", ignoreCase = true) }
                ?: if (targetWhId.contains("main", ignoreCase = true) || targetWhId == "main_treasury") "الخزينة الرئيسية" else "مستودع فرعي"

            return ledger.copy(
                warehouseName = cleanArabicName,
                warehouseNameCamel = cleanArabicName
            )
        }
        
        // بناء بطاقات المستودعات بالاعتماد النشط على مفاتيح warehouseBalances الحية لكسر جمود التدفق تماماً
        val whStats = if (isAdmin) {
            val allWhIds = (financialStatus.warehouseBalances.keys + warehouses.map { it.id }).distinct()
            allWhIds.map { whId ->
                val name = resolveWarehouseName(whId)
                val whFinancial = financialStatus.warehouseBalances[whId]
                
                // حارس التوقيت التزامني: إذا مر المبيع الأول، نجبر قنوات الواجهة على الإطلاق فورا
                val hasTodayActivity = whFinancial?.lastInvoiceDate == currentJordanDateStr || 
                    (whFinancial?.lastInvoiceDate.isNullOrEmpty() && (whFinancial?.todayCollection ?: 0.0) > 0.0)
                    
                val todayColl = if (hasTodayActivity) (whFinancial?.todayCollection ?: 0.0) else 0.0
                val todayCount = if (hasTodayActivity) (whFinancial?.todayCollectionCount ?: 0) else 0
                WarehouseStats(whId, name, todayColl, todayCount)
            }
        } else {
            val targetWhId = userWarehouseId ?: ""
            val name = resolveWarehouseName(targetWhId)
            val whFinancial = financialStatus.warehouseBalances[targetWhId]
            
            val hasTodayActivity = whFinancial?.lastInvoiceDate == currentJordanDateStr || 
                (whFinancial?.lastInvoiceDate.isNullOrEmpty() && (whFinancial?.todayCollection ?: 0.0) > 0.0)
                
            val todayColl = if (hasTodayActivity) (whFinancial?.todayCollection ?: 0.0) else 0.0
            val todayCount = if (hasTodayActivity) (whFinancial?.todayCollectionCount ?: 0) else 0
            listOf(WarehouseStats(targetWhId, name, todayColl, todayCount))
        }

        // فرز وتجهيز التنبيهات اللحظية المطرودة محليا بـ 0ms مع استهلاك رمز الإصدار version لإعادة التقييم القسري عند إلغاء الفاتورة
        val broadcastVersion = financialStatus.version // بث إشاري حاسم لالتقاط البرودكاست

        val lowStockItems = alerts.filter { alert ->
            // فحص صارم ومزدوج ومحكم ضد Dual-Stage Metadata Influx
            val docId = alert.id
            val targetVariantId = alert.variantId.ifEmpty { alert.relatedId }
            val targetRelatedId = alert.relatedId.ifEmpty { alert.variantId }
            val cId = "low_stock_${targetRelatedId}_${alert.warehouseId ?: ""}"
            val vcId = "low_stock_${targetVariantId}_${alert.warehouseId ?: ""}"
            val suppressedSet = _locallySuppressedAlertIds.value

            val isSuppressed = (docId.isNotBlank() && suppressedSet.contains(docId)) ||
                    (targetVariantId.isNotBlank() && suppressedSet.contains(targetVariantId)) ||
                    (targetRelatedId.isNotBlank() && suppressedSet.contains(targetRelatedId)) ||
                    suppressedSet.contains(cId) ||
                    suppressedSet.contains(vcId)

            if (isSuppressed) return@filter false

            !alert.resolved && alert.status != "resolved" && alert.status != "deleted" &&
            (isAdmin || alert.warehouseId == userWarehouseId)
        }.map { alert ->
            val rawCap = alert.capacity ?: alert.data["capacity"]
            val capacity: Int = when (rawCap) {
                is Number -> rawCap.toInt()
                is String -> rawCap.toIntOrNull() ?: 0
                else -> 0
            }
            val specification = alert.specification.ifEmpty { (alert.data["specification"] as? String) ?: "" }
            val rawStock = alert.currentStock ?: alert.data["currentStock"]
            val currentQty: Int = when (rawStock) {
                is Number -> rawStock.toInt()
                is String -> rawStock.toIntOrNull() ?: 0
                else -> 0
            }
            val rawMin = alert.threshold ?: alert.data["threshold"]
            val minQty: Int = when (rawMin) {
                is Number -> rawMin.toInt()
                is String -> rawMin.toIntOrNull() ?: 0
                else -> 0
            }
            val displayName = alert.title.removePrefix("تنبيه: ").ifBlank { "بطارية صنف" }
            
            LowStockItem(
                variantId = alert.relatedId,
                productName = displayName,
                capacity = capacity,
                specification = specification,
                currentQuantity = currentQty,
                minQuantity = minQty,
                warehouseName = resolveWarehouseName(alert.warehouseId ?: "", alert.warehouseName),
                warehouseId = alert.warehouseId ?: ""
            )
        }

        DashboardUiState(
            pendingApprovalsCount = if (isAdmin) pendingCount else 0,
            lowStockVariants = lowStockItems,
            upcomingBills = if (isAdmin) bills else emptyList(),
            warehouseStats = whStats,
            systemStats = systemStats,
            blockingConflictError = _blockingConflictError.value,
            notifications = constructNotifications(if (isAdmin) bills else emptyList(), if (isAdmin) pendingCount else 0, lowStockItems, Date()),
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
