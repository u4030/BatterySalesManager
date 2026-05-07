package com.batterysales.data.repositories

import com.batterysales.data.models.Payment
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class PaymentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    fun getPaymentsForInvoice(invoiceId: String): Flow<List<Payment>> = callbackFlow {
        val listenerRegistration = firestore.collection(Payment.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val payments = snapshot.documents.mapNotNull { it.toObject(Payment::class.java)?.copy(id = it.id) }
                    trySend(payments).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun getAllPaymentsFlow(): Flow<List<Payment>> = callbackFlow {
        val listenerRegistration = firestore.collection(Payment.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val payments = snapshot.documents.mapNotNull { it.toObject(Payment::class.java)?.copy(id = it.id) }
                    trySend(payments).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addPayment(payment: Payment): String {
        val docRef = firestore.collection(Payment.COLLECTION_NAME).document()
        val finalPayment = payment.copy(id = docRef.id)
        docRef.set(finalPayment).await()
        return docRef.id
    }

    suspend fun updatePayment(payment: Payment) {
        firestore.collection(Payment.COLLECTION_NAME).document(payment.id).set(payment).await()
    }

    suspend fun deletePayment(paymentId: String) {
        firestore.collection(Payment.COLLECTION_NAME).document(paymentId).delete().await()
    }

    suspend fun getTodayCollection(startDate: java.util.Date, warehouseId: String? = null): Double {
        var query: Query = firestore.collection(Payment.COLLECTION_NAME)
            .whereGreaterThanOrEqualTo("timestamp", startDate)
        
        if (warehouseId != null) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }

        val snapshot = query.aggregate(AggregateField.sum("amount"))
            .get(AggregateSource.SERVER)
            .await()
        return snapshot.getDouble(AggregateField.sum("amount")) ?: 0.0
    }

    suspend fun getTodayCollectedInvoicesCount(startDate: java.util.Date, warehouseId: String? = null): Int {
        var query: Query = firestore.collection(Payment.COLLECTION_NAME)
            .whereGreaterThanOrEqualTo("timestamp", startDate)
        
        if (warehouseId != null) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }

        // We fetch the documents to count unique invoiceIds
        val snapshot = query.get().await()
        return snapshot.documents.mapNotNull { it.getString("invoiceId") }.distinct().size
    }

    /**
     * Efficiently fetches daily collection stats using server-side aggregation.
     */
    suspend fun getTodayStats(warehouseId: String, startOfDayTimestamp: Long): Pair<Double, Int> {
        val startDate = java.util.Date(startOfDayTimestamp)
        var query: Query = firestore.collection(Payment.COLLECTION_NAME)
            .whereEqualTo("warehouseId", warehouseId)
            .whereGreaterThanOrEqualTo("timestamp", startDate)

        // Calculate sum
        val sumSnap = query.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await()
        val totalAmount = sumSnap.getDouble(AggregateField.sum("amount")) ?: 0.0

        // Calculate distinct count (Still requires fetching if distinct ID count is needed,
        // but for Dashboard a simple count of unique invoice payment events is often sufficient)
        val countSnap = query.get().await()
        val uniqueInvoices = countSnap.documents.mapNotNull { it.getString("invoiceId") }.distinct().size

        return Pair(totalAmount, uniqueInvoices)
    }
}
