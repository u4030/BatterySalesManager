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
    private val oldBatteryRepository: OldBatteryRepository,
    private val summaryRepository: SummaryRepository
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
     * Deprecated broad listener replaced with CACHE-first paginated fetch flow.
     */
    fun getAllInvoices(limit: Long = 20): Flow<List<Invoice>> = callbackFlow {
        val listenerRegistration = firestore.collection(Invoice.COLLECTION_NAME)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val invoices = snapshot.documents.mapNotNull { it.toObject(Invoice::class.java)?.copy(id = it.id) }
                    trySend(invoices)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updateInvoice(invoice: Invoice) {
        val updatedInvoice = invoice.copy(updatedAt = Date())
        firestore.runTransaction { transaction ->
            transaction.set(firestore.collection(Invoice.COLLECTION_NAME).document(invoice.id), updatedInvoice)
        }.await()
    }

    suspend fun getTotalDebtForWarehouse(warehouseId: String?): Double {
        var query = firestore.collection(Invoice.COLLECTION_NAME)
            .whereGreaterThan("remainingAmount", 0.001)

        if (!warehouseId.isNullOrBlank()) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }

        val aggregateQuery = query.aggregate(AggregateField.sum("remainingAmount"))
        val snapshot = aggregateQuery.get(AggregateSource.SERVER).await()
        return (snapshot.get(AggregateField.sum("remainingAmount")) as? Number)?.toDouble() ?: 0.0
    }

    /**
     * Cache-First Paginated Invoice Query (Architecture Mandate: Zero Quota Consumption for search & browsing).
     */
    suspend fun getInvoicesPaginated(
        warehouseId: String?,
        status: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        searchQuery: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20,
        useUpdatedAt: Boolean = false,
        source: com.google.firebase.firestore.Source = com.google.firebase.firestore.Source.CACHE
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

        return try {
            val snapshot = query.limit(limit).get(source).await()
            val invoices = snapshot.documents.mapNotNull { it.toObject(Invoice::class.java)?.copy(id = it.id) }
            val lastDoc = snapshot.documents.lastOrNull()
            Pair(invoices, lastDoc)
        } catch (e: Exception) {
            // Fallback to SERVER if CACHE failed (e.g., initial fresh setup)
            if (source == com.google.firebase.firestore.Source.CACHE) {
                val snapshot = query.limit(limit).get(com.google.firebase.firestore.Source.SERVER).await()
                val invoices = snapshot.documents.mapNotNull { it.toObject(Invoice::class.java)?.copy(id = it.id) }
                val lastDoc = snapshot.documents.lastOrNull()
                Pair(invoices, lastDoc)
            } else {
                Pair(emptyList(), null)
            }
        }
    }

    /**
     * Zero Client-Side Impact Command: Writes a delete request / status command directly to Firestore.
     * The server-side Cloud Function executes background reversals atomically.
     */
    suspend fun deleteInvoice(invoiceId: String) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId)
        val deleteCommand = mapOf(
            "status" to "PENDING_DELETE",
            "deleteRequestedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        invoiceRef.set(deleteCommand, com.google.firebase.firestore.SetOptions.merge()).await()

        // Also write to invoice_deletion_requests collection for strict command tracking
        val requestRef = firestore.collection("invoice_deletion_requests").document(invoiceId)
        requestRef.set(mapOf(
            "invoiceId" to invoiceId,
            "requestedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "status" to "PENDING"
        )).await()
    }

    /**
     * Write-Only Sale Command: Writes purely the central unified Invoice document to invoices/{invoiceId}.
     * Zero client-side pre-fetch reads or side-effect writes to stock_entries, old_battery_transactions, or transactions.
     * Cloud Functions server execution engine handles atomic distribution.
     */
    suspend fun createFullSale(
        invoice: Invoice,
        stockEntry: com.batterysales.data.models.StockEntry? = null,
        payment: com.batterysales.data.models.Payment? = null,
        treasuryTransaction: com.batterysales.data.models.Transaction? = null,
        oldBatteryTransaction: com.batterysales.data.models.OldBatteryTransaction? = null
    ): String {
        val docRef = firestore.collection(Invoice.COLLECTION_NAME).document()
        val finalInvoice = invoice.copy(
            id = docRef.id,
            createdAt = Date(),
            updatedAt = Date()
        )
        docRef.set(finalInvoice).await()
        return finalInvoice.id
    }

    /**
     * Payment Command Layer: Writes a write-only command to payment_commands.
     * Zero Client-Side Pre-Fetch Reads or local transactions.
     */
    suspend fun addPayment(invoiceId: String, payment: Payment) {
        val commandRef = firestore.collection("payment_commands").document()
        val command = mapOf(
            "id" to commandRef.id,
            "type" to "CREATE",
            "invoiceId" to invoiceId,
            "warehouseId" to payment.warehouseId,
            "amount" to payment.amount,
            "paymentMethod" to payment.paymentMethod,
            "notes" to payment.notes,
            "paymentDate" to (payment.paymentDate ?: Date()),
            "status" to "PENDING",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        commandRef.set(command).await()
    }

    /**
     * Payment Command Layer: Updates a payment via write-only command to payment_commands.
     * Zero Client-Side Pre-Fetch Reads or local transactions.
     */
    suspend fun updatePayment(payment: Payment) {
        val commandRef = firestore.collection("payment_commands").document()
        val command = mapOf(
            "id" to commandRef.id,
            "type" to "UPDATE",
            "paymentId" to payment.id,
            "invoiceId" to payment.invoiceId,
            "warehouseId" to payment.warehouseId,
            "amount" to payment.amount,
            "paymentMethod" to payment.paymentMethod,
            "notes" to payment.notes,
            "paymentDate" to (payment.paymentDate ?: Date()),
            "status" to "PENDING",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        commandRef.set(command).await()
    }

    /**
     * Payment Command Layer: Deletes a payment via write-only command to payment_commands.
     * Zero Client-Side Pre-Fetch Reads or local transactions.
     */
    suspend fun deletePayment(paymentId: String, invoiceId: String) {
        val commandRef = firestore.collection("payment_commands").document()
        val command = mapOf(
            "id" to commandRef.id,
            "type" to "DELETE",
            "paymentId" to paymentId,
            "invoiceId" to invoiceId,
            "status" to "PENDING",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        commandRef.set(command).await()
    }

    suspend fun migrateInvoices() {
        val snapshot = firestore.collection(Invoice.COLLECTION_NAME).get().await()
        val allPayments = firestore.collection(Payment.COLLECTION_NAME).get().await()
            .documents.mapNotNull { it.toObject(Payment::class.java) }
        
        val paymentsByInvoice = allPayments.groupBy { it.invoiceId }

        snapshot.documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            var count = 0
            chunk.forEach { doc ->
                val invoice = doc.toObject(Invoice::class.java) ?: return@forEach
                val updates = mutableMapOf<String, Any>()
                
                // 1. Fix Dates
                if (!doc.contains("invoiceDate") || doc.getDate("invoiceDate")?.time == 0L) {
                    updates["invoiceDate"] = doc.getDate("createdAt") ?: Date()
                }

                // 2. Fix Paid/Remaining Amount
                val invoicePayments = paymentsByInvoice[doc.id] ?: emptyList()
                val actualPaid = invoicePayments.sumOf { it.amount }
                val expectedRemaining = (invoice.totalAmount - actualPaid).coerceAtLeast(0.0)

                if (Math.abs(invoice.paidAmount - actualPaid) > 0.001) {
                    updates["paidAmount"] = actualPaid
                }
                if (Math.abs(invoice.remainingAmount - expectedRemaining) > 0.001) {
                    updates["remainingAmount"] = expectedRemaining
                }

                // 3. Fix Status
                val correctStatus = if (expectedRemaining <= 0.001) "paid" else "pending"
                if (invoice.status != correctStatus && invoice.status != "cancelled") {
                    updates["status"] = correctStatus
                }

                if (updates.isNotEmpty()) {
                    batch.update(doc.reference, updates)
                    count++
                }
            }
            if (count > 0) batch.commit().await()
        }
    }
}
 
