package com.batterysales.data.repositories

import com.batterysales.data.models.BankTransaction
import com.batterysales.data.models.BankTransactionType
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
            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, "global")

            // 2. Writes
            transactionOp.set(docRef, finalTransaction)
            val change = if (finalTransaction.type == BankTransactionType.DEPOSIT) finalTransaction.amount else -finalTransaction.amount
            summaryRepository.applyFinancialUpdate(transactionOp, snapshots, warehouseId = "global", bankChange = change)
        }.await()

        return docRef.id
    }

    suspend fun updateTransaction(transaction: BankTransaction, forceSystemUpdate: Boolean = false) {
        val docRef = firestore.collection(BankTransaction.COLLECTION_NAME).document(transaction.id)
        firestore.runTransaction { transactionOp ->
            // 1. Reads
            val oldTrans = transactionOp.get(docRef).toObject(BankTransaction::class.java)

            if (oldTrans?.isSystemManaged == true && !forceSystemUpdate) {
                throw Exception("هذا القيد مدار من قبل النظام (فاتورة/شيك)، يرجى تعديله من المصدر لضمان دقة البيانات.")
            }

            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, "global")

            // 2. Writes
            transactionOp.set(docRef, transaction)
            
            val oldChange = if (oldTrans?.type == BankTransactionType.DEPOSIT) -(oldTrans.amount) else (oldTrans?.amount ?: 0.0)
            val newChange = if (transaction.type == BankTransactionType.DEPOSIT) transaction.amount else -transaction.amount
            
            summaryRepository.applyFinancialUpdate(transactionOp, snapshots, warehouseId = "global", bankChange = oldChange + newChange)
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

    suspend fun getTotalWithdrawals(startDate: Long? = null, endDate: Long? = null): Double {
        var baseQuery: Query = firestore.collection(BankTransaction.COLLECTION_NAME)
            .whereEqualTo("type", BankTransactionType.WITHDRAWAL.name)
            
        if (startDate != null && endDate != null) {
            baseQuery = baseQuery.whereGreaterThanOrEqualTo("date", java.util.Date(com.batterysales.utils.DateUtils.getStartOfDay(startDate)))
                .whereLessThanOrEqualTo("date", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(endDate)))
        }

        val snapshot = baseQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await()
        return (snapshot.get(AggregateField.sum("amount")) as? Number)?.toDouble() ?: 0.0
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
        firestore.runTransaction { transactionOp ->
            // 1. Reads
            val oldTrans = transactionOp.get(docRef).toObject(BankTransaction::class.java)

            if (oldTrans?.isSystemManaged == true && !forceSystemUpdate) {
                throw Exception("هذا القيد مدار من قبل النظام (فاتورة/شيك)، يرجى حذفه من المصدر لضمان دقة البيانات.")
            }
            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, "global")

            // 2. Writes
            transactionOp.delete(docRef)
            
            if (oldTrans != null) {
                val oldChange = if (oldTrans.type == BankTransactionType.DEPOSIT) -(oldTrans.amount) else oldTrans.amount
                
                if (snapshots.financialStatus != null) {
                    summaryRepository.applyFinancialUpdate(transactionOp, snapshots, warehouseId = "global", bankChange = oldChange)
                } else {
                    // Force update if summary is missing (Summary system init)
                    summaryRepository.incrementSyncVersion(transactionOp, "financial")
                }
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
