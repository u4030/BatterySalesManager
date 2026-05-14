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
            transaction.set(docRef, finalBill)
            
            // Update Supplier Denormalized Totals (Credit only on creation/payment)
            if (finalBill.supplierId.isNotEmpty()) {
                val supplierRef = firestore.collection("suppliers").document(finalBill.supplierId)
                // Add the paid amount directly to unallocated pool for FIFO
                if (finalBill.paidAmount > 0) {
                    transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(finalBill.paidAmount))
                    transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-finalBill.paidAmount))
                    transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(finalBill.paidAmount))
                }
            }
        }.await()
        
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
            val snapshot = transaction.get(billRef)
            val bill = snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id) ?: return@runTransaction

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

            // Update Summaries
            summaryRepository.updateFinancialStatus(
                transaction = transaction,
                warehouseId = "global",
                cashChange = -paymentAmount,
                billChange = -paymentAmount
            )

            // Update Supplier Denormalized Totals
            if (bill.supplierId.isNotEmpty()) {
                val supplierRef = firestore.collection("suppliers").document(bill.supplierId)
                transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
                transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-paymentAmount))
                // Add to unallocated pool for FIFO processing
                transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
            }

            // Update Global System Stats
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            transaction.update(statsRef, mapOf(
                "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(-paymentAmount),
                "updatedAt" to java.util.Date()
            ))
        }.await()
    }

    suspend fun addBillPayment(bill: Bill, paymentAmount: Double, method: String, warehouseId: String, notes: String) {
        val billRef = firestore.collection(Bill.COLLECTION_NAME).document(bill.id)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(billRef)
            val freshBill = snapshot.toObject(Bill::class.java)?.copy(id = snapshot.id) ?: return@runTransaction

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

            // Update Summaries
            summaryRepository.updateFinancialStatus(
                transaction = transaction,
                warehouseId = warehouseId,
                cashChange = if (method == "cash") -paymentAmount else 0.0,
                bankChange = if (method == "bank") -paymentAmount else 0.0,
                billChange = -paymentAmount
            )

            // Update Supplier Denormalized Totals
            if (freshBill.supplierId.isNotEmpty()) {
                val supplierRef = firestore.collection("suppliers").document(freshBill.supplierId)
                transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
                transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-paymentAmount))
                transaction.update(supplierRef, "unallocatedCredit", com.google.firebase.firestore.FieldValue.increment(paymentAmount))
            }

            // Update Global System Stats
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
            transaction.update(statsRef, mapOf(
                "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(-paymentAmount),
                "updatedAt" to java.util.Date()
            ))
        }.await()
    }

    suspend fun deleteBill(billId: String) {
        firestore.collection(Bill.COLLECTION_NAME)
            .document(billId)
            .delete()
            .await()
    }

    suspend fun getBillsPaginated(
        searchQuery: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<Bill>, DocumentSnapshot?> {
        var query: Query = firestore.collection(Bill.COLLECTION_NAME)

        if (!searchQuery.isNullOrBlank()) {
            // Server-side search by referenceNumber (most common)
            query = query.whereGreaterThanOrEqualTo("referenceNumber", searchQuery)
                .whereLessThanOrEqualTo("referenceNumber", searchQuery + "\uf8ff")
                .orderBy("referenceNumber", Query.Direction.ASCENDING)
        } else {
            // Default sorting by supplierId (ASC) and then dueDate (ASC)
            query = query.orderBy("supplierId", Query.Direction.ASCENDING)
                .orderBy("dueDate", Query.Direction.ASCENDING)
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
        val updates = mutableMapOf<String, Any>(
            "description" to bill.description,
            "amount" to bill.amount,
            "dueDate" to bill.dueDate,
            "billType" to bill.billType,
            "referenceNumber" to bill.referenceNumber,
            "supplierId" to bill.supplierId,
            "updatedAt" to Date()
        )
        firestore.collection(Bill.COLLECTION_NAME)
            .document(bill.id)
            .update(updates)
            .await()
    }

    /**
     * Refactored Incremental FIFO for Grouped Orders:
     * Treats grouped StockEntries (Invoices) as single entities to ensure accurate balance tracking.
     */
    suspend fun autoLinkBillsForSupplier(supplierId: String, resetDate: Date? = null) {
        if (supplierId.isEmpty()) return

        // 1. Get ALL unsettled entries for this supplier to group them correctly
        // (Efficiency: we only get unsettled ones, still better than full history)
        val ordersQuery = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("supplierId", supplierId)
            .whereEqualTo("status", "approved")
            .whereEqualTo("isSettled", false)
            .orderBy("invoiceDate", Query.Direction.ASCENDING)
            .orderBy("timestamp", Query.Direction.ASCENDING)
        
        val entrySnapshots = ordersQuery.get().await()
        if (entrySnapshots.isEmpty) return

        val allEntries = entrySnapshots.documents.mapNotNull { it.toObject(StockEntry::class.java)?.copy(id = it.id) }
        
        // Group entries by Invoice/OrderId
        val groupedEntries = allEntries.groupBy { it.invoiceNumber.trim().ifEmpty { it.orderId.trim().ifEmpty { it.id } } }
        
        // Order groups by effective date
        val orderedGroups = groupedEntries.values.sortedBy { it.first().getEffectiveDate() }

        firestore.runTransaction { transaction ->
            // 2. Get Supplier's Unallocated Credit
            val supplierRef = firestore.collection("suppliers").document(supplierId)
            val supplierSnap = transaction.get(supplierRef)
            val supplier = supplierSnap.toObject(Supplier::class.java)
            
            var unallocatedPool = supplier?.unallocatedCredit ?: 0.0
            if (unallocatedPool <= 0.001) return@runTransaction

            // 3. Process FIFO per GROUP
            orderedGroups.forEach { group ->
                if (unallocatedPool <= 0.001) return@forEach

                val representative = group.first()
                val groupTotalCost = group.sumOf { it.getNetCost() }
                
                // Track remaining balance for the entire group
                // Note: In denormalized mode, remainingBalance is stored on EVERY document in the group
                // to make reports easier, but it represents the ENTIRE group balance.
                val currentGroupBalance = representative.remainingBalance ?: groupTotalCost
                if (currentGroupBalance <= 0.001) {
                    group.forEach { doc -> transaction.update(firestore.collection(StockEntry.COLLECTION_NAME).document(doc.id), "isSettled", true) }
                    return@forEach
                }

                val allocation = minOf(unallocatedPool, currentGroupBalance)
                val newGroupBalance = currentGroupBalance - allocation
                unallocatedPool -= allocation

                val notes = representative.settlementNotes.toMutableList()
                if (allocation > 0.001) {
                    notes.add("تسوية تلقائية: JD ${String.format("%.3f", allocation)}")
                }

                val updates = mutableMapOf<String, Any>(
                    "remainingBalance" to newGroupBalance,
                    "isSettled" to (newGroupBalance <= 0.001),
                    "settlementNotes" to notes.distinct()
                )
                
                // Apply update to ALL documents in the group to keep them synchronized
                group.forEach { doc ->
                    transaction.update(firestore.collection(StockEntry.COLLECTION_NAME).document(doc.id), updates)
                }
            }

            // 4. Update the supplier pool
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
