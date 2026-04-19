package com.batterysales.data.repositories

import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

class BillRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getAllBills(): List<Bill> {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
    }

    suspend fun getBill(id: String): Bill? {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME).document(id).get().await()
        return snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id)
    }

    fun getAllBillsFlow(): Flow<List<Bill>> = callbackFlow {
        val listenerRegistration = firestore.collection(Bill.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val bills = snapshot.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
                    trySend(bills).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addBill(bill: Bill): String {
        val docRef = if (bill.id.isNotEmpty()) firestore.collection(Bill.COLLECTION_NAME).document(bill.id)
                    else firestore.collection(Bill.COLLECTION_NAME).document()
        val finalBill = bill.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        docRef.set(finalBill).await()
        return docRef.id
    }

    suspend fun updateBillStatus(billId: String, status: BillStatus, paidDate: Date? = null) {
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to Date()
        )
        paidDate?.let { updates["paidDate"] = it }

        firestore.collection(Bill.COLLECTION_NAME)
            .document(billId)
            .update(updates)
            .await()
    }

    suspend fun recordPayment(billId: String, paymentAmount: Double) {
        val billRef = firestore.collection(Bill.COLLECTION_NAME).document(billId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(billRef)
            val bill = snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id) ?: return@runTransaction

            val newPaidAmount = bill.paidAmount + paymentAmount
            val newStatus = when {
                newPaidAmount >= bill.amount -> BillStatus.PAID
                newPaidAmount > 0 -> BillStatus.PARTIAL
                else -> BillStatus.UNPAID
            }

            val updates = mutableMapOf<String, Any>(
                "paidAmount" to newPaidAmount,
                "status" to newStatus,
                "updatedAt" to Date()
            )

            if (newStatus == BillStatus.PAID) {
                updates["paidDate"] = Date()
            }

            transaction.update(billRef, updates)
        }.await()
    }

    suspend fun deleteBill(billId: String) {
        firestore.collection(Bill.COLLECTION_NAME)
            .document(billId)
            .delete()
            .await()
    }

    suspend fun getBillsPaginated(
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<Bill>, DocumentSnapshot?> {
        var query = firestore.collection(Bill.COLLECTION_NAME)
            .orderBy("dueDate", Query.Direction.DESCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.limit(limit).get().await()
        val bills = snapshot.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(bills, lastDoc)
    }

    suspend fun getSupplierCredit(supplierId: String, resetDate: java.util.Date? = null, startDate: Long? = null, endDate: Long? = null): Double {
        var query = firestore.collection(Bill.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)

        resetDate?.let { query = query.whereGreaterThan("createdAt", it) }
        startDate?.let { query = query.whereGreaterThanOrEqualTo("dueDate", java.util.Date(com.batterysales.utils.DateUtils.getStartOfDay(it))) }
        endDate?.let { query = query.whereLessThanOrEqualTo("dueDate", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(it))) }

        val snapshot = query.aggregate(AggregateField.sum("paidAmount")).get(AggregateSource.SERVER).await()
        return snapshot.getDouble(AggregateField.sum("paidAmount")) ?: 0.0
    }

    suspend fun updateBill(bill: Bill) {
        val updates = mutableMapOf<String, Any>(
            "description" to bill.description,
            "amount" to bill.amount,
            "dueDate" to bill.dueDate,
            "billType" to bill.billType,
            "referenceNumber" to bill.referenceNumber,
            "supplierId" to bill.supplierId,
            "updatedAt" to Date()
        )
        firestore.collection(Bill.COLLECTION_NAME)
            .document(bill.id)
            .update(updates)
            .await()
    }

    /**
     * يقوم بتوزيع مبالغ الشيكات والكمبيالات غير المرتبطة على فواتير المشتريات غير المسددة
     * بنظام الأقدم فالأقدم (FIFO)
     */
    suspend fun autoLinkBillsForSupplier(supplierId: String, resetDate: Date? = null) {
        if (supplierId.isEmpty()) return

        // Get supplier name to match legacy entries
        val supplierDoc = firestore.collection("suppliers").document(supplierId).get().await()
        val supplierName = (supplierDoc.getString("name") ?: "").trim().lowercase()

        // 1. جلب كافة فواتير المشتريات المعتمدة للمورد
        val allEntries = firestore.collection(com.batterysales.data.models.StockEntry.COLLECTION_NAME)
            .whereEqualTo("status", "approved")
            .get()
            .await()
            .documents.mapNotNull { it.toObject(com.batterysales.data.models.StockEntry::class.java)?.copy(id = it.id) }

        val rawStockEntries = allEntries.filter { entry ->
            val matchId = entry.supplierId == supplierId
            val matchName = entry.supplier.trim().lowercase() == supplierName
            (matchId || matchName) && (resetDate == null || !entry.getEffectiveDate().before(resetDate))
        }

        // توحيد أرقام الفواتير للقيود التي تتشارك نفس معرف الطلبية
        val orderToInvoiceMap = rawStockEntries.filter { it.invoiceNumber.trim().isNotEmpty() && it.orderId.trim().isNotEmpty() }
            .associate { it.orderId.trim() to it.invoiceNumber.trim() }

        val stockEntries = rawStockEntries.map { entry ->
            val orderKey = entry.orderId.trim()
            if (entry.invoiceNumber.trim().isEmpty() && orderKey.isNotEmpty() && orderToInvoiceMap.containsKey(orderKey)) {
                entry.copy(invoiceNumber = orderToInvoiceMap[orderKey]!!)
            } else entry
        }

        // تجميع الفواتير حسب رقم الفاتورة أولاً، ثم معرف الطلبية، ثم المعرف الفريد
        val orders = stockEntries.groupBy { it.invoiceNumber.trim().ifEmpty { it.orderId.trim().ifEmpty { it.id } } }
            .map { (key, group) ->
                val totalCost = group.sumOf { it.getNetCost() }
                val effectiveDate = group.minOf { it.getEffectiveDate() }
                val sortingTimestamp = group.minOf { it.timestamp }
                key to Triple(totalCost, effectiveDate, sortingTimestamp)
            }
            .sortedWith(compareBy<Pair<String, Triple<Double, Date, Date>>> { it.second.second }
                .thenBy { it.second.third }
                .thenBy { it.first }) // الترتيب حسب الأقدم

        // 2. جلب كافة الشيكات والكمبيالات للمورد
        val allBills = firestore.collection(Bill.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
            .get()
            .await()
            .documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
            .filter { resetDate == null || !it.createdAt.before(resetDate) }

        // فصل الروابط اليدوية لحساب المبالغ المتبقية في الفواتير
        // نعتبر الربط يدوياً إذا كان الحقل relatedEntryId معبأ أو إذا كان رقم المرجع يطابق رقم الفاتورة
        val manualLinks = mutableMapOf<String, Double>()

        // خريطة لربط كل قيد فريد بالمجموعة (الفاتورة) التي ينتمي إليها
        val entryToOrderMap = stockEntries.associate { it.id to it.invoiceNumber.trim().ifEmpty { it.orderId.trim().ifEmpty { it.id } } }

        allBills.forEach { bill ->
            if (bill.relatedEntryId != null) {
                // إذا كان القيد المرتبط ينتمي لمجموعة، نربط المبلغ بالمجموعة كاملة
                val targetId = entryToOrderMap[bill.relatedEntryId] ?: bill.relatedEntryId
                val current = manualLinks.getOrDefault(targetId, 0.0)
                manualLinks[targetId] = current + bill.amount
            } else if (bill.referenceNumber.isNotEmpty()) {
                val ref = bill.referenceNumber.trim()
                // البحث عن فاتورة تطابق رقم المرجع (المرجع قد يكون رقم فاتورة أو معرف طلبية)
                orders.find { it.first == ref }?.let { (orderId, _) ->
                    val current = manualLinks.getOrDefault(orderId, 0.0)
                    manualLinks[orderId] = current + bill.amount
                }
            }
        }

        // حساب الميزان المتبقي لكل فاتورة (إجمالي التكلفة - الروابط اليدوية)
        val orderBalances = orders.map { (id, data) ->
            val (totalCost, _, _) = data
            id to (totalCost - (manualLinks[id] ?: 0.0))
        }.filter { it.second > 0.001 }.toMutableList()

        // 3. تحديد الشيكات المتاحة للربط التلقائي (غير مرتبطة يدوياً وغير مسددة كلياً)
        // نستثني أيضاً الشيكات التي تم اعتبارها مرتبطة يدوياً عبر رقم المرجع
        val manualLinkedBillIds = allBills.filter { bill ->
            bill.relatedEntryId != null ||
            (bill.referenceNumber.isNotEmpty() && orders.any { it.first == bill.referenceNumber.trim() })
        }.map { it.id }.toSet()

        val availableBills = allBills.filter { !manualLinkedBillIds.contains(it.id) }
            .sortedBy { it.createdAt } // ربط الشيكات الأقدم أولاً

        // 4. تنفيذ خوارزمية FIFO لتوزيع المبالغ
        val billUpdates = mutableMapOf<String, Pair<List<String>, Map<String, Double>>>()

        for (bill in availableBills) {
            var billRemainingAmount = bill.amount
            val linkedIds = mutableListOf<String>()
            val allocations = mutableMapOf<String, Double>()

            if (billRemainingAmount <= 0) {
                billUpdates[bill.id] = emptyList<String>() to emptyMap<String, Double>()
                continue
            }

            val iterator = orderBalances.iterator()
            while (iterator.hasNext() && billRemainingAmount > 0.001) {
                val orderBalanceEntry = iterator.next()
                val orderId = orderBalanceEntry.first
                var currentOrderBalance = orderBalanceEntry.second

                val allocation = minOf(billRemainingAmount, currentOrderBalance)

                if (allocation > 0.001) {
                    linkedIds.add(orderId)
                    allocations[orderId] = allocation
                    billRemainingAmount -= allocation
                    currentOrderBalance -= allocation

                    // تحديث الرصيد المتبقي للفاتورة في القائمة
                    val index = orderBalances.indexOfFirst { it.first == orderId }
                    if (index != -1) {
                        orderBalances[index] = orderId to currentOrderBalance
                    }
                }
            }
            billUpdates[bill.id] = linkedIds to allocations
        }

        // 5. تحديث قاعدة البيانات بالروابط الجديدة
        firestore.runTransaction { transaction ->
            // نقوم بمسح الروابط التلقائية القديمة للشيكات التي أصبحت الآن مرتبطة يدوياً
            manualLinkedBillIds.forEach { billId ->
                val docRef = firestore.collection(Bill.COLLECTION_NAME).document(billId)
                transaction.update(docRef, "autoLinkedEntryIds", emptyList<String>())
                transaction.update(docRef, "autoAllocations", emptyMap<String, Double>())
            }

            billUpdates.forEach { (billId, data) ->
                val (ids, allocations) = data
                val docRef = firestore.collection(Bill.COLLECTION_NAME).document(billId)
                transaction.update(docRef, "autoLinkedEntryIds", ids)
                transaction.update(docRef, "autoAllocations", allocations)
                transaction.update(docRef, "updatedAt", Date())
            }
        }.await()
    }

    suspend fun getLinkedEntryIds(): Set<String> {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME)
            .whereNotEqualTo("relatedEntryId", null)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.getString("relatedEntryId") }.toSet()
    }

    suspend fun getLinkedAmounts(): Map<String, Double> {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME)
            .whereNotEqualTo("relatedEntryId", null)
            .get()
            .await()
        val bills = snapshot.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
        return bills.groupBy { it.relatedEntryId!! }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

}
