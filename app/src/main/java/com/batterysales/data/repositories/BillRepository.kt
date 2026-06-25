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
    private val summaryRepository: SummaryRepository,
    private val stockEntryRepository: StockEntryRepository
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

    private suspend fun getMainWarehouseId(): String {
        return try {
            val snapshot = firestore.collection("warehouses").get().await()
            val warehouses = snapshot.documents.mapNotNull { it.toObject(com.batterysales.data.models.Warehouse::class.java)?.copy(id = it.id) }
            
            warehouses.find { it.isMain }?.id 
                ?: warehouses.find { it.name.contains("رئيسي") || it.name.lowercase().contains("main") }?.id
                ?: warehouses.firstOrNull()?.id
                ?: "main_treasury"
        } catch (e: Exception) {
            "main_treasury"
        }
    }

    suspend fun addBill(bill: Bill): String {
        val docRef = if (bill.id.isNotEmpty()) firestore.collection(Bill.COLLECTION_NAME).document(bill.id)
                    else firestore.collection(Bill.COLLECTION_NAME).document()
        val finalBill = bill.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        
        val mainWhId = if (finalBill.warehouseId.isNullOrEmpty() || finalBill.warehouseId == "main_treasury") getMainWarehouseId() else finalBill.warehouseId!!

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val targetWhId = mainWhId
            val snapshots = summaryRepository.getSummarySnapshots(transaction, listOf(targetWhId))
            
            // --- WRITE PHASE ---
            transaction.set(docRef, finalBill)
            
            // For Commitments (Checks/Bills), we credit the FULL amount immediately
            // to the supplier's FIFO pool, even if not yet cleared. This ensures invoices are "Covered".
            val creditToApply = if (finalBill.billType == BillType.CHECK || finalBill.billType == BillType.BILL) {
                finalBill.amount
            } else {
                finalBill.paidAmount
            }

            // Create Financial Transaction if there is immediate payment (CASH, TRANSFER, etc.)
            if (finalBill.paidAmount > 0.001) {
                val typeLabel = when(finalBill.billType) {
                    BillType.CHECK -> "شيك"
                    BillType.BILL -> "كمبيالة"
                    BillType.CASH -> "نقدي"
                    BillType.TRANSFER -> "تحويل"
                    else -> "دفعة"
                }

                if (finalBill.billType == BillType.TRANSFER) {
                    val bankRef = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME).document()
                    transaction.set(bankRef, com.batterysales.data.models.BankTransaction(
                        id = bankRef.id,
                        billId = finalBill.id,
                        amount = finalBill.paidAmount,
                        type = com.batterysales.data.models.BankTransactionType.WITHDRAWAL,
                        description = "دفعة مورد ($typeLabel): ${finalBill.description}",
                        referenceNumber = finalBill.referenceNumber,
                        date = Date(),
                        isSystemManaged = true
                    ))
                } else if (finalBill.billType != BillType.CHECK && finalBill.billType != BillType.BILL) {
                    // For CASH or other immediate types, deduct from treasury
                    val treasuryRef = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME).document()
                    transaction.set(treasuryRef, com.batterysales.data.models.Transaction(
                        id = treasuryRef.id,
                        relatedId = finalBill.id,
                        amount = finalBill.paidAmount,
                        type = com.batterysales.data.models.TransactionType.EXPENSE,
                        description = "دفعة مورد ($typeLabel): ${finalBill.description}",
                        referenceNumber = finalBill.referenceNumber,
                        warehouseId = targetWhId,
                        paymentMethod = "cash",
                        createdAt = Date(),
                        isSystemManaged = true
                    ))
                }

                // Update Summaries for immediate payment
                summaryRepository.applyFinancialUpdate(
                    transaction = transaction,
                    snapshots = snapshots,
                    warehouseId = targetWhId,
                    cashChange = if (finalBill.billType != BillType.TRANSFER && finalBill.billType != BillType.CHECK) -finalBill.paidAmount else 0.0,
                    bankChange = if (finalBill.billType == BillType.TRANSFER) -finalBill.paidAmount else 0.0
                )
            }

            var amountToPool = creditToApply

            // 2. Update Supplier Totals and unallocatedCredit
            if (finalBill.supplierId.isNotEmpty()) {
                summaryRepository.invalidateSupplierReportCache(transaction, finalBill.supplierId)
                val supplierRef = firestore.collection("suppliers").document(finalBill.supplierId)
                if (creditToApply > 0) {
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(creditToApply))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-creditToApply))
                    transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(creditToApply))
                }
            }

            // 3. Update Global System Stats
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            val statsUpdates = mutableMapOf<String, Any>(
                "updatedAt" to Date()
            )
            
            if (creditToApply > 0.001) {
                statsUpdates["totalSupplierDebt"] = com.google.firebase.firestore.FieldValue.increment(-creditToApply)
            }

            // Track Commitments in stats
            val commitmentChange = if (finalBill.billType == BillType.CHECK || finalBill.billType == BillType.BILL) {
                finalBill.amount - finalBill.paidAmount
            } else 0.0
            
            if (commitmentChange > 0.001) {
                if (finalBill.billType == BillType.CHECK) {
                    statsUpdates["totalUnpaidChecks"] = com.google.firebase.firestore.FieldValue.increment(commitmentChange)
                } else {
                    statsUpdates["totalUnpaidBills"] = com.google.firebase.firestore.FieldValue.increment(commitmentChange)
                }
            }
            
            if (finalBill.paidAmount > 0.001) {
                if (finalBill.billType == BillType.TRANSFER) {
                    statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(-finalBill.paidAmount)
                } else if (finalBill.billType != BillType.CHECK && finalBill.billType != BillType.BILL) {
                    statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(-finalBill.paidAmount)
                }
            }
            
            if (statsUpdates.size > 1) {
                transaction.update(statsRef, statsUpdates)
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
        val mainWhId = getMainWarehouseId()

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val snapshot = transaction.get(billRef)
            val bill = snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id) ?: return@runTransaction
            
            val targetMethod = if (bill.billType == BillType.CHECK) "bank" else "cash"
            val targetWhId = if (bill.warehouseId.isNullOrEmpty() || bill.warehouseId == "main_treasury") mainWhId else bill.warehouseId!!

            val snapshots = summaryRepository.getSummarySnapshots(transaction, listOf(targetWhId))

            val isAlreadyCredited = bill.billType == BillType.CHECK || bill.billType == BillType.BILL

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
                billChange = if (bill.billType == BillType.BILL) -paymentAmount else 0.0,
                checkChange = if (bill.billType == BillType.CHECK) -paymentAmount else 0.0
            )

            // Update Supplier Denormalized Totals
            if (bill.supplierId.isNotEmpty()) {
                summaryRepository.invalidateSupplierReportCache(transaction, bill.supplierId)
                val supplierRef = firestore.collection("suppliers").document(bill.supplierId)
                
                if (!isAlreadyCredited) {
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-paymentAmount))
                    transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
                }
            }

            // Update Global System Stats
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            val statsUpdates = mutableMapOf<String, Any>(
                "updatedAt" to Date()
            )
            
            if (!isAlreadyCredited) {
                statsUpdates["totalSupplierDebt"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
            }

            // Adjust Unpaid commitments
            if (isAlreadyCredited) {
                if (bill.billType == BillType.CHECK) {
                    statsUpdates["totalUnpaidChecks"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
                } else if (bill.billType == BillType.BILL) {
                    statsUpdates["totalUnpaidBills"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
                }
            }
            
            if (targetMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
            } else {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
            }
            
            transaction.update(statsRef, statsUpdates)
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
        val mainWhId = getMainWarehouseId()

        firestore.runTransaction { transaction ->
            // --- READ PHASE ---
            val snapshot = transaction.get(billRef)
            val freshBill = snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id) ?: return@runTransaction
            
            val targetMethod = if (freshBill.billType == BillType.CHECK) "bank" else "cash"
            val targetWhId = if (warehouseId.isBlank() && (freshBill.warehouseId.isNullOrEmpty() || freshBill.warehouseId == "main_treasury")) {
                mainWhId
            } else {
                warehouseId.ifBlank { freshBill.warehouseId ?: mainWhId }
            }

            val snapshots = summaryRepository.getSummarySnapshots(transaction, listOf(targetWhId))

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
                billChange = if (freshBill.billType == BillType.BILL) -paymentAmount else 0.0,
                checkChange = if (freshBill.billType == BillType.CHECK) -paymentAmount else 0.0
            )

            // Update Supplier Denormalized Totals
            if (freshBill.supplierId.isNotEmpty()) {
                summaryRepository.invalidateSupplierReportCache(transaction, freshBill.supplierId)
                val supplierRef = firestore.collection("suppliers").document(freshBill.supplierId)
                
                val isAlreadyCredited = freshBill.billType == BillType.CHECK || freshBill.billType == BillType.BILL
                if (!isAlreadyCredited) {
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-paymentAmount))
                    transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
                }
            }

            // Update Global System Stats
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            val statsUpdates = mutableMapOf<String, Any>(
                "updatedAt" to Date()
            )

            val isAlreadyCredited = freshBill.billType == BillType.CHECK || freshBill.billType == BillType.BILL
            if (!isAlreadyCredited) {
                statsUpdates["totalSupplierDebt"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
            }

            // Adjust Unpaid commitments
            if (isAlreadyCredited) {
                if (freshBill.billType == BillType.CHECK) {
                    statsUpdates["totalUnpaidChecks"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
                } else if (freshBill.billType == BillType.BILL) {
                    statsUpdates["totalUnpaidBills"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
                }
            }
            
            if (targetMethod == "bank") {
                statsUpdates["totalBankBalance"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
            } else {
                statsUpdates["totalCashBalance"] = com.google.firebase.firestore.FieldValue.increment(-paymentAmount)
            }
            
            transaction.update(statsRef, statsUpdates)
        }.await()

        // Trigger FIFO settlement
        if (bill.supplierId.isNotEmpty()) {
            autoLinkBillsForSupplier(bill.supplierId)
        }
    }

    suspend fun deleteBill(billId: String) {
        val billRef = firestore.collection(Bill.COLLECTION_NAME).document(billId)
        
        var supplierIdToSync: String? = null

        // Find linked entries outside the transaction
        val bankTransactions = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME)
            .whereEqualTo("billId", billId)
            .get().await()
            
        val treasuryTransactions = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", billId)
            .get().await()

        firestore.runTransaction { transaction ->
            // 1. Reads
            val billSnap = transaction.get(billRef)
            val bill = billSnap.toObject(Bill::class.java) ?: return@runTransaction
            supplierIdToSync = bill.supplierId
            
            val snapshots = summaryRepository.getSummarySnapshots(transaction, listOf(bill.warehouseId ?: "global"))
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
                // Adjust totalSupplierDebt in SystemStats
                transaction.update(statsRef, "totalSupplierDebt", com.google.firebase.firestore.FieldValue.increment(creditToRemove))

                // Update Supplier Denormalized Totals
                if (bill.supplierId.isNotEmpty()) {
                    summaryRepository.invalidateSupplierReportCache(transaction, bill.supplierId)
                    val supplierRef = firestore.collection("suppliers").document(bill.supplierId)
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(-creditToRemove))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(creditToRemove))
                }
            }

            // --- Reverse Ledger Impacts ---
            // These totals represent actual money spent (paidAmount)
            bankTransactions.documents.forEach { doc ->
                val amt = doc.getDouble("amount") ?: 0.0
                transaction.update(statsRef, "totalBankBalance", com.google.firebase.firestore.FieldValue.increment(amt))
                summaryRepository.applyFinancialUpdate(transaction, snapshots, bill.warehouseId ?: "global", bankChange = amt)
                transaction.delete(doc.reference)
            }
            
            treasuryTransactions.documents.forEach { doc ->
                val amt = doc.getDouble("amount") ?: 0.0
                transaction.update(statsRef, "totalCashBalance", com.google.firebase.firestore.FieldValue.increment(amt))
                summaryRepository.applyFinancialUpdate(transaction, snapshots, bill.warehouseId ?: "global", cashChange = amt)
                transaction.delete(doc.reference)
            }

            // --- Reverse Unpaid Commitments in stats ---
            val remainingCommitment = bill.amount - bill.paidAmount
            if (remainingCommitment > 0.001) {
                if (bill.billType == BillType.CHECK) {
                    transaction.update(statsRef, "totalUnpaidChecks", com.google.firebase.firestore.FieldValue.increment(-remainingCommitment))
                } else if (bill.billType == BillType.BILL) {
                    transaction.update(statsRef, "totalUnpaidBills", com.google.firebase.firestore.FieldValue.increment(-remainingCommitment))
                }
            }
        }.await()

        // Trigger full re-sync for this supplier to clean up entry notes and balances
        supplierIdToSync?.let { syncSupplierFinancials(it) }
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

    suspend fun getBillsBySuppliers(supplierIds: List<String>, supplierNames: List<String> = emptyList()): List<Bill> {
        if (supplierIds.isEmpty() && supplierNames.isEmpty()) return emptyList()
        val all = mutableListOf<Bill>()
        
        if (supplierIds.isNotEmpty()) {
            supplierIds.chunked(30).forEach { chunk ->
                val snap = firestore.collection(Bill.COLLECTION_NAME)
                    .whereIn("supplierId", chunk)
                    .get().await()
                all.addAll(snap.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) })
            }
        }

        if (supplierNames.isNotEmpty()) {
            val names = supplierNames.filter { it.isNotEmpty() }
            if (names.isNotEmpty()) {
                names.chunked(30).forEach { chunk ->
                    val snap = firestore.collection(Bill.COLLECTION_NAME)
                        .whereIn("supplier", chunk)
                        .get().await()
                    
                    val existingIds = all.map { it.id }.toSet()
                    val legacyBills = snap.documents.mapNotNull { it.toObject(Bill::class.java)?.copy(id = it.id) }
                        .filter { !existingIds.contains(it.id) }
                    all.addAll(legacyBills)
                }
            }
        }
        return all
    }

    suspend fun migrateBills() {
        val billsSnap = firestore.collection(Bill.COLLECTION_NAME).get().await()
        val suppliersSnap = firestore.collection("suppliers").get().await()
        val suppliersMap = suppliersSnap.documents.associate { (it.getString("name") ?: "").trim().lowercase() to it.id }

        val billsToUpdate = billsSnap.documents.filter { doc ->
            (doc.getString("supplierId") ?: "").isEmpty() && (doc.getString("supplier") ?: "").isNotEmpty()
        }

        if (billsToUpdate.isEmpty()) return

        billsToUpdate.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                val supplierName = (doc.getString("supplier") ?: "").trim().lowercase()
                suppliersMap[supplierName]?.let { sid ->
                    batch.update(doc.reference, "supplierId", sid)
                }
            }
            batch.commit().await()
        }
    }

    suspend fun updateBill(bill: Bill) {
        val billRef = firestore.collection(Bill.COLLECTION_NAME).document(bill.id)
        
        // Find linked entries outside the transaction
        val bankTransactions = firestore.collection(com.batterysales.data.models.BankTransaction.COLLECTION_NAME)
            .whereEqualTo("billId", bill.id)
            .get().await()
            
        val treasuryTransactions = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", bill.id)
            .get().await()

        var oldSupplierId: String? = null
        firestore.runTransaction { transaction ->
            // 1. Reads
            val oldSnap = transaction.get(billRef)
            val oldBill = oldSnap.toObject(Bill::class.java) ?: return@runTransaction
            oldSupplierId = oldBill.supplierId
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            
            val snapshots = summaryRepository.getSummarySnapshots(transaction, listOf(bill.warehouseId ?: "global"))

            // 2. Calculate Differences
            val amountDiff = bill.amount - oldBill.amount
            val supplierIdChanged = oldBill.supplierId != bill.supplierId

            // 3. Writes
            // Clear caches
            if (bill.supplierId.isNotEmpty()) summaryRepository.invalidateSupplierReportCache(transaction, bill.supplierId)
            if (supplierIdChanged && oldBill.supplierId.isNotEmpty()) summaryRepository.invalidateSupplierReportCache(transaction, oldBill.supplierId)

            // Update the Bill document
            val finalUpdates = mutableMapOf<String, Any>(
                "description" to bill.description,
                "amount" to bill.amount,
                "dueDate" to bill.dueDate,
                "billType" to bill.billType,
                "referenceNumber" to bill.referenceNumber,
                "supplierId" to bill.supplierId,
                "updatedAt" to Date()
            )
            
            // Recalculate status if amount changed
            if (bill.amount != oldBill.amount) {
                val newStatus = when {
                    oldBill.paidAmount >= bill.amount -> BillStatus.PAID
                    oldBill.paidAmount > 0 -> BillStatus.PARTIAL
                    else -> BillStatus.UNPAID
                }
                finalUpdates["status"] = newStatus
            }
            transaction.update(billRef, finalUpdates)

            // --- Propagate to Bank Transactions ---
            bankTransactions.documents.forEach { doc ->
                val updates = mutableMapOf<String, Any>(
                    "description" to "دفعة مورد (${if(bill.billType == BillType.CHECK) "شيك" else "تحويل"}): ${bill.description}",
                    "referenceNumber" to bill.referenceNumber,
                    "date" to (bill.dueDate ?: Date())
                )
                // For Transfer, the bank amount is the bill amount
                if (bill.billType == BillType.TRANSFER) {
                    updates["amount"] = bill.amount
                    val bankDiff = bill.amount - (doc.getDouble("amount") ?: 0.0)
                    if (Math.abs(bankDiff) > 0.001) {
                        transaction.update(statsRef, "totalBankBalance", com.google.firebase.firestore.FieldValue.increment(-bankDiff))
                        summaryRepository.applyFinancialUpdate(transaction, snapshots, bill.warehouseId ?: "global", bankChange = -bankDiff)
                    }
                }
                transaction.update(doc.reference, updates)
            }

            // Adjust Unpaid commitments in stats if amount changed
            if (bill.billType == BillType.CHECK || bill.billType == BillType.BILL) {
                val oldRemaining = oldBill.amount - oldBill.paidAmount
                val newRemaining = bill.amount - oldBill.paidAmount
                val diff = newRemaining - oldRemaining
                if (Math.abs(diff) > 0.001) {
                    val field = if (bill.billType == BillType.CHECK) "totalUnpaidChecks" else "totalUnpaidBills"
                    transaction.update(statsRef, field, com.google.firebase.firestore.FieldValue.increment(diff))
                }
            }

            // --- Propagate to Treasury Transactions ---
            treasuryTransactions.documents.forEach { doc ->
                val updates = mutableMapOf<String, Any>(
                    "description" to "دفعة مورد: ${bill.description}",
                    "referenceNumber" to bill.referenceNumber,
                    "createdAt" to (bill.dueDate ?: Date())
                )
                if (bill.billType == BillType.CASH) {
                    updates["amount"] = bill.amount
                    val cashDiff = bill.amount - (doc.getDouble("amount") ?: 0.0)
                    if (Math.abs(cashDiff) > 0.001) {
                        transaction.update(statsRef, "totalCashBalance", com.google.firebase.firestore.FieldValue.increment(-cashDiff))
                        summaryRepository.applyFinancialUpdate(transaction, snapshots, bill.warehouseId ?: "global", cashChange = -cashDiff)
                    }
                }
                transaction.update(doc.reference, updates)
            }

            // --- Update Global Balances & Supplier Debt ---
            if (Math.abs(amountDiff) > 0.001 || supplierIdChanged) {
                // Adjust totalSupplierDebt in SystemStats
                transaction.update(statsRef, "totalSupplierDebt", com.google.firebase.firestore.FieldValue.increment(-amountDiff))
                
                // Adjust Supplier Balances
                if (!supplierIdChanged) {
                    if (bill.supplierId.isNotEmpty()) {
                        val sRef = firestore.collection("suppliers").document(bill.supplierId)
                        transaction.update(sRef, mapOf(
                            "totalCredit" to com.google.firebase.firestore.FieldValue.increment(amountDiff),
                            "currentBalance" to com.google.firebase.firestore.FieldValue.increment(-amountDiff)
                        ))
                    }
                } else {
                    // Full swap
                    if (oldBill.supplierId.isNotEmpty()) {
                        val oldSRef = firestore.collection("suppliers").document(oldBill.supplierId)
                        transaction.update(oldSRef, mapOf(
                            "totalCredit" to com.google.firebase.firestore.FieldValue.increment(-oldBill.amount),
                            "currentBalance" to com.google.firebase.firestore.FieldValue.increment(oldBill.amount)
                        ))
                    }
                    if (bill.supplierId.isNotEmpty()) {
                        val newSRef = firestore.collection("suppliers").document(bill.supplierId)
                        transaction.update(newSRef, mapOf(
                            "totalCredit" to com.google.firebase.firestore.FieldValue.increment(bill.amount),
                            "currentBalance" to com.google.firebase.firestore.FieldValue.increment(-bill.amount)
                        ))
                    }
                }
            }
        }.await()

        if (bill.supplierId.isNotEmpty()) {
            autoLinkBillsForSupplier(bill.supplierId)
        }
        if (!oldSupplierId.isNullOrEmpty() && oldSupplierId != bill.supplierId) {
            autoLinkBillsForSupplier(oldSupplierId!!)
        }
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
            if (b.billType == BillType.CHECK || b.billType == BillType.BILL || b.relatedEntryId != null) b.amount else b.paidAmount
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

        val supplier = firestore.collection("suppliers").document(supplierId).get().await().toObject(Supplier::class.java)
        val supplierNames = if (supplier != null) listOf(supplier.name) else emptyList()

        // 1. Fetch data for calculation (Including legacy name-based entries)
        val allRawEntries = stockEntryRepository.getEntriesBySuppliers(listOf(supplierId), supplierNames)
            .filter { it.status == "approved" }
        if (allRawEntries.isEmpty()) return

        val supplierBills = getBillsBySuppliers(listOf(supplierId), supplierNames)
        
        val supplierReturns = allRawEntries.filter { it.totalCost < 0 }.sortedBy { it.getEffectiveDate() }
        val positiveEntries = allRawEntries.filter { it.totalCost > 0 }
        
        val groupedOrders = positiveEntries.groupBy { it.invoiceNumber.trim().ifEmpty { it.orderId.trim().ifEmpty { it.id } }.lowercase() }
        val orderedGroups = groupedOrders.values.sortedWith(compareBy<List<StockEntry>> { it.first().getEffectiveDate() }.thenBy { it.first().id })

        // --- CALCULATION PHASE (Out of transaction for performance and scale) ---
        val creditSources = (
            supplierBills.filter { 
                it.billType == BillType.CHECK || it.billType == BillType.BILL || it.paidAmount > 0.001 || (it.relatedEntryId != null && it.amount > 0.001)
            }
                .map { b ->
                    val totalAmount = if (b.billType == BillType.CHECK || b.billType == BillType.BILL || b.relatedEntryId != null) b.amount else b.paidAmount
                    val typeLabel = when(b.billType){ BillType.CHECK -> "شيك"; BillType.BILL -> "كمبيالة"; else -> "دفعة" }
                    val fullNote = if (b.referenceNumber.isNotEmpty()) "$typeLabel (#${b.referenceNumber})" else typeLabel
                    CreditSource(
                        id = b.id,
                        type = fullNote,
                        ref = b.referenceNumber,
                        amount = totalAmount,
                        date = b.createdAt ?: Date(0),
                        manualEntryId = b.relatedEntryId,
                        manualAllocation = b.manualAllocation
                    )
                } +
            supplierReturns.map { r ->
                CreditSource(
                    id = r.id,
                    type = "مرتجع مواد",
                    ref = r.invoiceNumber,
                    amount = -r.totalCost,
                    date = r.getEffectiveDate(),
                    manualEntryId = null,
                    manualAllocation = 0.0
                )
            }
        ).sortedWith(compareBy<CreditSource> { it.date }.thenBy { it.id })

        val entryStates = positiveEntries.associate { it.id to MemoryEntryState(it) }.toMutableMap()

        // FIFO Linking
        val activeAutoSources = creditSources.toMutableList()

        orderedGroups.forEach { group ->
            for (entry in group) {
                val state = entryStates[entry.id] ?: continue
                while (state.remainingBalance > 0.001 && activeAutoSources.isNotEmpty()) {
                    val source = activeAutoSources.first()
                    if (source.amount <= 0.001) { activeAutoSources.removeAt(0); continue }
                    
                    val allocation = minOf(state.remainingBalance, source.amount)
                    state.remainingBalance -= allocation
                    state.linkedAllocations[source.id] = (state.linkedAllocations[source.id] ?: 0.0) + allocation
                    state.settlementNotes.add(source.type)
                    activeAutoSources[0] = source.copy(amount = source.amount - allocation)
                }
            }
        }

        // --- WRITE PHASE ---
        // Only update entries that actually CHANGED to save quota and stay within transaction limits
        val changedStates = entryStates.values.filter { state ->
            val isSettled = state.remainingBalance <= 0.001
            val currentRemaining = state.entry.remainingBalance ?: state.entry.getNetCost()
            
            val balanceChanged = Math.abs(state.remainingBalance - currentRemaining) > 0.001
            val statusChanged = isSettled != state.entry.isSettled
            val allocationsChanged = state.linkedAllocations != state.entry.linkedAllocations
            
            // Critical: Distinct notes comparison
            val newNotes = state.settlementNotes.distinct().sorted()
            val oldNotes = state.entry.settlementNotes.distinct().sorted()
            val notesChanged = newNotes != oldNotes

            balanceChanged || statusChanged || allocationsChanged || notesChanged
        }

        val finalUnallocated = Math.max(0.0, activeAutoSources.sumOf { (it.amount as Number).toDouble() })

        // Process updates in chunks of 450 to respect Firestore limits
        if (changedStates.isNotEmpty()) {
            changedStates.chunked(450).forEach { chunk ->
                firestore.runTransaction { transaction ->
                    chunk.forEach { state ->
                        transaction.update(firestore.collection(StockEntry.COLLECTION_NAME).document(state.entry.id), mapOf(
                            "remainingBalance" to state.remainingBalance,
                            "isSettled" to (state.remainingBalance <= 0.001),
                            "settlementNotes" to state.settlementNotes.distinct(),
                            "linkedAllocations" to state.linkedAllocations
                        ))
                    }
                    
                    // Update supplier pool in the same transaction
                    transaction.update(firestore.collection("suppliers").document(supplierId), "unallocatedCredit", finalUnallocated)
                }.await()
            }
        } else {
            // Even if no entries changed, the unallocatedCredit pool might have changed due to a new bill or deletion
            val supplierRef = firestore.collection("suppliers").document(supplierId)
            val currentSupplier = supplierRef.get().await().toObject(Supplier::class.java)
            if (currentSupplier != null && Math.abs((currentSupplier.unallocatedCredit ?: 0.0) - finalUnallocated) > 0.001) {
                supplierRef.update("unallocatedCredit", finalUnallocated).await()
            }
        }
    }

    private data class CreditSource(
        val id: String,
        val type: String,
        val ref: String,
        val amount: Double,
        val date: Date,
        val manualEntryId: String? = null,
        val manualAllocation: Double = 0.0
    )

    private class MemoryEntryState(val entry: StockEntry) {
        var remainingBalance: Double = entry.getNetCost()
        // Aggressively filter out anything that looks like the old technical notes
        // but KEEP notes that have (#number) as they are the new detailed notes
        val settlementNotes: MutableList<String> = entry.settlementNotes.filter { 
            val isOldTechnical = it.contains("JD") || (it.contains("(") && !it.contains("(#"))
            !isOldTechnical
        }.toMutableList()
        val linkedAllocations: MutableMap<String, Double> = mutableMapOf()
    }

    suspend fun getLinkedEntryIds(): Set<String> {
        val snapshot = firestore.collection(Bill.COLLECTION_NAME)
            .whereNotEqualTo("relatedEntryId", null)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.getString("relatedEntryId") }.toSet()
    }

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
 
