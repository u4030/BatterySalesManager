package com.batterysales.data.repositories

import com.batterysales.data.models.Payment
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
                    val payments = snapshot.toObjects(Payment::class.java)
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
}
