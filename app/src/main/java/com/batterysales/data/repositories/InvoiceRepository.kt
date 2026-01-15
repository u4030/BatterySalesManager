package com.batterysales.data.repositories

import com.batterysales.data.models.Invoice
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

class InvoiceRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun createInvoice(invoice: Invoice) {
        val docRef = firestore.collection(Invoice.COLLECTION_NAME).document()
        val finalInvoice = invoice.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        docRef.set(finalInvoice).await()
    }

    suspend fun getInvoice(invoiceId: String): Invoice? {
        return firestore.collection(Invoice.COLLECTION_NAME)
            .document(invoiceId)
            .get()
            .await()
            .toObject(Invoice::class.java)
    }

    suspend fun getAllInvoices(): List<Invoice> {
        return firestore.collection(Invoice.COLLECTION_NAME)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()
            .toObjects(Invoice::class.java)
    }

    suspend fun recordPayment(invoiceId: String, amount: Double) {
        val invoice = getInvoice(invoiceId) ?: throw Exception("Invoice not found")
        val newPaidAmount = invoice.paidAmount + amount
        val newRemainingAmount = (invoice.totalAmount - newPaidAmount).coerceAtLeast(0.0)
        val newStatus = if (newPaidAmount >= invoice.totalAmount) "paid" else "pending"

        val updates = mapOf(
            "paidAmount" to newPaidAmount,
            "remainingAmount" to newRemainingAmount,
            "status" to newStatus,
            "updatedAt" to Date()
        )
        firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId).update(updates).await()
    }
}
