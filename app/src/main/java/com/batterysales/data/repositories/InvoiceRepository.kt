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
    private val firestore: FirebaseFirestore,
    private val oldBatteryRepository: OldBatteryRepository
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

    /**
     * Warning: Dangerous broad listener. Use getInvoicesPaginated instead.
     */
    fun getAllInvoices(limit: Long = 1000): Flow<List<Invoice>> = callbackFlow {
        val listenerRegistration = firestore.collection(Invoice.COLLECTION_NAME)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
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
        limit: Long = 20,
        useUpdatedAt: Boolean = false
    ): Pair<List<Invoice>, DocumentSnapshot?> {
        var query: Query = firestore.collection(Invoice.COLLECTION_NAME)

        if (!warehouseId.isNullOrBlank()) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }

        if (status != null) {
            query = query.whereEqualTo("status", status)
        }

        val isSearching = !searchQuery.isNullOrBlank()

        val dateField = if (useUpdatedAt) "updatedAt" else "invoiceDate"

        if (startDate != null && endDate != null && !isSearching) {
            val start = Date(com.batterysales.utils.DateUtils.getStartOfDay(startDate))
            val end = Date(com.batterysales.utils.DateUtils.getEndOfDay(endDate))

            query = query.whereGreaterThanOrEqualTo(dateField, start)
                .whereLessThanOrEqualTo(dateField, end)
        }

        if (isSearching) {
            // Determine if we should search by invoiceNumber, customerPhone or customerName
            val isNumeric = searchQuery.all { it.isDigit() }
            val searchField = when {
                isNumeric && searchQuery.length >= 3 -> "customerPhone"
                searchQuery.any { it in '\u0600'..'\u06FF' } -> "customerName"
                else -> "invoiceNumber"
            }

            // Prefix search
            query = query.whereGreaterThanOrEqualTo(searchField, searchQuery)
                .whereLessThanOrEqualTo(searchField, searchQuery + "\uf8ff")
                .orderBy(searchField, Query.Direction.DESCENDING)
        } else {
            query = query.orderBy(dateField, Query.Direction.DESCENDING)
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

        val entries = stockEntries.documents.mapNotNull { it.toObject(com.batterysales.data.models.StockEntry::class.java)?.copy(id = it.id) }
        val approvedEntries = entries.filter { it.status == "approved" }
        val variantIds = approvedEntries.map { it.productVariantId }.distinct()

        firestore.runTransaction { transaction ->
            // 1. All Reads
            val variantSnapshots = variantIds.associateWith { vid ->
                transaction.get(firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(vid))
            }
            
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId)
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java)

            // 2. All Writes
            // 2.1 Update variants and system stats
            var totalValueToReverse = 0.0
            var totalQtyToReverse = 0
            
            approvedEntries.forEach { entry ->
                val variant = variantSnapshots[entry.productVariantId]?.toObject(com.batterysales.data.models.ProductVariant::class.java)
                if (variant != null && variant.currentStock != null) {
                    val stockMap = variant.currentStock.toMutableMap()
                    stockMap[entry.warehouseId] = (stockMap[entry.warehouseId] ?: 0) - (entry.quantity)
                    transaction.update(variantSnapshots[entry.productVariantId]!!.reference, "currentStock", stockMap)
                    
                    totalValueToReverse += (entry.quantity * variant.weightedAverageCost)
                    totalQtyToReverse += entry.quantity
                }
            }

            if (invoice != null) {
                transaction.update(statsRef, mapOf(
                    "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(-totalQtyToReverse.toLong()),
                    "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(-totalValueToReverse),
                    "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(-invoice.remainingAmount)
                ))
            }

            // 2.2 Delete associated payments
            payments.documents.forEach { transaction.delete(it.reference) }

            // 2.3 Delete stock entries
            stockEntries.documents.forEach { transaction.delete(it.reference) }

            // 2.4 Delete the invoice itself
            transaction.delete(invoiceRef)
        }.await()
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
            // 1. All Reads
            val variantRef = firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(stockEntry.productVariantId)
            val variant = transaction.get(variantRef).toObject(com.batterysales.data.models.ProductVariant::class.java)

            // 2. All Writes
            transaction.set(invoiceRef, finalInvoice)

            val stockRef = firestore.collection(com.batterysales.data.models.StockEntry.COLLECTION_NAME).document()
            val finalStockEntry = stockEntry.copy(id = stockRef.id, invoiceId = finalInvoice.id)
            transaction.set(stockRef, finalStockEntry)

            if (variant != null && variant.currentStock != null) {
                val newStockMap = variant.currentStock.toMutableMap()
                val currentQty = newStockMap[finalStockEntry.warehouseId] ?: 0
                newStockMap[finalStockEntry.warehouseId] = currentQty + (finalStockEntry.quantity - finalStockEntry.returnedQuantity)
                transaction.update(variantRef, "currentStock", newStockMap)
            }

            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            val qty = stockEntry.quantity - stockEntry.returnedQuantity
            val valueChange = qty * (variant?.weightedAverageCost ?: 0.0)
            val customerDebtChange = invoice.remainingAmount

            transaction.update(statsRef, mapOf(
                "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(qty.toLong()),
                "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(valueChange),
                "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(customerDebtChange),
                "updatedAt" to java.util.Date()
            ))

            if (payment != null) {
                val paymentRef = firestore.collection(com.batterysales.data.models.Payment.COLLECTION_NAME).document()
                transaction.set(paymentRef, payment.copy(id = paymentRef.id, invoiceId = finalInvoice.id))
            }

            if (treasuryTransaction != null) {
                val treasuryRef = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME).document()
                transaction.set(treasuryRef, treasuryTransaction.copy(id = treasuryRef.id, relatedId = finalInvoice.id))
            }

            if (oldBatteryTransaction != null) {
                val scrapRef = firestore.collection(com.batterysales.data.models.OldBatteryTransaction.COLLECTION_NAME).document()
                transaction.set(scrapRef, oldBatteryTransaction.copy(id = scrapRef.id, invoiceId = finalInvoice.id))
            }
        }.await()

        if (oldBatteryTransaction != null) {
            oldBatteryRepository.syncScrapWarehouse(oldBatteryTransaction.warehouseId)
        }

        return finalInvoice.id
    }

    suspend fun addPayment(invoiceId: String, payment: Payment) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId)
        firestore.runTransaction { transaction ->
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java) ?: return@runTransaction
            
            val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document()
            transaction.set(paymentRef, payment.copy(id = paymentRef.id, invoiceId = invoiceId))
            
            val newTotalPaid = invoice.paidAmount + payment.amount
            val newRemaining = invoice.totalAmount - newTotalPaid
            val newStatus = if (newRemaining <= 0.001) "paid" else "pending"
            
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to newStatus,
                "updatedAt" to Date()
            ))

            // Update System Stats
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            transaction.update(statsRef, "totalCustomerDebt", com.google.firebase.firestore.FieldValue.increment(-payment.amount))
        }.await()
    }

    suspend fun updatePayment(payment: Payment) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(payment.invoiceId)
        firestore.runTransaction { transaction ->
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java) ?: return@runTransaction
            
            val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document(payment.id)
            val oldPayment = transaction.get(paymentRef).toObject(Payment::class.java) ?: return@runTransaction
            
            val diff = payment.amount - oldPayment.amount
            transaction.set(paymentRef, payment)
            
            val newTotalPaid = invoice.paidAmount + diff
            val newRemaining = invoice.totalAmount - newTotalPaid
            val newStatus = if (newRemaining <= 0.001) "paid" else "pending"
            
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to newStatus,
                "updatedAt" to Date()
            ))

            // Update System Stats
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            transaction.update(statsRef, "totalCustomerDebt", com.google.firebase.firestore.FieldValue.increment(-diff))
        }.await()
    }

    suspend fun deletePayment(paymentId: String, invoiceId: String) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId)
        firestore.runTransaction { transaction ->
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java) ?: return@runTransaction
            
            val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document(paymentId)
            val oldPayment = transaction.get(paymentRef).toObject(Payment::class.java) ?: return@runTransaction
            
            transaction.delete(paymentRef)
            
            val newTotalPaid = invoice.paidAmount - oldPayment.amount
            val newRemaining = invoice.totalAmount - newTotalPaid
            val newStatus = if (newRemaining <= 0.001) "paid" else "pending"
            
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to newStatus,
                "updatedAt" to Date()
            ))

            // Update System Stats
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            transaction.update(statsRef, "totalCustomerDebt", com.google.firebase.firestore.FieldValue.increment(oldPayment.amount))
        }.await()
    }
}
