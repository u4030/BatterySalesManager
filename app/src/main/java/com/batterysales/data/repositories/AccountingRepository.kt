package com.batterysales.data.repositories

import com.batterysales.data.models.Expense
import com.batterysales.data.models.Transaction
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AccountingRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getAllTransactions(): List<Transaction> {
        val snapshot = firestore.collection(Transaction.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Transaction::class.java)?.copy(id = it.id) }
    }

    suspend fun getAllExpenses(): List<Expense> {
        val snapshot = firestore.collection(Expense.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Expense::class.java)?.copy(id = it.id) }
    }

    suspend fun getCurrentBalance(): Double {
        val transactions = getAllTransactions()
        // We unify expenses into transactions, but for legacy support we check if an expense is already a transaction
        // Actually, to keep it simple and fix the user's issue:
        return transactions.sumOf {
            when (it.type) {
                com.batterysales.data.models.TransactionType.INCOME,
                com.batterysales.data.models.TransactionType.PAYMENT -> it.amount
                com.batterysales.data.models.TransactionType.EXPENSE,
                com.batterysales.data.models.TransactionType.REFUND -> -it.amount
            }
        }
    }

    suspend fun addTransaction(transaction: Transaction) {
        val docRef = firestore.collection(Transaction.COLLECTION_NAME).document()
        val finalTransaction = transaction.copy(id = docRef.id)
        docRef.set(finalTransaction).await()
    }

    suspend fun addExpense(expense: Expense) {
        val expenseRef = firestore.collection(Expense.COLLECTION_NAME).document()
        val transactionRef = firestore.collection(Transaction.COLLECTION_NAME).document()

        val finalExpense = expense.copy(id = expenseRef.id)

        // Also add as a transaction for consistency
        val transaction = Transaction(
            id = transactionRef.id,
            type = com.batterysales.data.models.TransactionType.EXPENSE,
            amount = expense.amount,
            description = expense.description,
            createdAt = expense.timestamp,
            relatedId = expenseRef.id
        )

        firestore.runBatch { batch ->
            batch.set(expenseRef, finalExpense)
            batch.set(transactionRef, transaction)
        }.await()
    }

    suspend fun updateTransaction(transaction: Transaction) {
        firestore.collection(Transaction.COLLECTION_NAME)
            .document(transaction.id)
            .set(transaction)
            .await()
    }

    suspend fun deleteTransaction(transactionId: String) {
        firestore.collection(Transaction.COLLECTION_NAME)
            .document(transactionId)
            .delete()
            .await()
    }
}
