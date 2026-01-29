package com.batterysales.services

import android.content.Context
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import com.batterysales.ui.components.NotificationHelper
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
    private val firestore: FirebaseFirestore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isInitialized = false
    private val notifiedLowStockIds = mutableSetOf<String>()

    fun startListening() {
        if (isInitialized) return
        isInitialized = true

        userRepository.getCurrentUserFlow()
            .onEach { user ->
                pendingEntriesListener?.remove()
                upcomingBillsListener?.remove()

                if (user != null) {
                    setupRealtimeListeners(user)
                    setupUpcomingBillsListener()
                }
            }.launchIn(scope)

        setupLowStockListener()
    }

    private var pendingEntriesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var upcomingBillsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val notifiedBillIds = mutableSetOf<String>()

    private fun setupUpcomingBillsListener() {
        upcomingBillsListener?.remove()

        var hasEmittedData = false
        // Listener for Upcoming Bills/Checks
        upcomingBillsListener = firestore.collection(Bill.COLLECTION_NAME)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot == null) return@addSnapshotListener

                val now = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0) }
                val nextWeek = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 7)
                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                }

                val upcomingBills = snapshot.documents.mapNotNull {
                    it.toObject(Bill::class.java)?.copy(id = it.id)
                }.filter { bill ->
                    bill.status != BillStatus.PAID &&
                    !bill.dueDate.before(now.time) &&
                    !bill.dueDate.after(nextWeek.time)
                }

                if (!hasEmittedData) {
                    hasEmittedData = true
                    if (upcomingBills.isNotEmpty()) {
                        NotificationHelper.showNotification(
                            context,
                            "كمبيالات مستحقة قريباً",
                            "يوجد عدد ${upcomingBills.size} كمبيالات/شيكات تستحق خلال الـ 7 أيام القادمة"
                        )
                        // Add to notified list to avoid individual notifications immediately
                        upcomingBills.forEach { notifiedBillIds.add(it.id) }
                    }
                    return@addSnapshotListener
                }

                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                        change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {

                        val bill = change.document.toObject(Bill::class.java).copy(id = change.document.id)

                        if (bill.status != BillStatus.PAID &&
                            !bill.dueDate.before(now.time) &&
                            !bill.dueDate.after(nextWeek.time)) {

                            if (!notifiedBillIds.contains(bill.id)) {
                                val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                                NotificationHelper.showNotification(
                                    context,
                                    "موعد استحقاق قريب",
                                    "الكمبيالة: ${bill.description} تستحق بتاريخ ${dateFormatter.format(bill.dueDate)}"
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
                            NotificationHelper.showNotification(
                                context,
                                "طلب موافقة جديد",
                                "يوجد طلب ${if (entry.supplier == "Transfer") "ترحيل" else "إدخال"} مخزون جديد بانتظار الموافقة"
                            )
                        }
                    }
                }
        }
    }

    private fun setupLowStockListener() {
        var hasEmittedData = false
        combine(
            stockEntryRepository.getAllStockEntriesFlow(),
            productVariantRepository.getAllVariantsFlow(),
            productRepository.getProducts()
        ) { allEntries, allVariants, allProducts ->
            // Skip if no variants exist yet (likely still loading or really empty)
            // But we should allow it if products are loaded and variants are empty but arrived.
            // Firestore might emit empty list for a collection that exists but has no data.
            // To be safe, we wait for both to emit at least once.
            // Since combine waits for all, if any list is empty it means it's either truly empty or still loading.

            val productMap = allProducts.associateBy { it.id }

            // Group entries to optimize calculation
            val stockMap = allEntries.filter { it.status == StockEntry.STATUS_APPROVED }
                .groupBy { it.productVariantId }
                .mapValues { it.value.sumOf { entry -> entry.quantity } }

            val lowStockVariants = allVariants.filter {
                !it.archived && it.minQuantity > 0 && (stockMap[it.id] ?: 0) <= it.minQuantity
            }

            if (!hasEmittedData) {
                // If we have products but no variants yet, we might be in an intermediate state.
                // However, if both collections are checked and we still have no variants, then it's fine.
                if (allProducts.isNotEmpty() && allVariants.isEmpty()) return@combine

                hasEmittedData = true
                if (lowStockVariants.isNotEmpty()) {
                    NotificationHelper.showNotification(
                        context,
                        "تنبيه المخزون المنخفض",
                        "يوجد عدد ${lowStockVariants.size} أصناف وصلت للحد الأدنى للمخزون"
                    )
                }
                // Mark all existing low stock variants as notified
                lowStockVariants.forEach { notifiedLowStockIds.add(it.id) }
                return@combine
            }

            allVariants.filter { !it.archived && it.minQuantity > 0 }.forEach { variant ->
                val currentQty = stockMap[variant.id] ?: 0

                if (currentQty <= variant.minQuantity) {
                    if (!notifiedLowStockIds.contains(variant.id)) {
                        val product = productMap[variant.productId]
                        NotificationHelper.showNotification(
                            context,
                            "تنبيه انخفاض المخزون",
                            "المنتج ${product?.name ?: ""} (${variant.capacity} أمبير) وصل للحد الأدنى ($currentQty)"
                        )
                        notifiedLowStockIds.add(variant.id)
                    }
                } else {
                    // Reset if stock goes up
                    notifiedLowStockIds.remove(variant.id)
                }
            }

        }.launchIn(scope)
    }
}
