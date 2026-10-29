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

    suspend fun createFullSale(
        invoice: Invoice,
        stockEntry: com.batterysales.data.models.StockEntry,
        payment: com.batterysales.data.models.Payment?,
        treasuryTransaction: com.batterysales.data.models.Transaction?,
        oldBatteryTransaction: com.batterysales.data.models.OldBatteryTransaction?
    ): String {
        val batch = firestore.batch()
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document()
        val finalInvoice = invoice.copy(id = invoiceRef.id, createdAt = Date(), updatedAt = Date())

        // Save Invoice document with items array embedded inside the invoice document itself
        batch.set(invoiceRef, finalInvoice)

        val stockRef = firestore.collection(com.batterysales.data.models.StockEntry.COLLECTION_NAME).document()
        val finalStockEntry = stockEntry.copy(id = stockRef.id, invoiceId = finalInvoice.id)
        batch.set(stockRef, finalStockEntry)

        if (payment != null && payment.amount > 0.001) {
            val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document()
            val finalPayment = payment.copy(id = paymentRef.id, invoiceId = finalInvoice.id, warehouseId = stockEntry.warehouseId)
            batch.set(paymentRef, finalPayment)

            if (treasuryTransaction != null) {
                val treasuryRef = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME).document()
                batch.set(treasuryRef, treasuryTransaction.copy(id = treasuryRef.id, relatedId = finalPayment.id, warehouseId = stockEntry.warehouseId))
            }
        }

        if (oldBatteryTransaction != null && oldBatteryTransaction.quantity > 0) {
            val scrapRef = firestore.collection(com.batterysales.data.models.OldBatteryTransaction.COLLECTION_NAME).document()
            val finalScrapTrans = oldBatteryTransaction.copy(id = scrapRef.id, invoiceId = finalInvoice.id, warehouseId = stockEntry.warehouseId)
            batch.set(scrapRef, finalScrapTrans)
        }

        // Commit WriteBatch without phone-side stock manipulation or read loops
        batch.commit().await()
        return finalInvoice.id
    }

    suspend fun addPayment(invoiceId: String, payment: Payment) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId)
        
        // Fetch existing payments for this invoice to verify if invoice was already collected today
        val existingPaymentsSnap = firestore.collection(Payment.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get().await()
            
        val now = Date()
        val pDate = payment.paymentDate ?: now
        val isPaymentToday = com.batterysales.utils.DateUtils.isSameDay(pDate, now)
        val hasPriorPaymentToday = isPaymentToday && existingPaymentsSnap.documents.any { doc ->
            val date = doc.getDate("paymentDate") ?: doc.getDate("timestamp")
            date != null && com.batterysales.utils.DateUtils.isSameDay(date, now)
        }

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java)?.copy(id = invoiceSnap.id) ?: return@runTransaction
            val targetWhId = invoice.warehouseId.ifBlank { "unassigned" }
            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, listOf(targetWhId))
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // --- WRITE PHASE ---
            val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document()
            val finalPayment = payment.copy(id = paymentRef.id, invoiceId = invoiceId, warehouseId = targetWhId)
            transaction.set(paymentRef, finalPayment)

            // Create Ledger Entry
            if (finalPayment.paymentMethod == "bank") {
                val bankRef = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME).document()
                transaction.set(bankRef, com.batterysales.data.models.BankTransaction(
                    id = bankRef.id,
                    billId = finalPayment.id,
                    amount = finalPayment.amount,
                    type = com.batterysales.data.models.BankTransactionType.DEPOSIT,
                    description = "دفعة من زبون: ${invoice.customerName} - فاتورة #${invoice.invoiceNumber}",
                    date = finalPayment.paymentDate,
                    notes = finalPayment.notes,
                    isSystemManaged = true
                ))
            } else {
                val treasuryRef = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME).document()
                transaction.set(treasuryRef, com.batterysales.data.models.Transaction(
                    id = treasuryRef.id,
                    type = com.batterysales.data.models.TransactionType.PAYMENT,
                    amount = finalPayment.amount,
                    description = "دفعة من زبون: ${invoice.customerName} - فاتورة #${invoice.invoiceNumber}",
                    relatedId = finalPayment.id,
                    warehouseId = targetWhId,
                    paymentMethod = finalPayment.paymentMethod,
                    createdAt = finalPayment.paymentDate,
                    isSystemManaged = true
                ))
            }
            
            val newTotalPaid = invoice.paidAmount + finalPayment.amount
            val newRemaining = invoice.totalAmount - newTotalPaid
            val newStatus = if (newRemaining <= 0.001) "paid" else "pending"
            
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to newStatus,
                "updatedAt" to Date()
            ))

            // Only increment unique invoice count if this invoice had NO prior payments today
            val countChange = if (isPaymentToday && !hasPriorPaymentToday) 1 else 0

            summaryRepository.applyFinancialUpdate(
                transaction = transaction,
                snapshots = summarySnapshots,
                warehouseId = targetWhId,
                cashChange = if (finalPayment.paymentMethod != "bank") finalPayment.amount else 0.0,
                bankChange = if (finalPayment.paymentMethod == "bank") finalPayment.amount else 0.0,
                pendingCollectionChange = -finalPayment.amount,
                todayCollectionChange = if (isPaymentToday) finalPayment.amount else 0.0,
                todayCollectionCountChange = countChange
            )

            val statsUpdates = mutableMapOf<String, Any>(
                "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(-finalPayment.amount)
            )

            if (finalPayment.paymentMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(finalPayment.amount)
            } else {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(finalPayment.amount)
            }

            transaction.set(statsRef, statsUpdates, com.google.firebase.firestore.SetOptions.merge())
        }.await()
    }

    suspend fun updatePayment(payment: Payment) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(payment.invoiceId)
        val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document(payment.id)

        // Find linked ledger entries
        val treasuryTransactions = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", payment.id).get().await().documents
        val bankTransactions = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME)
            .whereEqualTo("billId", payment.id).get().await().documents

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java)?.copy(id = invoiceSnap.id) ?: return@runTransaction
            val pSnap = transaction.get(paymentRef)
            val oldPayment = pSnap.toObject(Payment::class.java)?.copy(id = pSnap.id) ?: return@runTransaction
            
            val targetWhId = invoice.warehouseId.ifBlank { "unassigned" }
            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, listOf(targetWhId))
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // --- WRITE PHASE ---
            val finalPayment = payment.copy(warehouseId = targetWhId)
            transaction.set(paymentRef, finalPayment)

            val diff = finalPayment.amount - oldPayment.amount
            
            // Re-calculate daily stats delta
            val now = Date()
            val wasToday = com.batterysales.utils.DateUtils.isSameDay(oldPayment.paymentDate, now)
            val isToday = com.batterysales.utils.DateUtils.isSameDay(finalPayment.paymentDate, now)
            
            var todayAmtDiff = 0.0
            var todayCountDiff = 0
            
            if (wasToday && isToday) {
                todayAmtDiff = diff
            } else if (!wasToday && isToday) {
                todayAmtDiff = finalPayment.amount
                todayCountDiff = 1
            } else if (wasToday && !isToday) {
                todayAmtDiff = -oldPayment.amount
                todayCountDiff = -1
            }

            // Sync with Ledger (Accounting)
            treasuryTransactions.forEach { doc ->
                transaction.update(doc.reference, mapOf(
                    "amount" to finalPayment.amount,
                    "createdAt" to finalPayment.paymentDate,
                    "paymentMethod" to finalPayment.paymentMethod
                ))
            }
            bankTransactions.forEach { doc ->
                transaction.update(doc.reference, mapOf(
                    "amount" to finalPayment.amount,
                    "date" to finalPayment.paymentDate
                ))
            }
            
            val newTotalPaid = invoice.paidAmount + diff
            val newRemaining = invoice.totalAmount - newTotalPaid
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to (if (newRemaining <= 0.001) "paid" else "pending"),
                "updatedAt" to Date()
            ))

            summaryRepository.applyFinancialUpdate(
                transaction = transaction,
                snapshots = summarySnapshots,
                warehouseId = targetWhId,
                cashChange = if (finalPayment.paymentMethod != "bank") diff else 0.0,
                bankChange = if (finalPayment.paymentMethod == "bank") diff else 0.0,
                pendingCollectionChange = -diff,
                todayCollectionChange = todayAmtDiff,
                todayCollectionCountChange = todayCountDiff
            )

            val statsUpdates = mutableMapOf<String, Any>(
                "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(-diff)
            )
            if (finalPayment.paymentMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(diff)
            } else {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(diff)
            }
            transaction.set(statsRef, statsUpdates, com.google.firebase.firestore.SetOptions.merge())
        }.await()
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

    suspend fun deletePayment(paymentId: String, invoiceId: String) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId)
        val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document(paymentId)

        // Find linked ledger entries and remaining payments for this invoice
        val treasuryTransactions = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", paymentId).get().await().documents
        val bankTransactions = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME)
            .whereEqualTo("billId", paymentId).get().await().documents
        val allInvoicePaymentsSnap = firestore.collection(Payment.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId).get().await()

        val now = Date()
        val otherPaymentsToday = allInvoicePaymentsSnap.documents
            .filter { it.id != paymentId }
            .any { doc ->
                val date = doc.getDate("paymentDate") ?: doc.getDate("timestamp")
                date != null && com.batterysales.utils.DateUtils.isSameDay(date, now)
            }

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java)?.copy(id = invoiceSnap.id) ?: return@runTransaction
            val pSnap = transaction.get(paymentRef)
            val oldPayment = pSnap.toObject(Payment::class.java)?.copy(id = pSnap.id) ?: return@runTransaction
            
            val targetWhId = invoice.warehouseId.ifBlank { "unassigned" }
            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, listOf(targetWhId))
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // --- WRITE PHASE ---
            transaction.delete(paymentRef)
            treasuryTransactions.forEach { transaction.delete(it.reference) }
            bankTransactions.forEach { transaction.delete(it.reference) }
            
            val newTotalPaid = invoice.paidAmount - oldPayment.amount
            val newRemaining = invoice.totalAmount - newTotalPaid
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to (if (newRemaining <= 0.001) "paid" else "pending"),
                "updatedAt" to Date()
            ))

            // Only reverse from today if it was paid today
            val wasToday = com.batterysales.utils.DateUtils.isSameDay(oldPayment.paymentDate, now)
            val countChange = if (wasToday && !otherPaymentsToday) -1 else 0

            summaryRepository.applyFinancialUpdate(
                transaction = transaction,
                snapshots = summarySnapshots,
                warehouseId = targetWhId,
                cashChange = if (oldPayment.paymentMethod != "bank") -oldPayment.amount else 0.0,
                bankChange = if (oldPayment.paymentMethod == "bank") -oldPayment.amount else 0.0,
                pendingCollectionChange = oldPayment.amount,
                todayCollectionChange = if (wasToday) -oldPayment.amount else 0.0,
                todayCollectionCountChange = countChange
            )

            val statsUpdates = mutableMapOf<String, Any>(
                "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(oldPayment.amount)
            )
            if (oldPayment.paymentMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(-oldPayment.amount)
            } else {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(-oldPayment.amount)
            }
            transaction.set(statsRef, statsUpdates, com.google.firebase.firestore.SetOptions.merge())
        }.await()
    }
}
 
