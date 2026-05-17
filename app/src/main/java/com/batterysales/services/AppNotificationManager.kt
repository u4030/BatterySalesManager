package com.batterysales.services

import android.content.Context
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import com.batterysales.ui.components.NotificationHelper
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stockEntryRepository: StockEntryRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productRepository: com.batterysales.data.repositories.ProductRepository,
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository,
    private val firestore: FirebaseFirestore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isInitialized = false
    private val notifiedLowStockKeys = mutableSetOf<String>() // Key: "variantId:warehouseId"
    private val notifiedBillIds = mutableSetOf<String>()

    private var pendingEntriesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var pendingRequestsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var upcomingBillsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var lowStockListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun startListening() {
        if (isInitialized) return
        isInitialized = true
        Log.d("AppNotificationManager", "Starting notification listeners")

        userRepository.getCurrentUserFlow()
            .onEach { user ->
                Log.d("AppNotificationManager", "User changed: ${user?.displayName}, role: ${user?.role}")
                pendingEntriesListener?.remove()
                pendingRequestsListener?.remove()
                upcomingBillsListener?.remove()
                lowStockListener?.remove()

                if (user != null) {
                    setupRealtimeListeners(user)
                    if (user.role != User.ROLE_SELLER) {
                        setupUpcomingBillsListener()
                    }
                    setupTargetedLowStockListener(user)
                } else {
                    notifiedLowStockKeys.clear()
                    notifiedBillIds.clear()
                }
            }.launchIn(scope)
    }

    private fun setupUpcomingBillsListener() {
        upcomingBillsListener?.remove()

        var hasEmittedData = false
        upcomingBillsListener = firestore.collection(Bill.COLLECTION_NAME)
            .whereNotEqualTo("status", BillStatus.PAID)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot == null) return@addSnapshotListener

                val now = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0) }
                val nextWeek = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 7)
                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                }

                val allRelevantBills = snapshot.documents.mapNotNull {
                    it.toObject(Bill::class.java)?.copy(id = it.id)
                }.filter { bill ->
                    bill.status != BillStatus.PAID &&
                            !bill.dueDate.after(nextWeek.time)
                }

                val overdueCount = allRelevantBills.count { it.dueDate.before(now.time) }
                val upcomingBills = allRelevantBills.filter { !it.dueDate.before(now.time) }

                if (!hasEmittedData) {
                    hasEmittedData = true
                    if (allRelevantBills.isNotEmpty()) {
                        val message = StringBuilder()
                        if (overdueCount > 0) {
                            message.append("يوجد عدد $overdueCount كمبيالات متأخرة! ")
                        }

                        if (upcomingBills.isNotEmpty()) {
                            val minDaysRemaining = upcomingBills.minOf { bill ->
                                val diff = bill.dueDate.time - now.time.time
                                (diff / (1000 * 60 * 60 * 24)).toInt()
                            }
                            message.append(when (minDaysRemaining) {
                                0 -> "يوجد كمبيالات تستحق اليوم"
                                1 -> "يوجد كمبيالات تستحق غداً"
                                else -> "يوجد كمبيالات تستحق خلال $minDaysRemaining أيام"
                            })
                        }

                        NotificationHelper.showNotification(
                            context,
                            "تنبيه الالتزامات المالية",
                            message.toString(),
                            playSound = false
                        )
                        allRelevantBills.forEach { notifiedBillIds.add(it.id) }
                    }
                    return@addSnapshotListener
                }

                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                        change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {

                        val bill = change.document.toObject(Bill::class.java).copy(id = change.document.id)

                        if (bill.status != BillStatus.PAID && !bill.dueDate.after(nextWeek.time)) {
                            if (!notifiedBillIds.contains(bill.id)) {
                                val isOverdue = bill.dueDate.before(now.time)
                                val title = if (isOverdue) "كمبيالة متأخرة!" else "موعد استحقاق قريب"
                                val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                                NotificationHelper.showNotification(
                                    context,
                                    title,
                                    "الكمبيالة: ${bill.description} ${if (isOverdue) "كانت تستحق" else "تستحق"} بتاريخ ${dateFormatter.format(bill.dueDate)}"
                                )
                                notifiedBillIds.add(bill.id)
                            }
                        }
                    }
                }
            }
    }

    private fun setupRealtimeListeners(user: User) {
        pendingEntriesListener?.remove()
        pendingRequestsListener?.remove()

        if (user.role == User.ROLE_ADMIN) {
            var isFirstSnapshotEntries = true
            pendingEntriesListener = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereEqualTo("status", StockEntry.STATUS_PENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    if (isFirstSnapshotEntries) {
                        isFirstSnapshotEntries = false
                        if (!snapshot.isEmpty) {
                            NotificationHelper.showNotification(
                                context,
                                "موافقات معلقة",
                                "لديك عدد ${snapshot.size()} قيود بانتظار الموافقة",
                                playSound = false,
                                notificationId = 3000
                            )
                        }
                        return@addSnapshotListener
                    }
                    snapshot.documentChanges.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val entry = change.document.toObject(StockEntry::class.java)
                            val userSuffix = if (entry.createdByUserName.isNotEmpty()) " بواسطة ${entry.createdByUserName}" else ""
                            NotificationHelper.showNotification(
                                context,
                                "طلب موافقة جديد",
                                "يوجد طلب ${if (entry.supplier == "Transfer") "ترحيل" else "إدخال"} مخزون جديد بانتظار الموافقة$userSuffix"
                            )
                        }
                    }
                }

            var isFirstSnapshotRequests = true
            pendingRequestsListener = firestore.collection(ApprovalRequest.COLLECTION_NAME)
                .whereEqualTo("status", ApprovalRequest.STATUS_PENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    if (isFirstSnapshotRequests) {
                        isFirstSnapshotRequests = false
                        if (!snapshot.isEmpty) {
                            NotificationHelper.showNotification(
                                context,
                                "طلبات تعديل معلقة",
                                "لديك عدد ${snapshot.size()} طلبات حذف/تعديل بانتظار الموافقة",
                                playSound = false,
                                notificationId = 4000
                            )
                        }
                        return@addSnapshotListener
                    }
                    snapshot.documentChanges.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val req = change.document.toObject(ApprovalRequest::class.java)
                            val action = if (req.actionType == ApprovalRequest.ACTION_EDIT) "تعديل" else "حذف"
                            val target = if (req.targetType == ApprovalRequest.TARGET_PRODUCT) "منتج" else "سعة"
                            NotificationHelper.showNotification(
                                context,
                                "طلب موافقة جديد",
                                "يوجد طلب $action $target (${req.productName}) بانتظار الموافقة بواسطة ${req.requesterName}"
                            )
                        }
                    }
                }
        }
    }

    /**
     * Event-Driven Low Stock Listener:
     * Listens only to the system_alerts collection.
     */
    private fun setupTargetedLowStockListener(user: User) {
        lowStockListener?.remove()

        var isFirstSnapshot = true
        lowStockListener = firestore.collection(com.batterysales.data.models.SystemAlert.COLLECTION_NAME)
            .whereEqualTo("type", com.batterysales.data.models.SystemAlert.TYPE_LOW_STOCK)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val isAdmin = user.role == "admin"
                val myWarehouseId = user.warehouseId

                val relevantAlerts = snapshot.documents.mapNotNull { it.toObject(SystemAlert::class.java) }
                    .filter { isAdmin || it.warehouseId == myWarehouseId }

                if (isFirstSnapshot) {
                    isFirstSnapshot = false
                    relevantAlerts.forEach { alert ->
                        notifiedLowStockKeys.add("${alert.relatedId}:${alert.warehouseId}")
                    }

                    if (relevantAlerts.isNotEmpty()) {
                        NotificationHelper.showNotification(
                            context,
                            "تنبيه المخزون",
                            "لديك عدد ${relevantAlerts.size} أصناف برصيد منخفض",
                            playSound = false,
                            notificationId = 2000
                        )
                    }
                    return@addSnapshotListener
                }

                snapshot.documentChanges.forEach { change ->
                    val alert = change.document.toObject(SystemAlert::class.java) ?: return@forEach
                    if (!isAdmin && alert.warehouseId != myWarehouseId) return@forEach

                    val key = "${alert.relatedId}:${alert.warehouseId}"

                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        if (!notifiedLowStockKeys.contains(key)) {
                            NotificationHelper.showNotification(
                                context,
                                alert.title,
                                alert.message,
                                notificationId = key.hashCode()
                            )
                            notifiedLowStockKeys.add(key)
                        }
                    } else if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                        notifiedLowStockKeys.remove(key)
                    }
                }
            }
    }
}
