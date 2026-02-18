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

import com.batterysales.data.helper.BalanceManager

class InvoiceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val balanceManager: BalanceManager
) {

    suspend fun createInvoice(invoice: Invoice): Invoice {
        val docRef = firestore.collection(Invoice.COLLECTION_NAME).document()
        val finalInvoice = invoice.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        docRef.set(finalInvoice).await()
        return finalInvoice
    }

    suspend fun getInvoice(invoiceId: String): Invoice? {
        val snapshot = firestore.collection(Invoice.COLLECTION_NAME)
            .document(invoiceId)
            .get()
            .await()
        return snapshot.toObject(Invoice::class.java)?.copy(id = snapshot.id)
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

    suspend fun getTotalDebtForWarehouse(warehouseId: String?): Double {
        var query = firestore.collection(Invoice.COLLECTION_NAME)
            .whereEqualTo("status", "pending")

        if (!warehouseId.isNullOrBlank()) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }

        val aggregateQuery = query.aggregate(AggregateField.sum("remainingAmount"))
        val snapshot = aggregateQuery.get(AggregateSource.SERVER).await()
        return (snapshot.get(AggregateField.sum("remainingAmount")) as? Number)?.toDouble() ?: 0.0
    }

    suspend fun getInvoicesPaginated(
        warehouseId: String?,
        status: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        searchQuery: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<Invoice>, DocumentSnapshot?> {
        var query: Query = firestore.collection(Invoice.COLLECTION_NAME)

        if (!warehouseId.isNullOrBlank()) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }

        if (status != null) {
            query = query.whereEqualTo("status", status)
        }

        val isSearching = !searchQuery.isNullOrBlank()

        if (startDate != null && endDate != null && !isSearching) {
            query = query.whereGreaterThanOrEqualTo("invoiceDate", Date(startDate))
                .whereLessThanOrEqualTo("invoiceDate", Date(endDate + 86400000))
        }

        if (isSearching) {
            // Determine if we should search by invoiceNumber or customerPhone
            val isNumeric = searchQuery.all { it.isDigit() }
            val searchField = if (isNumeric && searchQuery.length >= 3) "customerPhone" else "invoiceNumber"

            // Prefix search
            query = query.whereGreaterThanOrEqualTo(searchField, searchQuery)
                .whereLessThanOrEqualTo(searchField, searchQuery + "\uf8ff")
                .orderBy(searchField, Query.Direction.DESCENDING)
        } else {
            query = query.orderBy("invoiceDate", Query.Direction.DESCENDING)
        }

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.limit(limit).get().await()
        val invoices = snapshot.documents.mapNotNull { it.toObject(Invoice::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(invoices, lastDoc)
    }

    suspend fun deleteInvoice(invoiceId: String) {
        val payments = firestore.collection(Payment.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get()
            .await()

        val stockEntries = firestore.collection(com.batterysales.data.models.StockEntry.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get()
            .await()

        firestore.runTransaction { transaction ->
            // 1. Delete associated payments
            payments.documents.forEach { transaction.delete(it.reference) }

            // 2. Delete stock entries and update balances
            stockEntries.documents.forEach { doc ->
                val entry = doc.toObject(com.batterysales.data.models.StockEntry::class.java)
                if (entry != null) {
                    balanceManager.updateVariantStock(transaction, entry.productVariantId, entry.warehouseId, -(entry.quantity - entry.returnedQuantity))
                    if (entry.status == com.batterysales.data.models.StockEntry.STATUS_APPROVED && entry.supplierId.isNotEmpty()) {
                        balanceManager.updateSupplierBalance(transaction, entry.supplierId, debitDelta = -entry.totalCost, creditDelta = 0.0)
                    }
                }
                transaction.delete(doc.reference)
            }

            // 3. Delete the invoice itself
            transaction.delete(firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId))
        }.await()
    }
}
