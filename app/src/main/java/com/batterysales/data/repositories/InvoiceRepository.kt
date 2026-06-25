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
        val warehouseIds = approvedEntries.map { it.warehouseId }.distinct()

        firestore.runTransaction { transaction ->
            // 1. All Reads
            val variantSnapshots = variantIds.associateWith { vid ->
                transaction.get(firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(vid))
            }
            
            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, warehouseIds)

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
                    
                    // Update Summary
                    summaryRepository.applyInventoryUpdate(
                        transaction = transaction,
                        snapshots = summarySnapshots,
                        warehouseId = entry.warehouseId,
                        variantId = entry.productVariantId,
                        variant = variant,
                        qtyChange = -entry.quantity
                    )

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
            // 1. All Reads First
            val variantRef = firestore.collection(com.batterysales.data.models.ProductVariant.COLLECTION_NAME).document(stockEntry.productVariantId)
            val vSnap = transaction.get(variantRef)
            val variant = vSnap.toObject(com.batterysales.data.models.ProductVariant::class.java)?.copy(id = vSnap.id)
            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, stockEntry.warehouseId)

            // 2. All Writes
            transaction.set(invoiceRef, finalInvoice)

            val stockRef = firestore.collection(com.batterysales.data.models.StockEntry.COLLECTION_NAME).document()
            val finalStockEntry = stockEntry.copy(id = stockRef.id, invoiceId = finalInvoice.id)
            transaction.set(stockRef, finalStockEntry)

            if (variant != null && variant.currentStock != null) {
                val newStockMap = variant.currentStock.toMutableMap()
                val currentQty = newStockMap[finalStockEntry.warehouseId] ?: 0
                val netQtyChange = finalStockEntry.quantity - finalStockEntry.returnedQuantity
                newStockMap[finalStockEntry.warehouseId] = currentQty + netQtyChange
                transaction.update(variantRef, "currentStock", newStockMap)

                // --- Update Summaries ---
                summaryRepository.applyInventoryUpdate(
                    transaction = transaction,
                    snapshots = summarySnapshots,
                    warehouseId = finalStockEntry.warehouseId,
                    variantId = finalStockEntry.productVariantId,
                    variant = variant,
                    qtyChange = netQtyChange,
                    costChange = netQtyChange * variant.weightedAverageCost
                )

                // --- Low Stock Check (Event-Driven) ---
                val threshold = variant.minQuantities[finalStockEntry.warehouseId] ?: variant.minQuantity
                if (threshold > 0 && (currentQty + netQtyChange) <= threshold) {
                    val alertRef = firestore.collection(com.batterysales.data.models.SystemAlert.COLLECTION_NAME).document("low_stock_${variant.id}_${finalStockEntry.warehouseId}")
                    transaction.set(alertRef, com.batterysales.data.models.SystemAlert(
                        id = alertRef.id,
                        type = com.batterysales.data.models.SystemAlert.TYPE_LOW_STOCK,
                        title = "مخزون منخفض: ${variant.productName ?: ""}",
                        message = "${variant.capacity}A | الكمية الحالية: ${currentQty + netQtyChange} (الحد: $threshold)",
                        relatedId = variant.id,
                        warehouseId = finalStockEntry.warehouseId,
                        timestamp = Date(),
                        data = mapOf("capacity" to variant.capacity, "currentStock" to (currentQty + netQtyChange), "threshold" to threshold)
                    ))
                }
            }

            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            val qty = stockEntry.quantity - stockEntry.returnedQuantity
            val valueChange = qty * (variant?.weightedAverageCost ?: 0.0)
            val customerDebtChange = invoice.remainingAmount

            val statsUpdates = mutableMapOf<String, Any>(
                "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(qty.toLong()),
                "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(valueChange),
                "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(customerDebtChange),
                "updatedAt" to java.util.Date()
            )

            if (payment != null && payment.paymentMethod == "cash") {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(payment.amount)
            } else if (payment != null && payment.paymentMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(payment.amount)
            }

            transaction.update(statsRef, statsUpdates)

            if (payment != null) {
                val paymentRef = firestore.collection(com.batterysales.data.models.Payment.COLLECTION_NAME).document()
                transaction.set(paymentRef, payment.copy(id = paymentRef.id, invoiceId = finalInvoice.id))
                
                // Update Financial Summary
                summaryRepository.applyFinancialUpdate(
                    transaction = transaction,
                    snapshots = summarySnapshots,
                    warehouseId = stockEntry.warehouseId,
                    cashChange = if (payment.paymentMethod == "cash") payment.amount else 0.0,
                    bankChange = if (payment.paymentMethod == "bank") payment.amount else 0.0,
                    pendingCollectionChange = finalInvoice.remainingAmount
                )
            } else {
                // Just update pending collection
                summaryRepository.applyFinancialUpdate(
                    transaction = transaction,
                    snapshots = summarySnapshots,
                    warehouseId = stockEntry.warehouseId,
                    pendingCollectionChange = finalInvoice.remainingAmount
                )
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
            // 1. Reads
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java)?.copy(id = invoiceSnap.id) ?: return@runTransaction
            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, invoice.warehouseId)
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // 2. Writes
            val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document()
            val finalPayment = payment.copy(id = paymentRef.id, invoiceId = invoiceId)
            transaction.set(paymentRef, finalPayment)

            // Create Ledger Entry (Treasury or Bank)
            if (finalPayment.paymentMethod == "bank") {
                val bankRef = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME).document()
                transaction.set(bankRef, com.batterysales.data.models.BankTransaction(
                    id = bankRef.id,
                    amount = finalPayment.amount,
                    type = com.batterysales.data.models.BankTransactionType.DEPOSIT,
                    description = "دفعة من زبون: ${invoice.customerName} - فاتورة #${invoice.invoiceNumber}",
                    date = finalPayment.paymentDate,
                    notes = finalPayment.notes,
                    isSystemManaged = true
                    // Note: We don't have a specific paymentId field in BankTransaction yet, 
                    // but we can use description or reference for now, or add it later.
                ))
            } else {
                val treasuryRef = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME).document()
                transaction.set(treasuryRef, com.batterysales.data.models.Transaction(
                    id = treasuryRef.id,
                    type = com.batterysales.data.models.TransactionType.PAYMENT,
                    amount = finalPayment.amount,
                    description = "دفعة من زبون: ${invoice.customerName} - فاتورة #${invoice.invoiceNumber}",
                    relatedId = finalPayment.id, // Use payment ID for direct linking
                    warehouseId = invoice.warehouseId,
                    paymentMethod = finalPayment.paymentMethod,
                    createdAt = finalPayment.paymentDate,
                    isSystemManaged = true
                ))
            }
            
            val newTotalPaid = invoice.paidAmount + payment.amount
            val newRemaining = invoice.totalAmount - newTotalPaid
            val newStatus = if (newRemaining <= 0.001) "paid" else "pending"
            
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to newStatus,
                "updatedAt" to Date()
            ))

            // Update Financial Summary
            summaryRepository.applyFinancialUpdate(
                transaction = transaction,
                snapshots = summarySnapshots,
                warehouseId = invoice.warehouseId,
                cashChange = if (payment.paymentMethod == "cash") payment.amount else 0.0,
                bankChange = if (payment.paymentMethod == "bank") payment.amount else 0.0,
                pendingCollectionChange = -payment.amount
            )

            // Update System Stats
            val statsUpdates = mutableMapOf<String, Any>(
                "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(-payment.amount)
            )

            if (payment.paymentMethod == "cash") {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(payment.amount)
            } else if (payment.paymentMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(payment.amount)
            }

            transaction.update(statsRef, statsUpdates)
        }.await()
    }

    suspend fun updatePayment(payment: Payment) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(payment.invoiceId)
        
        // Find linked ledger entries
        val treasuryTransactions = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", payment.id).get().await()

        firestore.runTransaction { transaction ->
            // 1. Reads
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java)?.copy(id = invoiceSnap.id) ?: return@runTransaction
            val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document(payment.id)
            val pSnap = transaction.get(paymentRef)
            val oldPayment = pSnap.toObject(Payment::class.java)?.copy(id = pSnap.id) ?: return@runTransaction
            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, invoice.warehouseId)
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // 2. Writes
            val diff = payment.amount - oldPayment.amount
            transaction.set(paymentRef, payment)

            // Update linked ledger entries
            treasuryTransactions.documents.forEach { doc ->
                transaction.update(doc.reference, mapOf(
                    "amount" to payment.amount,
                    "createdAt" to payment.paymentDate,
                    "description" to "دفعة من زبون: ${invoice.customerName} - فاتورة #${invoice.invoiceNumber}"
                ))
            }
            
            val newTotalPaid = invoice.paidAmount + diff
            val newRemaining = invoice.totalAmount - newTotalPaid
            val newStatus = if (newRemaining <= 0.001) "paid" else "pending"
            
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to newStatus,
                "updatedAt" to Date()
            ))

            // Update Financial Summary
            summaryRepository.applyFinancialUpdate(
                transaction = transaction,
                snapshots = summarySnapshots,
                warehouseId = invoice.warehouseId,
                cashChange = if (payment.paymentMethod == "cash") diff else 0.0,
                bankChange = if (payment.paymentMethod == "bank") diff else 0.0,
                pendingCollectionChange = -diff
            )

            // Update System Stats
            val statsUpdates = mutableMapOf<String, Any>(
                "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(-diff)
            )

            if (payment.paymentMethod == "cash") {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(diff)
            } else if (payment.paymentMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(diff)
            }

            transaction.update(statsRef, statsUpdates)
        }.await()
    }

    suspend fun migrateInvoices() {
        val snapshot = firestore.collection(Invoice.COLLECTION_NAME).get().await()
        val docsToUpdate = snapshot.documents.filter { doc ->
            // Migration check: invoiceDate was previously initialized to Date(0) in the model, 
            // but older documents might not have the field at all.
            !doc.contains("invoiceDate") || doc.getDate("invoiceDate")?.time == 0L
        }

        if (docsToUpdate.isEmpty()) return

        docsToUpdate.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                val createdAt = doc.getDate("createdAt") ?: Date()
                batch.update(doc.reference, "invoiceDate", createdAt)
            }
            batch.commit().await()
        }
    }

    suspend fun deletePayment(paymentId: String, invoiceId: String) {
        val invoiceRef = firestore.collection(Invoice.COLLECTION_NAME).document(invoiceId)

        // Find linked ledger entries
        val treasuryTransactions = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", paymentId).get().await()

        firestore.runTransaction { transaction ->
            // 1. Reads
            val invoiceSnap = transaction.get(invoiceRef)
            val invoice = invoiceSnap.toObject(Invoice::class.java)?.copy(id = invoiceSnap.id) ?: return@runTransaction
            val paymentRef = firestore.collection(Payment.COLLECTION_NAME).document(paymentId)
            val pSnap = transaction.get(paymentRef)
            val oldPayment = pSnap.toObject(Payment::class.java)?.copy(id = pSnap.id) ?: return@runTransaction
            val summarySnapshots = summaryRepository.getSummarySnapshots(transaction, invoice.warehouseId)
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // 2. Writes
            transaction.delete(paymentRef)

            // Delete linked ledger entries
            treasuryTransactions.documents.forEach { doc ->
                transaction.delete(doc.reference)
            }
            
            val newTotalPaid = invoice.paidAmount - oldPayment.amount
            val newRemaining = invoice.totalAmount - newTotalPaid
            val newStatus = if (newRemaining <= 0.001) "paid" else "pending"
            
            transaction.update(invoiceRef, mapOf(
                "paidAmount" to newTotalPaid,
                "remainingAmount" to newRemaining,
                "status" to newStatus,
                "updatedAt" to Date()
            ))

            // Update Financial Summary
            summaryRepository.applyFinancialUpdate(
                transaction = transaction,
                snapshots = summarySnapshots,
                warehouseId = invoice.warehouseId,
                cashChange = if (oldPayment.paymentMethod == "cash") -oldPayment.amount else 0.0,
                bankChange = if (oldPayment.paymentMethod == "bank") -oldPayment.amount else 0.0,
                pendingCollectionChange = oldPayment.amount
            )

            // Update System Stats
            val statsUpdates = mutableMapOf<String, Any>(
                "totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(oldPayment.amount)
            )

            if (oldPayment.paymentMethod == "cash") {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(-oldPayment.amount)
            } else if (oldPayment.paymentMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(-oldPayment.amount)
            }

            transaction.update(statsRef, statsUpdates)
        }.await()
    }
}
 
