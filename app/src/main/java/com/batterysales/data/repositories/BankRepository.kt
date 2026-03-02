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
    private val firestore: FirebaseFirestore
) {
    fun getAllTransactionsFlow(): Flow<List<BankTransaction>> {
        return firestore.collection(BankTransaction.COLLECTION_NAME)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(BankTransaction::class.java)?.copy(id = it.id) }
            }
    }

    suspend fun addTransaction(transaction: BankTransaction) {
        val docRef = firestore.collection(BankTransaction.COLLECTION_NAME).document()
        val finalTransaction = transaction.copy(id = docRef.id)
        docRef.set(finalTransaction).await()
    }

    suspend fun getCurrentBalance(endDate: Long? = null): Double {
        var baseQuery: Query = firestore.collection(BankTransaction.COLLECTION_NAME)
        if (endDate != null) {
            baseQuery = baseQuery.whereLessThanOrEqualTo("date", java.util.Date(endDate + 86400000))
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
            baseQuery = baseQuery.whereGreaterThanOrEqualTo("date", java.util.Date(startDate))
                .whereLessThanOrEqualTo("date", java.util.Date(endDate + 86400000))
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

        if (type != null) {
            query = query.whereEqualTo("type", type)
        }

        if (startDate != null && endDate != null) {
            query = query.whereGreaterThanOrEqualTo("date", java.util.Date(startDate))
                .whereLessThanOrEqualTo("date", java.util.Date(endDate + 86400000))
        }

        query = query.orderBy("date", Query.Direction.DESCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.limit(limit).get().await()
        val transactions = snapshot.documents.mapNotNull { it.toObject(BankTransaction::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(transactions, lastDoc)
    }

    suspend fun deleteTransaction(id: String) {
        firestore.collection(BankTransaction.COLLECTION_NAME)
            .document(id)
            .delete()
            .await()
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
