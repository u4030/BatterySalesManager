package com.batterysales.services

import android.content.Context
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.User
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.UserRepository
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

        // Listener for Upcoming Bills/Checks
        upcomingBillsListener = firestore.collection(com.batterysales.data.models.Bill.COLLECTION_NAME)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                val now = java.util.Calendar.getInstance()
                val nextWeek = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 7) }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                        change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {

                        val bill = change.document.toObject(com.batterysales.data.models.Bill::class.java).copy(id = change.document.id)

                        if (bill.status != com.batterysales.data.models.BillStatus.PAID &&
                            bill.dueDate.after(now.time) &&
                            bill.dueDate.before(nextWeek.time)) {

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
        var isFirstSnapshot = true
        combine(
            stockEntryRepository.getAllStockEntriesFlow(),
            productVariantRepository.getAllVariantsFlow(),
            productRepository.getProducts()
        ) { allEntries, allVariants, allProducts ->
            val productMap = allProducts.associateBy { it.id }

            allVariants.filter { !it.isArchived && it.minQuantity > 0 }.forEach { variant ->
                val currentQty = allEntries.filter { it.productVariantId == variant.id && it.status == StockEntry.STATUS_APPROVED }.sumOf { it.quantity }

                if (currentQty <= variant.minQuantity) {
                    if (!notifiedLowStockIds.contains(variant.id)) {
                        if (!isFirstSnapshot) {
                            val product = productMap[variant.productId]
                            NotificationHelper.showNotification(
                                context,
                                "تنبيه انخفاض المخزون",
                                "المنتج ${product?.name ?: ""} (${variant.capacity} أمبير) وصل للحد الأدنى ($currentQty)"
                            )
                        }
                        notifiedLowStockIds.add(variant.id)
                    }
                } else {
                    // Reset if stock goes up
                    notifiedLowStockIds.remove(variant.id)
                }
            }

            if (isFirstSnapshot) {
                isFirstSnapshot = false
                val initialLowCount = allVariants.count { variant ->
                    !variant.isArchived && variant.minQuantity > 0 &&
                    allEntries.filter { it.productVariantId == variant.id && it.status == StockEntry.STATUS_APPROVED }.sumOf { it.quantity } <= variant.minQuantity
                }
                if (initialLowCount > 0) {
                    NotificationHelper.showNotification(
                        context,
                        "تنبيه المخزون المنخفض",
                        "يوجد عدد $initialLowCount أصناف وصلت للحد الأدنى للمخزون"
                    )
                }
            }
        }.launchIn(scope)
    }
}
