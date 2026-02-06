package com.batterysales.data.repositories

import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.google.firebase.firestore.FirebaseFirestore
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
        val docRef = firestore.collection(Bill.COLLECTION_NAME).document()
        val finalBill = bill.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        docRef.set(finalBill).await()
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
        }.await()
    }

    suspend fun deleteBill(billId: String) {
        firestore.collection(Bill.COLLECTION_NAME)
            .document(billId)
            .delete()
            .await()
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
}
