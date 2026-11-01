package com.batterysales.data.repositories

import com.batterysales.data.models.BankTransaction
import com.batterysales.data.models.BankTransactionType
import com.batterysales.data.models.SystemStats
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class BankRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val summaryRepository: SummaryRepository
) {
    /**
     * Warning: Dangerous broad listener. Use getTransactionsPaginated instead.
     */
    fun getAllTransactionsFlow(limit: Long = 1000): Flow<List<BankTransaction>> {
        return firestore.collection(BankTransaction.COLLECTION_NAME)
            .limit(limit)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(BankTransaction::class.java)?.copy(id = it.id) }
            }
    }

    suspend fun addTransaction(transaction: BankTransaction): String {
        val docRef = if (transaction.id.isEmpty()) firestore.collection(BankTransaction.COLLECTION_NAME).document()
        else firestore.collection(BankTransaction.COLLECTION_NAME).document(transaction.id)
        val finalTransaction = transaction.copy(id = docRef.id)

        firestore.runTransaction { transactionOp ->
            // 1. Reads
            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, listOf("global"))
            val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)

            // 2. Writes
            transactionOp.set(docRef, finalTransaction)
            val change = if (finalTransaction.type == BankTransactionType.DEPOSIT) finalTransaction.amount else -finalTransaction.amount
            
            summaryRepository.applyFinancialUpdate(transactionOp, snapshots, warehouseId = "global", bankChange = change)
            
            // Update Global Stats
            transactionOp.set(statsRef, mapOf("totalBankBalance" to com.google.firebase.firestore.FieldValue.increment(change)), com.google.firebase.firestore.SetOptions.merge())
        }.await()

        return docRef.id
    }

    suspend fun updateTransaction(transaction: BankTransaction, forceSystemUpdate: Boolean = false) {
        val docRef = firestore.collection(BankTransaction.COLLECTION_NAME).document(transaction.id)
        
        // Find linked treasury transaction (where relatedId == transaction.id)
        val linkedTreasurySnap = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", transaction.id).get().await()

        // Check if linked to a Bill or Payment via billId
        val billRef = if (!transaction.billId.isNullOrEmpty()) firestore.collection(com.batterysales.data.models.Bill.COLLECTION_NAME).document(transaction.billId) else null
        val paymentRef = if (!transaction.billId.isNullOrEmpty()) firestore.collection(com.batterysales.data.models.Payment.COLLECTION_NAME).document(transaction.billId) else null

        firestore.runTransaction { transactionOp ->
            // 1. Reads
            val oldTrans = transactionOp.get(docRef).toObject(BankTransaction::class.java)

            if (oldTrans?.isSystemManaged == true && !forceSystemUpdate) {
                throw Exception("هذا القيد مدار من قبل النظام (فاتورة/شيك)، يرجى تعديله من المصدر لضمان دقة البيانات.")
            }

            val billSnap = billRef?.let { transactionOp.get(it) }
            val paymentSnap = paymentRef?.let { transactionOp.get(it) }
            val oldPayment = paymentSnap?.toObject(com.batterysales.data.models.Payment::class.java)

            val invRef = oldPayment?.let { firestore.collection(com.batterysales.data.models.Invoice.COLLECTION_NAME).document(it.invoiceId) }
            val invSnap = invRef?.let { transactionOp.get(it) }

            val warehouseIds = mutableListOf("global")
            linkedTreasurySnap.documents.forEach { doc ->
                doc.getString("warehouseId")?.let { if (it.isNotBlank()) warehouseIds.add(it) }
            }

            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, warehouseIds.distinct())
            val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)

            // 2. Writes
            transactionOp.set(docRef, transaction)
            
            val oldChange = if (oldTrans?.type == BankTransactionType.DEPOSIT) -(oldTrans.amount) else (oldTrans?.amount ?: 0.0)
            val newChange = if (transaction.type == BankTransactionType.DEPOSIT) transaction.amount else -transaction.amount
            val totalBankChange = oldChange + newChange

            var checkChangeDelta = 0.0
            var billChangeDelta = 0.0

            // If linked to a Bill (Check or Promissory note)
            if (billSnap != null && billSnap.exists()) {
                val oldBill = billSnap.toObject(com.batterysales.data.models.Bill::class.java)
                if (oldBill != null) {
                    val amountDiff = transaction.amount - (oldTrans?.amount ?: 0.0)
                    val newPaid = oldBill.paidAmount + amountDiff
                    val newStatus = when {
                        newPaid >= oldBill.amount -> com.batterysales.data.models.BillStatus.PAID
                        newPaid > 0 -> com.batterysales.data.models.BillStatus.PARTIAL
                        else -> com.batterysales.data.models.BillStatus.UNPAID
                    }
                    transactionOp.update(billRef, mapOf(
                        "paidAmount" to newPaid,
                        "status" to newStatus,
                        "updatedAt" to java.util.Date()
                    ))

                    if (oldBill.billType == com.batterysales.data.models.BillType.CHECK) {
                        checkChangeDelta = -amountDiff
                        transactionOp.set(statsRef, mapOf("totalUnpaidChecks" to com.google.firebase.firestore.FieldValue.increment(-amountDiff)), com.google.firebase.firestore.SetOptions.merge())
                    } else if (oldBill.billType == com.batterysales.data.models.BillType.BILL) {
                        billChangeDelta = -amountDiff
                        transactionOp.set(statsRef, mapOf("totalUnpaidBills" to com.google.firebase.firestore.FieldValue.increment(-amountDiff)), com.google.firebase.firestore.SetOptions.merge())
                    }
                }
            }

            // If linked to a Payment (Invoice payment)
            if (oldPayment != null && paymentRef != null && invRef != null && invSnap != null) {
                val amountDiff = transaction.amount - oldPayment.amount
                transactionOp.update(paymentRef, mapOf(
                    "amount" to transaction.amount,
                    "paymentDate" to (transaction.date ?: java.util.Date())
                ))

                val oldInv = invSnap.toObject(com.batterysales.data.models.Invoice::class.java)
                if (oldInv != null) {
                    val newPaid = oldInv.paidAmount + amountDiff
                    val newRem = oldInv.totalAmount - newPaid
                    transactionOp.update(invRef, mapOf(
                        "paidAmount" to newPaid,
                        "remainingAmount" to newRem,
                        "status" to (if (newRem <= 0.001) "paid" else "pending"),
                        "updatedAt" to java.util.Date()
                    ))
                }
                transactionOp.set(statsRef, mapOf("totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(-amountDiff)), com.google.firebase.firestore.SetOptions.merge())
            }

            val financialDeltas = mutableMapOf<String, SummaryRepository.FinancialDelta>()
            financialDeltas["global"] = SummaryRepository.FinancialDelta(
                bankChange = totalBankChange,
                checkChange = checkChangeDelta,
                billChange = billChangeDelta
            )

            // Update Global Stats for bank balance
            transactionOp.set(statsRef, mapOf("totalBankBalance" to com.google.firebase.firestore.FieldValue.increment(totalBankChange)), com.google.firebase.firestore.SetOptions.merge())

            // Sync linked treasury transaction if exists
            linkedTreasurySnap.documents.forEach { doc ->
                val oldTreasury = doc.toObject(com.batterysales.data.models.Transaction::class.java)
                val oldAmount = oldTreasury?.amount ?: 0.0
                val amountDiff = transaction.amount - oldAmount
                val targetWh = oldTreasury?.warehouseId ?: "global"

                transactionOp.update(doc.reference, mapOf(
                    "amount" to transaction.amount,
                    "description" to "تغذية رصيد بنك: ${transaction.description}",
                    "createdAt" to (transaction.date ?: java.util.Date())
                ))

                if (Math.abs(amountDiff) > 0.001 && oldTreasury != null) {
                    val treasuryChange = if (oldTreasury.type == com.batterysales.data.models.TransactionType.INCOME) amountDiff else -amountDiff
                    val existingDelta = financialDeltas[targetWh] ?: SummaryRepository.FinancialDelta()
                    financialDeltas[targetWh] = existingDelta.copy(cashChange = existingDelta.cashChange + treasuryChange)
                    transactionOp.set(statsRef, mapOf("totalCashBalance" to com.google.firebase.firestore.FieldValue.increment(treasuryChange)), com.google.firebase.firestore.SetOptions.merge())
                }
            }

            // Single atomic summary update to prevent overwriting financial_status
            summaryRepository.applyFinancialUpdates(transactionOp, snapshots, financialDeltas)
        }.await()
    }

    suspend fun getCurrentBalance(endDate: Long? = null): Double {
        var baseQuery: Query = firestore.collection(BankTransaction.COLLECTION_NAME)
        if (endDate != null) {
            baseQuery = baseQuery.whereLessThanOrEqualTo("date", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(endDate)))
        }

        // Sum DEPOSIT
        val depositQuery = baseQuery.whereEqualTo("type", BankTransactionType.DEPOSIT.name)
        val depositSnap = depositQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await()
        val totalDeposit = (depositSnap.get(AggregateField.sum("amount")) as? Number)?.toDouble() ?: 0.0

        // Sum WITHDRAWAL
        val withdrawalQuery = baseQuery.whereEqualTo("type", BankTransactionType.WITHDRAWAL.name)
        val withdrawalSnap = withdrawalQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await()
        val totalWithdrawal = (withdrawalSnap.get(AggregateField.sum("amount")) as? Number)?.toDouble() ?: 0.0

        return totalDeposit - totalWithdrawal
    }

    private fun cleanDouble(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        val bd = java.math.BigDecimal.valueOf(value).setScale(3, java.math.RoundingMode.HALF_UP)
        val res = bd.toDouble()
        return if (Math.abs(res) < 0.0001) 0.0 else res
    }

    suspend fun getTotalWithdrawals(startDate: Long? = null, endDate: Long? = null): Double {
        return try {
            val snap = firestore.collection(BankTransaction.COLLECTION_NAME).get().await()
            val withdrawals = snap.documents.mapNotNull { it.toObject(BankTransaction::class.java) }
                .filter { trans ->
                    val isWithdrawal = trans.type == BankTransactionType.WITHDRAWAL || trans.type.name.equals("WITHDRAWAL", ignoreCase = true)
                    val time = trans.date?.time ?: 0L
                    val startMatch = startDate == null || time >= com.batterysales.utils.DateUtils.getStartOfDay(startDate)
                    val endMatch = endDate == null || time <= com.batterysales.utils.DateUtils.getEndOfDay(endDate)
                    isWithdrawal && startMatch && endMatch
                }
            cleanDouble(withdrawals.sumOf { it.amount })
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun getTransactionsPaginated(
        startDate: Long? = null,
        endDate: Long? = null,
        type: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<BankTransaction>, DocumentSnapshot?> {
        var query: Query = firestore.collection(BankTransaction.COLLECTION_NAME)

        if (!type.isNullOrEmpty()) {
            // Support both Enum name and potentially lowercase if stored that way
            query = query.whereIn("type", listOf(type, type.lowercase(), type.uppercase()).distinct())
        }

        if (startDate != null && endDate != null) {
            query = query.whereGreaterThanOrEqualTo("date", java.util.Date(com.batterysales.utils.DateUtils.getStartOfDay(startDate)))
                .whereLessThanOrEqualTo("date", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(endDate)))
        }

        // Add index-safe ordering
        query = query.orderBy("date", Query.Direction.DESCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.limit(limit).get().await()
        val transactions = snapshot.documents.mapNotNull { it.toObject(BankTransaction::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(transactions, lastDoc)
    }

    suspend fun deleteTransaction(id: String, forceSystemUpdate: Boolean = false) {
        val docRef = firestore.collection(BankTransaction.COLLECTION_NAME).document(id)
        val linkedTreasurySnap = firestore.collection(com.batterysales.data.models.Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", id).get().await()

        firestore.runTransaction { transactionOp ->
            // 1. Reads
            val oldTrans = transactionOp.get(docRef).toObject(BankTransaction::class.java)

            if (oldTrans?.isSystemManaged == true && !forceSystemUpdate) {
                throw Exception("هذا القيد مدار من قبل النظام (فاتورة/شيك)، يرجى حذفه من المصدر لضمان دقة البيانات.")
            }

            val billRef = if (!oldTrans?.billId.isNullOrEmpty()) firestore.collection(com.batterysales.data.models.Bill.COLLECTION_NAME).document(oldTrans!!.billId) else null
            val paymentRef = if (!oldTrans?.billId.isNullOrEmpty()) firestore.collection(com.batterysales.data.models.Payment.COLLECTION_NAME).document(oldTrans!!.billId) else null

            val billSnap = billRef?.let { transactionOp.get(it) }
            val paymentSnap = paymentRef?.let { transactionOp.get(it) }

            val warehouseIds = mutableListOf("global")
            linkedTreasurySnap.documents.forEach { doc ->
                doc.getString("warehouseId")?.let { if (it.isNotBlank()) warehouseIds.add(it) }
            }

            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, warehouseIds.distinct())
            val statsRef = firestore.collection(SystemStats.COLLECTION_NAME).document(SystemStats.DOCUMENT_ID)

            // 2. Writes
            transactionOp.delete(docRef)
            
            if (oldTrans != null) {
                val bankChange = cleanDouble(if (oldTrans.type == BankTransactionType.DEPOSIT) -(oldTrans.amount) else oldTrans.amount)
                var checkChangeDelta = 0.0
                var billChangeDelta = 0.0

                // Reverse linked Bill (Check/Promissory note)
                if (billSnap != null && billSnap.exists()) {
                    val oldBill = billSnap.toObject(com.batterysales.data.models.Bill::class.java)
                    if (oldBill != null) {
                        val amountToRevert = oldTrans.amount
                        val newPaid = cleanDouble(Math.max(0.0, oldBill.paidAmount - amountToRevert))
                        val newStatus = when {
                            newPaid >= oldBill.amount -> com.batterysales.data.models.BillStatus.PAID
                            newPaid > 0 -> com.batterysales.data.models.BillStatus.PARTIAL
                            else -> com.batterysales.data.models.BillStatus.UNPAID
                        }
                        transactionOp.update(billRef, mapOf(
                            "paidAmount" to newPaid,
                            "status" to newStatus,
                            "updatedAt" to java.util.Date()
                        ))

                        if (oldBill.billType == com.batterysales.data.models.BillType.CHECK) {
                            checkChangeDelta = amountToRevert
                            transactionOp.set(statsRef, mapOf("totalUnpaidChecks" to com.google.firebase.firestore.FieldValue.increment(amountToRevert)), com.google.firebase.firestore.SetOptions.merge())
                        } else if (oldBill.billType == com.batterysales.data.models.BillType.BILL) {
                            billChangeDelta = amountToRevert
                            transactionOp.set(statsRef, mapOf("totalUnpaidBills" to com.google.firebase.firestore.FieldValue.increment(amountToRevert)), com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
                }

                // Reverse linked Payment (Invoice payment)
                if (paymentSnap != null && paymentSnap.exists()) {
                    val oldPayment = paymentSnap.toObject(com.batterysales.data.models.Payment::class.java)
                    if (oldPayment != null) {
                        transactionOp.delete(paymentRef!!)
                        val invRef = firestore.collection(com.batterysales.data.models.Invoice.COLLECTION_NAME).document(oldPayment.invoiceId)
                        val invSnap = transactionOp.get(invRef)
                        val oldInv = invSnap.toObject(com.batterysales.data.models.Invoice::class.java)
                        if (oldInv != null) {
                            val newPaid = cleanDouble(Math.max(0.0, oldInv.paidAmount - oldPayment.amount))
                            val newRem = cleanDouble(oldInv.totalAmount - newPaid)
                            transactionOp.update(invRef, mapOf(
                                "paidAmount" to newPaid,
                                "remainingAmount" to newRem,
                                "status" to (if (newRem <= 0.001) "paid" else "pending"),
                                "updatedAt" to java.util.Date()
                            ))
                        }
                        transactionOp.set(statsRef, mapOf("totalCustomerDebt" to com.google.firebase.firestore.FieldValue.increment(oldPayment.amount)), com.google.firebase.firestore.SetOptions.merge())
                    }
                }

                summaryRepository.applyFinancialUpdate(
                    transaction = transactionOp,
                    snapshots = snapshots,
                    warehouseId = "global",
                    bankChange = bankChange,
                    checkChange = checkChangeDelta,
                    billChange = billChangeDelta
                )
                
                // Update Global Stats
                transactionOp.set(statsRef, mapOf("totalBankBalance" to com.google.firebase.firestore.FieldValue.increment(bankChange)), com.google.firebase.firestore.SetOptions.merge())
            }
        }.await()
    }

    suspend fun deleteTransactionsByBillId(billId: String) {
        val snapshots = firestore.collection(BankTransaction.COLLECTION_NAME)
            .whereEqualTo("billId", billId)
            .get()
            .await()

        if (snapshots.isEmpty) return

        val batch = firestore.batch()
        snapshots.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }
}
 
