package com.batterysales.data.repositories

import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.batterysales.data.models.BillType
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Supplier
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

class BillRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val summaryRepository: SummaryRepository
) {

    suspend fun getAllBills(): List<Bill> {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
    }

    suspend fun getBill(id: String): Bill? {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME).document(id).get().await()
        return snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id)
    }

    fun getAllBillsFlow(): Flow<List<Bill>> = callbackFlow {
        val listenerRegistration = firestore.collection(Bill.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val bills = snapshot.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
                    trySend(bills).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addBill(bill: Bill): String {
        val docRef = if (bill.id.isNotEmpty()) firestore.collection(Bill.COLLECTION_NAME).document(bill.id)
                    else firestore.collection(Bill.COLLECTION_NAME).document()
        val finalBill = bill.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        
        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val entrySnap = if (finalBill.relatedEntryId != null) {
                transaction.get(firestore.collection(StockEntry.COLLECTION_NAME).document(finalBill.relatedEntryId))
            } else null

            // --- WRITE PHASE ---
            transaction.set(docRef, finalBill)
            
            // For Checks and Bills, we credit the FULL amount immediately to the supplier, even if UNPAID.
            // This allows them to cover/settle invoices immediately (FIFO).
            val creditToApply = if (finalBill.billType == BillType.CHECK || finalBill.billType == BillType.BILL) {
                finalBill.amount
            } else {
                finalBill.paidAmount
            }

            var amountToPool = creditToApply

            // 1. If manually linked to a purchase order entry, settle it first
            if (entrySnap != null && creditToApply > 0) {
                val entry = entrySnap.toObject(StockEntry::class.java)
                if (entry != null) {
                    val currentRemaining = entry.remainingBalance ?: entry.getNetCost()
                    val allocation = minOf(creditToApply, currentRemaining)
                    
                    val notes = entry.settlementNotes.toMutableList()
                    val typeLabel = when(finalBill.billType) {
                        BillType.CHECK -> "شيك"
                        BillType.BILL -> "كمبيالة"
                        BillType.CASH -> "نقدي"
                        BillType.VISA -> "فيزا"
                        BillType.E_WALLET -> "محفظة"
                        BillType.TRANSFER -> "تحويل"
                        else -> "دفعة"
                    }
                    notes.add("ارتباط يدوي ($typeLabel): JD ${String.format("%.3f", allocation)} (#${finalBill.referenceNumber})")
                    
                    val allocations = entry.linkedAllocations.toMutableMap()
                    allocations[finalBill.id] = (allocations[finalBill.id] ?: 0.0) + allocation

                    val newRemaining = (currentRemaining - allocation).coerceAtLeast(0.0)
                    transaction.update(entrySnap.reference, mapOf(
                        "settlementNotes" to notes.distinct(),
                        "remainingBalance" to newRemaining,
                        "isSettled" to (newRemaining <= 0.001),
                        "linkedAllocations" to allocations
                    ))

                    // Update bill to record manual allocation
                    transaction.update(docRef, "manualAllocation", allocation)
                    
                    // Only pool the SURPLUS amount
                    amountToPool = (creditToApply - allocation).coerceAtLeast(0.0)
                }
            }

            // 2. Update Supplier Totals and unallocatedCredit
            if (finalBill.supplierId.isNotEmpty()) {
                val supplierRef = firestore.collection("suppliers").document(finalBill.supplierId)
                if (creditToApply > 0) {
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(creditToApply))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-creditToApply))
                    
                    if (amountToPool > 0.001) {
                        transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(amountToPool))
                    }
                }
            }

            // 3. Update Global System Stats
            if (creditToApply > 0.001) {
                val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
                transaction.update(statsRef, mapOf(
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(-creditToApply),
                    "updatedAt" to Date()
                ))
            }
        }.await()

        // Trigger FIFO settlement
        if (finalBill.supplierId.isNotEmpty()) {
            autoLinkBillsForSupplier(finalBill.supplierId)
        }
        
        return docRef.id
    }

    suspend fun updateBillStatus(billId: String, status: BillStatus, paidDate: Date? = null) {
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to Date()
        )
        paidDate?.let { updates["paidDate"] = it }

        firestore.collection(Bill.COLLECTION_NAME)
            .document(billId)
            .update(updates)
            .await()
    }

    suspend fun recordPayment(billId: String, paymentAmount: Double) {
        val billRef = firestore.collection(Bill.COLLECTION_NAME).document(billId)

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val snapshot = transaction.get(billRef)
            val bill = snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id) ?: return@runTransaction
            
            val targetMethod = if (bill.billType == BillType.CHECK) "bank" else "cash"
            val targetWhId = if (bill.billType == BillType.BILL) {
                bill.warehouseId ?: "main_treasury" 
            } else "global"

            val snapshots = summaryRepository.getSummarySnapshots(transaction, targetWhId)

            val isAlreadyCredited = bill.billType == BillType.CHECK || bill.billType == BillType.BILL
            val entrySnap = if (bill.relatedEntryId != null && !isAlreadyCredited) {
                transaction.get(firestore.collection(StockEntry.COLLECTION_NAME).document(bill.relatedEntryId!!))
            } else null

            // --- WRITE PHASE ---
            val newPaidAmount = bill.paidAmount + paymentAmount
            val newStatus = when {
                newPaidAmount >= bill.amount -> BillStatus.PAID
                newPaidAmount > 0 -> BillStatus.PARTIAL
                else -> BillStatus.UNPAID
            }

            val updates = mutableMapOf<String, Any>(
                "paidAmount" to newPaidAmount,
                "status" to newStatus,
                "updatedAt" to Date()
            )

            if (newStatus == BillStatus.PAID) {
                updates["paidDate"] = Date()
            }

            transaction.update(billRef, updates)

            // Create Transaction Record
            if (targetMethod == "bank") {
                val bankRef = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME).document()
                transaction.set(bankRef, com.batterysales.data.models.BankTransaction(
                    id = bankRef.id,
                    billId = bill.id,
                    amount = paymentAmount,
                    type = com.batterysales.data.models.BankTransactionType.WITHDRAWAL,
                    description = "تسديد شيك: ${bill.description}",
                    referenceNumber = bill.referenceNumber,
                    date = Date(),
                    isSystemManaged = true
                ))
            } else {
                val treasuryRef = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME).document()
                transaction.set(treasuryRef, com.batterysales.data.models.Transaction(
                    id = treasuryRef.id,
                    relatedId = bill.id,
                    amount = paymentAmount,
                    type = com.batterysales.data.models.TransactionType.EXPENSE,
                    description = "تسديد كمبيالة: ${bill.description}",
                    referenceNumber = bill.referenceNumber,
                    warehouseId = targetWhId,
                    createdAt = Date(),
                    isSystemManaged = true
                ))
            }

            // Update Summaries
            summaryRepository.applyFinancialUpdate(
                transaction = transaction,
                snapshots = snapshots,
                warehouseId = targetWhId,
                cashChange = if (targetMethod == "cash") -paymentAmount else 0.0,
                bankChange = if (targetMethod == "bank") -paymentAmount else 0.0,
                billChange = -paymentAmount
            )

            // Update Supplier Denormalized Totals
            if (bill.supplierId.isNotEmpty()) {
                val supplierRef = firestore.collection("suppliers").document(bill.supplierId)

                if (!isAlreadyCredited) {
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-paymentAmount))
                }
                
                // Add to unallocated pool (Check if it has a manual link to deduct first)
                var unallocatedToAdd = if (isAlreadyCredited) 0.0 else paymentAmount
                if (entrySnap != null) {
                    val entry = entrySnap.toObject(StockEntry::class.java)
                    if (entry != null) {
                        val currentRemaining = entry.remainingBalance ?: entry.getNetCost()
                        val allocation = minOf(paymentAmount, currentRemaining)
                        val newRemaining = (currentRemaining - allocation).coerceAtLeast(0.0)
                        
                        val notes = entry.settlementNotes.toMutableList()
                        val typeLabel = when(bill.billType) {
                            BillType.CHECK -> "شيك"
                            BillType.BILL -> "كمبيالة"
                            else -> "دفعة"
                        }
                        notes.add("تسديد ($typeLabel): JD ${String.format("%.3f", allocation)} (#${bill.referenceNumber})")
                        
                        val allocations = entry.linkedAllocations.toMutableMap()
                        allocations[bill.id] = (allocations[bill.id] ?: 0.0) + allocation

                        transaction.update(entrySnap.reference, mapOf(
                            "remainingBalance" to newRemaining,
                            "isSettled" to (newRemaining <= 0.001),
                            "settlementNotes" to notes.distinct(),
                            "linkedAllocations" to allocations
                        ))

                        // Update bill's manual allocation record
                        transaction.update(billRef, "manualAllocation", bill.manualAllocation + allocation)

                        unallocatedToAdd -= allocation
                    }
                }
                
                if (unallocatedToAdd > 0.001) {
                    transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(unallocatedToAdd))
                }
            }

            // Update Global System Stats
            if (!isAlreadyCredited) {
                val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
                transaction.update(statsRef, mapOf(
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(-paymentAmount),
                    "updatedAt" to java.util.Date()
                ))
            }
        }.await()

        // Trigger FIFO settlement
        getBill(billId)?.let { freshBill ->
            if (freshBill.supplierId.isNotEmpty()) {
                autoLinkBillsForSupplier(freshBill.supplierId)
            }
        }
    }

    suspend fun addBillPayment(bill: Bill, paymentAmount: Double, method: String, warehouseId: String, notes: String) {
        val billRef = firestore.collection(Bill.COLLECTION_NAME).document(bill.id)

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val snapshot = transaction.get(billRef)
            val freshBill = snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id) ?: return@runTransaction
            
            val targetMethod = if (freshBill.billType == BillType.CHECK) "bank" else "cash"
            val targetWhId = if (freshBill.billType == BillType.BILL) {
                warehouseId 
            } else "global"

            val snapshots = summaryRepository.getSummarySnapshots(transaction, targetWhId)

            val entrySnap = if (freshBill.relatedEntryId != null && freshBill.billType != BillType.CHECK && freshBill.billType != BillType.BILL) {
                transaction.get(firestore.collection(StockEntry.COLLECTION_NAME).document(freshBill.relatedEntryId!!))
            } else null

            // --- WRITE PHASE ---
            val newPaidAmount = freshBill.paidAmount + paymentAmount
            val newStatus = when {
                newPaidAmount >= freshBill.amount -> BillStatus.PAID
                newPaidAmount > 0 -> BillStatus.PARTIAL
                else -> BillStatus.UNPAID
            }

            val updates = mutableMapOf<String, Any>(
                "paidAmount" to newPaidAmount,
                "status" to newStatus,
                "updatedAt" to Date()
            )

            if (newStatus == BillStatus.PAID) {
                updates["paidDate"] = Date()
            }

            transaction.update(billRef, updates)

            // Create Transaction Record (Accounting or Bank)
            if (targetMethod == "bank") {
                val bankRef = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME).document()
                transaction.set(bankRef, com.batterysales.data.models.BankTransaction(
                    id = bankRef.id,
                    type = com.batterysales.data.models.BankTransactionType.WITHDRAWAL,
                    amount = paymentAmount,
                    description = "تسديد شيك: ${freshBill.description} (#${freshBill.referenceNumber})",
                    referenceNumber = freshBill.referenceNumber,
                    billId = freshBill.id,
                    date = Date(),
                    isSystemManaged = true
                ))
            } else {
                val treasuryRef = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME).document()
                transaction.set(treasuryRef, com.batterysales.data.models.Transaction(
                    id = treasuryRef.id,
                    type = com.batterysales.data.models.TransactionType.EXPENSE,
                    amount = paymentAmount,
                    description = "تسديد كمبيالة: ${freshBill.description} (#${freshBill.referenceNumber})",
                    referenceNumber = freshBill.referenceNumber,
                    warehouseId = targetWhId,
                    relatedId = freshBill.id,
                    paymentMethod = "cash",
                    createdAt = Date(),
                    isSystemManaged = true
                ))
            }

            // Update Summaries
            summaryRepository.applyFinancialUpdate(
                transaction = transaction,
                snapshots = snapshots,
                warehouseId = targetWhId,
                cashChange = if (targetMethod == "cash") -paymentAmount else 0.0,
                bankChange = if (targetMethod == "bank") -paymentAmount else 0.0,
                billChange = -paymentAmount
            )

            // Update Supplier Denormalized Totals
            if (freshBill.supplierId.isNotEmpty()) {
                val supplierRef = firestore.collection("suppliers").document(freshBill.supplierId)

                if (freshBill.billType != BillType.CHECK && freshBill.billType != BillType.BILL) {
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-paymentAmount))

                    var amountToPool = paymentAmount
                    // If this specific payment was linked to an entry, we handle it
                    if (entrySnap != null) {
                        val entry = entrySnap.toObject(StockEntry::class.java)
                        if (entry != null) {
                            val currentRemaining = entry.remainingBalance ?: entry.getNetCost()
                            val allocation = minOf(paymentAmount, currentRemaining)
                            val newRemaining = (currentRemaining - allocation).coerceAtLeast(0.0)

                            val notes = entry.settlementNotes.toMutableList()
                            val typeLabel = when(freshBill.billType) {
                                BillType.CHECK -> "شيك"
                                BillType.BILL -> "كمبيالة"
                                else -> "دفعة"
                            }
                            notes.add("تسديد ($typeLabel): JD ${String.format("%.3f", allocation)} (#${freshBill.referenceNumber})")

                            val allocations = entry.linkedAllocations.toMutableMap()
                            allocations[freshBill.id] = (allocations[freshBill.id] ?: 0.0) + allocation

                            transaction.update(entrySnap.reference, mapOf(
                                "remainingBalance" to newRemaining,
                                "isSettled" to (newRemaining <= 0.001),
                                "settlementNotes" to notes.distinct(),
                                "linkedAllocations" to allocations
                            ))

                            transaction.update(billRef, "manualAllocation", freshBill.manualAllocation + allocation)
                            amountToPool -= allocation
                        }
                    }

                    if (amountToPool > 0.001) {
                        transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(amountToPool))
                    }
                }
            }

            // Update Global System Stats
            if (freshBill.billType != BillType.CHECK && freshBill.billType != BillType.BILL) {
                val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
                transaction.update(statsRef, mapOf(
                    "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(-paymentAmount),
                    "updatedAt" to java.util.Date()
                ))
            }
        }.await()

        // Trigger FIFO settlement
        if (bill.supplierId.isNotEmpty()) {
            autoLinkBillsForSupplier(bill.supplierId)
        }
    }

    suspend fun deleteBill(billId: String) {
        val billRef = firestore.collection(Bill.COLLECTION_NAME).document(billId)
        
        firestore.runTransaction { transaction ->
            // 1. Reads
            val billSnap = transaction.get(billRef)
            val bill = billSnap.toObject(Bill::class.java) ?: return@runTransaction
            val snapshots = summaryRepository.getSummarySnapshots(transaction, "global")
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // 2. Writes
            transaction.delete(billRef)

            // Reverse financial impact
            val creditToRemove = if (bill.billType == BillType.CHECK || bill.billType == BillType.BILL) {
                bill.amount
            } else {
                bill.paidAmount
            }

            if (creditToRemove > 0) {
                summaryRepository.applyFinancialUpdate(
                    transaction = transaction,
                    snapshots = snapshots,
                    warehouseId = "global",
                    billChange = bill.paidAmount // Reversing the actual financial clearing
                )

                // Update Supplier Denormalized Totals
                if (bill.supplierId.isNotEmpty()) {
                    val supplierRef = firestore.collection("suppliers").document(bill.supplierId)
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(-creditToRemove))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(creditToRemove))

                    // We need to estimate how much of THIS bill was still in the unallocated pool.
                    // This is complex, but standard reversal is to deduct from the pool.
                    transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(-creditToRemove))
                }

                // Update System Stats
                transaction.update(statsRef, "totalSupplierDebt", com.google.firebase.firestore.FieldValue.increment(creditToRemove))
            }
        }.await()
    }

    suspend fun getBillsPaginated(
        searchQuery: String? = null,
        supplierId: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<Bill>, DocumentSnapshot?> {
        var query: Query = firestore.collection(Bill.COLLECTION_NAME)

        if (!supplierId.isNullOrEmpty()) {
            query = query.whereEqualTo("supplierId", supplierId)
        }

        if (!searchQuery.isNullOrBlank()) {
            // Server-side search by referenceNumber (most common)
            query = query.whereGreaterThanOrEqualTo("referenceNumber", searchQuery)
                .whereLessThanOrEqualTo("referenceNumber", searchQuery + "\uf8ff")
                .orderBy("referenceNumber", Query.Direction.ASCENDING)
        } else {
            // Default sorting by supplierId (ASC) and then dueDate (ASC)
            if (supplierId.isNullOrEmpty()) {
                query = query.orderBy("supplierId", Query.Direction.ASCENDING)
            }
            query = query.orderBy("dueDate", Query.Direction.ASCENDING)
        }

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.limit(limit).get().await()
        val bills = snapshot.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(bills, lastDoc)
    }

    suspend fun getSupplierCredit(supplierId: String, resetDate: java.util.Date? = null, startDate: Long? = null, endDate: Long? = null): Double {
        var query = firestore.collection(Bill.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)

        resetDate?.let { query = query.whereGreaterThan("createdAt", it) }
        startDate?.let { query = query.whereGreaterThanOrEqualTo("dueDate", java.util.Date(com.batterysales.utils.DateUtils.getStartOfDay(it))) }
        endDate?.let { query = query.whereLessThanOrEqualTo("dueDate", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(it))) }

        val snapshot = query.aggregate(AggregateField.sum("paidAmount")).get(AggregateSource.SERVER).await()
        return snapshot.getDouble(AggregateField.sum("paidAmount")) ?: 0.0
    }

    suspend fun getBillsBySuppliers(supplierIds: List<String>): List<Bill> {
        if (supplierIds.isEmpty()) return emptyList()
        val all = mutableListOf<Bill>()
        supplierIds.chunked(30).forEach { chunk ->
            val snap = firestore.collection(Bill.COLLECTION_NAME)
                .whereIn("supplierId", chunk)
                .get().await()
            all.addAll(snap.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) })
        }
        return all
    }

    suspend fun updateBill(bill: Bill) {
        val billRef = firestore.collection(Bill.COLLECTION_NAME).document(bill.id)
        
        firestore.runTransaction { transaction ->
            // 1. Reads
            val oldSnap = transaction.get(billRef)
            val oldBill = oldSnap.toObject(Bill::class.java) ?: return@runTransaction
            
            // 2. Writes
            val updates = mutableMapOf<String, Any>(
                "description" to bill.description,
                "amount" to bill.amount,
                "dueDate" to bill.dueDate,
                "billType" to bill.billType,
                "referenceNumber" to bill.referenceNumber,
                "supplierId" to bill.supplierId,
                "updatedAt" to Date()
            )
            transaction.update(billRef, updates)
            
            // Note: If amount changed, we might need to recalculate status, but typically 
            // these basic updates don't change paidAmount.
            if (bill.amount != oldBill.amount) {
                val newStatus = when {
                    oldBill.paidAmount >= bill.amount -> BillStatus.PAID
                    oldBill.paidAmount > 0 -> BillStatus.PARTIAL
                    else -> BillStatus.UNPAID
                }
                transaction.update(billRef, "status", newStatus)
            }
        }.await()
    }

    /**
     * Manual Sync: Recalculates everything for a supplier from scratch.
     * This is now an ON-DEMAND operation to save quota and time.
     */
    suspend fun syncSupplierFinancials(supplierId: String) {
        if (supplierId.isEmpty()) return

        val supplierRef = firestore.collection("suppliers").document(supplierId)
        val bills = firestore.collection(Bill.COLLECTION_NAME).whereEqualTo("supplierId", supplierId).get().await()
            .documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }

        val entries = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
            .whereEqualTo("status", "approved")
            .get().await()
            .documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }

        val positiveEntries = entries.filter { it.totalCost > 0 }
        val returns = entries.filter { it.totalCost < 0 }

        // Calculate Pool
        val totalReturnCredit = returns.sumOf { -it.totalCost }
        val totalBillCredit = bills.sumOf { b ->
            if (b.billType == BillType.CHECK || b.billType == BillType.BILL) b.amount else b.paidAmount
        }
        val totalManualAllocation = bills.sumOf { it.manualAllocation }

        val totalCreditPool = totalReturnCredit + totalBillCredit
        val unallocatedPool = (totalCreditPool - totalManualAllocation).coerceAtLeast(0.0)
        val totalDebit = positiveEntries.sumOf { it.totalCost }

        // Split updates into batches to handle the 500-op limit
        val allDocsToUpdate = positiveEntries
        val chunks = allDocsToUpdate.chunked(450) // Leaving room for supplier update

        chunks.forEach { chunk ->
            firestore.runTransaction { transaction ->
                chunk.forEach { entry ->
                    transaction.update(firestore.collection(StockEntry.COLLECTION_NAME).document(entry.id), mapOf(
                        "remainingBalance" to entry.totalCost,
                        "isSettled" to false,
                        "settlementNotes" to emptyList<String>(),
                        "linkedAllocations" to emptyMap<String, Double>()
                    ))
                }
            }.await()
        }

        firestore.runTransaction { transaction ->
            // Update Supplier Totals
            transaction.update(supplierRef, mapOf(
                "totalDebit" to totalDebit,
                "totalCredit" to totalCreditPool,
                "currentBalance" to (totalDebit - totalCreditPool),
                "unallocatedCredit" to unallocatedPool
            ))
        }.await()

        // 3. Run FIFO Linkage (It will re-apply manual links first internally)
        autoLinkBillsForSupplier(supplierId)
    }

    /**
     * Advanced Incremental FIFO for Grouped Orders:
     * Treats grouped StockEntries (Invoices) as single units.
     * Records detailed settlement notes including reference numbers.
     */
    suspend fun autoLinkBillsForSupplier(supplierId: String, resetDate: Date? = null) {
        if (supplierId.isEmpty()) return

        // 1. Get ALL unsettled entries for this supplier
        val entriesQuery = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
            .whereEqualTo("status", "approved")
            .whereEqualTo("isSettled", false)
        
        val entriesSnap = entriesQuery.get().await()
        if (entriesSnap.isEmpty) return

        // 2. Fetch all bills and RETURNS
        val billsQuery = firestore.collection(Bill.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
        val billsSnap = billsQuery.get().await()

        val returnsQuery = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
            .whereEqualTo("status", "approved")
            .whereLessThan("totalCost", 0.0)
        val returnsSnap = returnsQuery.get().await()

        val supplierBills = billsSnap.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
            .sortedBy { it.createdAt }
        val supplierReturns = returnsSnap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
            .sortedBy { it.timestamp }

        val allEntries = entriesSnap.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        val groupedOrders = allEntries.groupBy { it.invoiceNumber.trim().ifEmpty { it.orderId.trim().ifEmpty { it.id } }.lowercase() }
        val orderedGroups = groupedOrders.values.sortedBy { it.first().getEffectiveDate() }

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val supplierRef = firestore.collection("suppliers").document(supplierId)
            val supplierSnap = transaction.get(supplierRef)
            val supplier = supplierSnap.toObject(Supplier::class.java)
            
            var unallocatedPool = supplier?.unallocatedCredit ?: 0.0

            // Gather entries snapshots
            val entriesToRead = orderedGroups.flatMap { it }.map { it.id }.distinct()
            val entrySnapshots = entriesToRead.associateWith { id ->
                transaction.get(firestore.collection(StockEntry.COLLECTION_NAME).document(id))
            }

            // --- CALCULATION PHASE (No transaction writes yet) ---
            val documentUpdates = mutableMapOf<String, Map<String, Any>>()

            // 1. Re-apply Manual Allocations if needed
            supplierBills.filter { it.relatedEntryId != null && it.manualAllocation > 0.001 }.forEach { bill ->
                val entrySnap = entrySnapshots[bill.relatedEntryId]
                if (entrySnap != null) {
                    val entry = entrySnap.toObject(StockEntry::class.java)
                    if (entry != null) {
                        val typeLabel = when(bill.billType) {
                            BillType.CHECK -> "شيك"
                            BillType.BILL -> "كمبيالة"
                            else -> "دفعة"
                        }
                        val manualNote = "ارتباط يدوي ($typeLabel): JD ${String.format("%.3f", bill.manualAllocation)} (#${bill.referenceNumber})"

                        if (!entry.settlementNotes.contains(manualNote)) {
                            val currentRemaining = entry.remainingBalance ?: entry.getNetCost()
                            val newRemaining = (currentRemaining - bill.manualAllocation).coerceAtLeast(0.0)
                            val newNotes = (entry.settlementNotes + manualNote).distinct()
                            val newAllocations = entry.linkedAllocations.toMutableMap()
                            newAllocations[bill.id] = bill.manualAllocation

                            documentUpdates[entry.id] = mapOf(
                                "remainingBalance" to newRemaining,
                                "isSettled" to (newRemaining <= 0.001),
                                "settlementNotes" to newNotes,
                                "linkedAllocations" to newAllocations
                            )
                        }
                    }
                }
            }

            // 2. FIFO Linking
            if (unallocatedPool > 0.001) {
                val creditSources = (
                    supplierBills.filter { it.billType == BillType.CHECK || it.billType == BillType.BILL || it.paidAmount > 0 }
                        .map { b ->
                            val totalAmount = if (b.billType == BillType.CHECK || b.billType == BillType.BILL) b.amount else b.paidAmount
                            val availableAmount = (totalAmount - b.manualAllocation).coerceAtLeast(0.0)
                            CreditSource(
                                id = b.id,
                                type = when(b.billType){ BillType.CHECK -> "شيك"; BillType.BILL -> "كمبيالة"; else -> "دفعة" },
                                ref = b.referenceNumber,
                                amount = availableAmount,
                                date = b.createdAt
                            )
                        } +
                    supplierReturns.map { r ->
                        CreditSource(
                            id = r.id,
                            type = "مرتجع مواد",
                            ref = r.invoiceNumber,
                            amount = -r.totalCost,
                            date = r.timestamp
                        )
                    }
                ).sortedBy { it.date }

                val totalCreditEver = creditSources.sumOf { it.amount }
                var creditAlreadyConsumed = totalCreditEver - unallocatedPool

                val activeSources = creditSources.toMutableList()
                while (activeSources.isNotEmpty() && creditAlreadyConsumed >= activeSources.first().amount - 0.001) {
                    creditAlreadyConsumed -= activeSources.removeAt(0).amount
                }
                
                if (activeSources.isNotEmpty() && creditAlreadyConsumed > 0.001) {
                    val first = activeSources[0]
                    activeSources[0] = first.copy(amount = first.amount - creditAlreadyConsumed)
                }

                orderedGroups.forEach { group ->
                    if (activeSources.isEmpty()) return@forEach

                    val freshGroup = group.mapNotNull { entrySnapshots[it.id]?.toObject(StockEntry::class.java)?.copy(id = it.id) }

                    freshGroup.forEach { doc ->
                        // Start with updates from manual re-link if any
                        val currentUpdate = documentUpdates[doc.id]
                        var itemBalance = if (currentUpdate != null) {
                            currentUpdate["remainingBalance"] as Double
                        } else {
                            doc.remainingBalance ?: doc.getNetCost()
                        }

                        if (itemBalance <= 0.001) {
                            if (!documentUpdates.containsKey(doc.id)) {
                                documentUpdates[doc.id] = mapOf("isSettled" to true, "remainingBalance" to 0.0)
                            }
                            return@forEach
                        }

                        val notes = (currentUpdate?.get("settlementNotes") as? List<String> ?: doc.settlementNotes).toMutableList()
                        val allocations = (currentUpdate?.get("linkedAllocations") as? Map<String, Double> ?: doc.linkedAllocations).toMutableMap()

                        while (itemBalance > 0.001 && activeSources.isNotEmpty()) {
                            val source = activeSources.first()
                            val allocation = minOf(itemBalance, source.amount)

                            itemBalance -= allocation
                            unallocatedPool -= allocation

                            if (source.amount - allocation <= 0.001) {
                                activeSources.removeAt(0)
                            } else {
                                activeSources[0] = source.copy(amount = source.amount - allocation)
                            }

                            val noteText = "${source.type}: ${source.ref} (ربط تلقائي): JD ${String.format("%.3f", allocation)}"
                            notes.add(noteText)
                            allocations[source.id] = (allocations[source.id] ?: 0.0) + allocation
                        }

                        documentUpdates[doc.id] = mapOf(
                            "remainingBalance" to itemBalance.coerceAtLeast(0.0),
                            "isSettled" to (itemBalance <= 0.001),
                            "settlementNotes" to notes.distinct(),
                            "linkedAllocations" to allocations
                        )
                    }
                }
            }

            // --- WRITE PHASE ---
            documentUpdates.forEach { (id, updates) ->
                transaction.update(firestore.collection(StockEntry.COLLECTION_NAME).document(id), updates)
            }
            transaction.update(supplierRef, "unallocatedCredit", unallocatedPool)
        }.await()
    }

    suspend fun getLinkedEntryIds(): Set<String> {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME)
            .whereNotEqualTo("relatedEntryId", null)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.getString("relatedEntryId") }.toSet()
    }

    private data class CreditSource(
        val id: String,
        val type: String,
        val ref: String,
        val amount: Double,
        val date: Date
    )

    suspend fun getLinkedAmounts(): Map<String, Double> {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME).get().await()
        val bills = snapshot.documents.mapNotNull { it.toObject(Bill::class.java) }
        val manual = bills.filter { it.relatedEntryId != null }.groupBy { it.relatedEntryId!! }.mapValues { (_, group) -> group.sumOf { it.amount } }
        val auto = mutableMapOf<String, Double>()
        bills.forEach { bill ->
            bill.autoAllocations.forEach { (id, amount) -> auto[id] = (auto[id] ?: 0.0) + amount }
        }
        val result = manual.toMutableMap()
        auto.forEach { (id, amount) -> result[id] = (result[id] ?: 0.0) + amount }
        return result
    }
}
