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

            // 2. Delete stock entries
            stockEntries.documents.forEach { doc ->
                val entry = doc.toObject(com.batterysales.data.models.StockEntry::class.java)
                if (entry != null && entry.status == "approved") {
                    updateVariantStock(transaction, entry.productVariantId, entry.warehouseId, -(entry.quantity))
                }
                transaction.delete(doc.reference)
            }

            // 3. Delete the invoice itself
            transaction.delete(firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId))
        }.await()
    }

    private fun updateVariantStock(transaction: com.google.firebase.firestore.Transaction, variantId: String, warehouseId: String, quantityChange: Int) {
        val variantRef = firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(variantId)
        val variantSnap = transaction.get(variantRef)
        val variant = variantSnap.toObject(com.batterysales.data.models.ProductVariant::class.java)
        if (variant != null) {
            val newStockMap = (variant.currentStock ?: emptyMap()).toMutableMap()
            val currentQty = newStockMap[warehouseId] ?: 0
            newStockMap[warehouseId] = currentQty + quantityChange
            transaction.update(variantRef, "currentStock", newStockMap)
        }
    }

    suspend fun createFullSale(
        invoice: Invoice,
        stockEntry: com.batterysales.data.models.StockEntry,
        payment: com.batterysales.data.models.Payment?,
        treasuryTransaction: com.batterysales.data.models.Transaction?,
        oldBatteryTransaction: com.batterysales.data.models.OldBatteryTransaction?
    ): String {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document()
        val finalInvoice = invoice.copy(id = invoiceRef.id, createdAt = Date(), updatedAt = Date())

        firestore.runTransaction { transaction ->
            // 1. Create Invoice
            transaction.set(invoiceRef, finalInvoice)

            // 2. Create Stock Entry
            val stockRef = firestore.collection(com.batterysales.data.models.StockEntry.COLLECTION_NAME).document()
            transaction.set(stockRef, stockEntry.copy(id = stockRef.id, invoiceId = finalInvoice.id))

            // 2.1 Update denormalized stock in ProductVariant
            updateVariantStock(transaction, stockEntry.productVariantId, stockEntry.warehouseId, stockEntry.quantity)

            // 3. Create Payment (if any)
            if (payment != null) {
                val paymentRef = firestore.collection(com.batterysales.data.models.Payment.COLLECTION_NAME).document()
                transaction.set(paymentRef, payment.copy(id = paymentRef.id, invoiceId = finalInvoice.id))
            }

            // 4. Create Treasury Transaction (if any)
            if (treasuryTransaction != null) {
                val treasuryRef = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME).document()
                transaction.set(treasuryRef, treasuryTransaction.copy(id = treasuryRef.id, relatedId = finalInvoice.id))
            }

            // 5. Create Old Battery Transaction (if any)
            if (oldBatteryTransaction != null) {
                val scrapRef = firestore.collection(com.batterysales.data.models.OldBatteryTransaction.COLLECTION_NAME).document()
                transaction.set(scrapRef, oldBatteryTransaction.copy(id = scrapRef.id, invoiceId = finalInvoice.id))
            }
        }.await()

        return finalInvoice.id
    }
}
