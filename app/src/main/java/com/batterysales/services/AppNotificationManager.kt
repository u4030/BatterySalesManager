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
    private var upcomingBillsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var lowStockJob: kotlinx.coroutines.Job? = null

    fun startListening() {
        if (isInitialized) return
        isInitialized = true
        Log.d("AppNotificationManager", "Starting notification listeners")

        userRepository.getCurrentUserFlow()
            .onEach { user ->
                Log.d("AppNotificationManager", "User changed: ${user?.displayName}, role: ${user?.role}")
                pendingEntriesListener?.remove()
                upcomingBillsListener?.remove()
                lowStockJob?.cancel()

                if (user != null) {
                    setupRealtimeListeners(user)
                    setupUpcomingBillsListener()
                    setupLowStockListener()
                } else {
                    notifiedLowStockKeys.clear()
                    notifiedBillIds.clear()
                }
            }.launchIn(scope)
    }

    private fun setupUpcomingBillsListener() {
        upcomingBillsListener?.remove()

        var hasEmittedData = false
        // Listener for Upcoming Bills/Checks
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
                            message.toString()
                        )
                        // Add to notified list to avoid individual notifications immediately
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

        // Listener for NEW Pending Stock Entries (For Admins)
        if (user.role == User.ROLE_ADMIN) {
            var isFirstSnapshot = true
            pendingEntriesListener = firestore.collection(StockEntry.COLLECTION_NAME)
                .whereEqualTo("status", StockEntry.STATUS_PENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener

                    if (isFirstSnapshot) {
                        isFirstSnapshot = false
                        return@addSnapshotListener
                    }

                    snapshot?.documentChanges?.forEach { change ->
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
        }
    }

    private fun setupLowStockListener() {
        lowStockJob?.cancel()

        // Setup a listener for NEW Stock Entries to trigger targeted low stock checks
        // This avoids periodic full-database scans while maintaining near real-time accuracy.
        var isFirstSnapshot = true
        lowStockJob = firestore.collection(StockEntry.COLLECTION_NAME)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (isFirstSnapshot) {
                    isFirstSnapshot = false
                    // Perform initial check on startup
                    scope.launch { performFullLowStockCheck() }
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val entry = change.document.toObject(StockEntry::class.java)
                        // Trigger check for the specific variant/warehouse affected
                        scope.launch { checkVariantLowStock(entry.productVariantId, entry.warehouseId) }
                    }
                }
            }.let { registration ->
                // Return a job that removes the listener when cancelled
                scope.launch {
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        registration.remove()
                    }
                }
            }
    }

    private suspend fun checkVariantLowStock(variantId: String, warehouseId: String) {
        try {
            val variant = productVariantRepository.getVariant(variantId) ?: return
            if (variant.archived) return

            val threshold = variant.minQuantities[warehouseId] ?: variant.minQuantity
            if (threshold <= 0) return

            val whSummary = stockEntryRepository.getVariantSummary(variantId, warehouseId)
            val qty = whSummary.first

            val key = "$variantId:$warehouseId"
            if (qty <= threshold) {
                if (!notifiedLowStockKeys.contains(key)) {
                    val product = productRepository.getProduct(variant.productId)
                    NotificationHelper.showNotification(
                        context,
                        "تنبيه مخزون منخفض",
                        "المنتج ${product?.name ?: ""} (${variant.capacity}A) وصل للحد الأدنى في المستودع"
                    )
                    notifiedLowStockKeys.add(key)
                }
            } else {
                // Reset notification if stock is replenished
                notifiedLowStockKeys.remove(key)
            }
        } catch (e: Exception) {
            Log.e("AppNotificationManager", "Error checking targeted low stock", e)
        }
    }

    private suspend fun performFullLowStockCheck() {
        try {
            val allVariants = productVariantRepository.getAllVariants().filter { !it.archived }
            val allWarehouses = warehouseRepository.getWarehousesOnce()

            var lowStockCount = 0
            for (variant in allVariants) {
                for (warehouse in allWarehouses) {
                    val threshold = variant.minQuantities[warehouse.id] ?: variant.minQuantity
                    if (threshold <= 0) continue

                    val whSummary = stockEntryRepository.getVariantSummary(variant.id, warehouse.id)
                    if (whSummary.first <= threshold) {
                        lowStockCount++
                        notifiedLowStockKeys.add("${variant.id}:${warehouse.id}")
                    }
                }
            }

            if (lowStockCount > 0) {
                NotificationHelper.showNotification(
                    context,
                    "ملخص المخزون المنخفض",
                    "يوجد عدد $lowStockCount أصناف وصلت للحد الأدنى للمخزون"
                )
            }
        } catch (e: Exception) {
            Log.e("AppNotificationManager", "Error in full low stock check", e)
        }
    }
}
