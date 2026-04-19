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
    suspend fun autoLinkBillsForSupplier(supplierId: String) {
        if (supplierId.isEmpty()) return

        // 1. جلب كافة فواتير المشتريات المعتمدة للمورد
        val stockEntries = firestore.collection(com.batterysales.data.models.StockEntry.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
            .whereEqualTo("status", "approved")
            .get()
            .await()
            .documents.mapNotNull { it.toObject(com.batterysales.data.models.StockEntry::class.java)?.copy(id = it.id) }
            .filter { it.quantity > 0 } // مشتريات فقط

        // تجميع الفواتير حسب رقم الفاتورة أو معرف الطلبية
        val orders = stockEntries.groupBy { it.invoiceNumber.ifEmpty { it.orderId.ifEmpty { it.id } } }
            .map { (key, group) ->
                val totalCost = group.sumOf { if (it.totalCost > 0) it.totalCost else it.quantity * it.costPrice }
                val timestamp = group.minOf { it.timestamp }
                key to (totalCost to timestamp)
            }
            .sortedBy { it.second.second } // الترتيب حسب الأقدم

        // 2. جلب كافة الشيكات والكمبيالات للمورد
        val allBills = firestore.collection(Bill.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
            .get()
            .await()
            .documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }

        // فصل الروابط اليدوية لحساب المبالغ المتبقية في الفواتير
        val manualLinks = allBills.filter { it.relatedEntryId != null }
            .groupBy { it.relatedEntryId!! }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // حساب الميزان المتبقي لكل فاتورة (إجمالي التكلفة - الروابط اليدوية)
        val orderBalances = orders.map { (id, data) ->
            val (totalCost, _) = data
            id to (totalCost - (manualLinks[id] ?: 0.0))
        }.filter { it.second > 0.001 }.toMutableList()

        // 3. تحديد الشيكات المتاحة للربط التلقائي (غير مرتبطة يدوياً وغير مسددة كلياً)
        val availableBills = allBills.filter { it.relatedEntryId == null }
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
