package com.batterysales.data.repositories

import com.batterysales.data.models.Invoice
import com.batterysales.data.models.Payment
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

class InvoiceRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun createInvoice(invoice: Invoice): Invoice {
        val docRef = firestore.collection(Invoice.COLLECTION_NAME).document()
        val finalInvoice = invoice.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        docRef.set(finalInvoice).await()
        return finalInvoice
    }

    suspend fun getInvoice(invoiceId: String): Invoice? {
        return firestore.collection(Invoice.COLLECTION_NAME)
            .document(invoiceId)
            .get()
            .await()
            .toObject(Invoice::class.java)
    }

    fun getAllInvoices(): Flow<List<Invoice>> = callbackFlow {
        val listenerRegistration = firestore.collection(Invoice.COLLECTION_NAME)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val invoices = snapshot.documents.mapNotNull { it.toObject(Invoice::class.java)?.copy(id = it.id) }
                    trySend(invoices).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updateInvoice(invoice: Invoice) {
        val updatedInvoice = invoice.copy(updatedAt = Date())
        firestore.collection(Invoice.COLLECTION_NAME).document(invoice.id).set(updatedInvoice).await()
    }

    suspend fun getTotalDebtForWarehouse(warehouseId: String): Double {
        val query = firestore.collection(Invoice.COLLECTION_NAME)
            .whereEqualTo("warehouseId", warehouseId)
            .whereEqualTo("status", "pending")

        val aggregateQuery = query.aggregate(AggregateField.sum("remainingAmount"))
        val snapshot = aggregateQuery.get(AggregateSource.SERVER).await()
        return snapshot.getDouble(AggregateField.sum("remainingAmount")) ?: 0.0
    }

    suspend fun getInvoicesPaginated(
        warehouseId: String,
        status: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<Invoice>, DocumentSnapshot?> {
        var query: Query = firestore.collection(Invoice.COLLECTION_NAME)
            .whereEqualTo("warehouseId", warehouseId)

        if (status != null) {
            query = query.whereEqualTo("status", status)
        }

        if (startDate != null && endDate != null) {
            query = query.whereGreaterThanOrEqualTo("invoiceDate", Date(startDate))
                .whereLessThanOrEqualTo("invoiceDate", Date(endDate + 86400000))
        }

        query = query.orderBy("invoiceDate", Query.Direction.DESCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.limit(limit).get().await()
        val invoices = snapshot.documents.mapNotNull { it.toObject(Invoice::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(invoices, lastDoc)
    }

    suspend fun deleteInvoice(invoiceId: String) {
        // First, delete all payments associated with the invoice
        val payments = firestore.collection(Payment.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get()
            .await()

        val batch = firestore.batch()
        payments.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        // Then, delete the invoice itself
        batch.delete(firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId))

        batch.commit().await()
    }
}
