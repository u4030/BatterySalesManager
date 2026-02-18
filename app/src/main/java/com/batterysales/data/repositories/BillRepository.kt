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

import com.batterysales.data.helper.BalanceManager

class BillRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val balanceManager: BalanceManager
) {

    suspend fun getAllBills(): List<Bill> {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
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

    suspend fun addBill(bill: Bill) {
        firestore.runTransaction { transaction ->
            val docRef = firestore.collection(Bill.COLLECTION_NAME).document()
            val finalBill = bill.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
            transaction.set(docRef, finalBill)

            if (finalBill.paidAmount != 0.0 && finalBill.supplierId.isNotEmpty()) {
                balanceManager.updateSupplierBalance(transaction, finalBill.supplierId, debitDelta = 0.0, creditDelta = finalBill.paidAmount)
            }
        }.await()
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
            val bill = snapshot.toObject(Bill::class.java) ?: return@runTransaction

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

            if (bill.supplierId.isNotEmpty()) {
                balanceManager.updateSupplierBalance(transaction, bill.supplierId, debitDelta = 0.0, creditDelta = paymentAmount)
            }
        }.await()
    }

    suspend fun deleteBill(billId: String) {
        firestore.runTransaction { transaction ->
            val billRef = firestore.collection(Bill.COLLECTION_NAME).document(billId)
            val bill = transaction.get(billRef).toObject(Bill::class.java) ?: return@runTransaction

            if (bill.paidAmount != 0.0 && bill.supplierId.isNotEmpty()) {
                balanceManager.updateSupplierBalance(transaction, bill.supplierId, debitDelta = 0.0, creditDelta = -bill.paidAmount)
            }

            transaction.delete(billRef)
        }.await()
    }

    suspend fun getBillsPaginated(
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<Bill>, DocumentSnapshot?> {
        var query = firestore.collection(Bill.COLLECTION_NAME)
            .orderBy("dueDate", Query.Direction.ASCENDING)

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
        startDate?.let { query = query.whereGreaterThanOrEqualTo("dueDate", java.util.Date(it)) }
        endDate?.let { query = query.whereLessThanOrEqualTo("dueDate", java.util.Date(it + 86400000)) }

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
        val bills = snapshot.documents.mapNotNull { it.toObject(Bill::class.java) }
        return bills.groupBy { it.relatedEntryId!! }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

}
