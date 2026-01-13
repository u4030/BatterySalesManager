package com.batterysales.data.repositories

import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

class BillRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getAllBills(): List<Bill> {
        return firestore.collection(Bill.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(Bill::class.java)
    }

    suspend fun addBill(bill: Bill) {
        firestore.collection(Bill.COLLECTION_NAME)
            .add(bill)
            .await()
    }

    suspend fun updateBillStatus(billId: String, status: BillStatus) {
        firestore.collection(Bill.COLLECTION_NAME)
            .document(billId)
            .update("status", status)
            .await()
    }

    suspend fun deleteBill(billId: String) {
        firestore.collection(Bill.COLLECTION_NAME)
            .document(billId)
            .delete()
            .await()
    }
}
