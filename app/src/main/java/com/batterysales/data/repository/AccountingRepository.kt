package com.batterysales.data.repository

import com.batterysales.data.models.Expense
import com.batterysales.data.models.Transaction
import com.batterysales.data.models.TransactionType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountingRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : BaseRepository() {

    suspend fun addExpense(expense: Expense): Result<String> = safeCall {
        val docRef = firestore.collection(Expense.COLLECTION_NAME).document()
        val finalExpense = expense.copy(id = docRef.id)

        firestore.runBatch { batch ->
            batch.set(docRef, finalExpense)

            val transactionRef = firestore.collection(Transaction.COLLECTION_NAME).document()
            val transaction = Transaction(
                id = transactionRef.id,
                type = TransactionType.EXPENSE,
                amount = expense.amount,
                description = expense.description,
                relatedId = finalExpense.id
            )
            batch.set(transactionRef, transaction)
        }.await()

        finalExpense.id
    }

    suspend fun getAllExpenses(): Result<List<Expense>> = safeCall {
        val snapshot = firestore.collection(Expense.COLLECTION_NAME)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        snapshot.toObjectList(Expense::class.java)
    }

    suspend fun getAllTransactions(): Result<List<Transaction>> = safeCall {
        val snapshot = firestore.collection(Transaction.COLLECTION_NAME)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        snapshot.toObjectList(Transaction::class.java)
    }

    suspend fun getCurrentBalance(): Result<Double> = safeCall {
        val transactions = getAllTransactions().getOrThrow()
        val income = transactions.filter { it.type == TransactionType.INCOME || it.type == TransactionType.PAYMENT }.sumOf { it.amount }
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        income - expenses
    }
}
