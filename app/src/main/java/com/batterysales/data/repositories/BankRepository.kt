package com.batterysales.data.repositories

import com.batterysales.data.models.BankTransaction
import com.batterysales.data.models.BankTransactionType
import com.google.firebase.firestore.FirebaseFirestore
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

    suspend fun getCurrentBalance(): Double {
        val transactions = firestore.collection(BankTransaction.COLLECTION_NAME)
            .get()
            .await()
            .documents.mapNotNull { it.toObject(BankTransaction::class.java)?.copy(id = it.id) }

        return transactions.sumOf {
            if (it.type == BankTransactionType.DEPOSIT) it.amount else -it.amount
        }
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
