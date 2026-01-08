package com.batterysales.data.repository

import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : BaseRepository() {

    suspend fun addBill(bill: Bill): Result<String> = safeCall {
        val docRef = firestore.collection(Bill.COLLECTION_NAME).document()
        val finalBill = bill.copy(id = docRef.id)
        docRef.set(finalBill).await()
        finalBill.id
    }

    suspend fun getAllBills(): Result<List<Bill>> = safeCall {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME)
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .get()
            .await()
        snapshot.toObjectList(Bill::class.java)
    }

    suspend fun updateBillStatus(billId: String, status: BillStatus): Result<Unit> = safeCall {
        val updates = mutableMapOf<String, Any>("status" to status, "updatedAt" to Date())
        if (status == BillStatus.PAID) {
            updates["paidDate"] = Date()
        }
        firestore.collection(Bill.COLLECTION_NAME).document(billId).update(updates).await()
    }

    suspend fun deleteBill(billId: String): Result<Unit> = safeCall {
        firestore.collection(Bill.COLLECTION_NAME).document(billId).delete().await()
    }
}
