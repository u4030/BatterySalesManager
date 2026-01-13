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
        return firestore.collection(Transaction.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(Transaction::class.java)
    }

    suspend fun getAllExpenses(): List<Expense> {
        return firestore.collection(Expense.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(Expense::class.java)
    }

    suspend fun getCurrentBalance(): Double {
        val transactions = getAllTransactions()
        val expenses = getAllExpenses()
        return transactions.sumOf { it.amount } - expenses.sumOf { it.amount }
    }

    suspend fun addExpense(expense: Expense) {
        firestore.collection(Expense.COLLECTION_NAME)
            .add(expense)
            .await()
    }
}
