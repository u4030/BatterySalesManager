package com.batterysales.data.repositories

import com.batterysales.data.models.Payment
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

class PaymentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun createPayment(payment: Payment): String {
        val docRef = firestore.collection(Payment.COLLECTION_NAME).document()
        val finalPayment = payment.copy(id = docRef.id, timestamp = Date())
        docRef.set(finalPayment).await()
        return docRef.id
    }

    suspend fun getPayment(paymentId: String): Payment? {
        val snapshot = firestore.collection(Payment.COLLECTION_NAME).document(paymentId).get().await()
        return snapshot.toObject(Payment::class.java)?.copy(id = snapshot.id)
    }

    fun getPaymentsForInvoice(invoiceId: String): Flow<List<Payment>> = callbackFlow {
        val listenerRegistration = firestore.collection(Payment.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
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

    suspend fun getTodayStats(warehouseId: String, startOfToday: Long): Pair<Double, Int> {
        val query = firestore.collection(Payment.COLLECTION_NAME)
            .whereEqualTo("warehouseId", warehouseId)
            .whereGreaterThanOrEqualTo("timestamp", Date(startOfToday))

        val snapshot = query.aggregate(
            AggregateField.sum("amount"),
            AggregateField.count()
        ).get(AggregateSource.SERVER).await()

        val amount = snapshot.getDouble(AggregateField.sum("amount")) ?: 0.0
        val count = snapshot.count.toInt()

        return Pair(amount, count)
    }

    suspend fun addPayment(payment: Payment): String {
        val docRef = firestore.collection(Payment.COLLECTION_NAME).document()
        val finalPayment = payment.copy(id = docRef.id, timestamp = Date())
        docRef.set(finalPayment).await()
        return docRef.id
    }

    suspend fun updatePayment(payment: Payment) {
        firestore.collection(Payment.COLLECTION_NAME)
            .document(payment.id)
            .set(payment)
            .await()
    }

    suspend fun deletePayment(paymentId: String) {
        firestore.collection(Payment.COLLECTION_NAME)
            .document(paymentId)
            .delete()
            .await()
    }
}
