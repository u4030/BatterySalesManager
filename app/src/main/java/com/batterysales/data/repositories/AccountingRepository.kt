package com.batterysales.data.repositories

import com.batterysales.data.models.Expense
import com.batterysales.data.models.Transaction
import com.batterysales.data.models.TransactionType
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    suspend fun getCurrentBalance(
        warehouseId: String? = null,
        paymentMethod: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): Double {
        var baseQuery: Query = firestore.collection(Transaction.COLLECTION_NAME)
        if (warehouseId != null) {
            baseQuery = baseQuery.whereEqualTo("warehouseId", warehouseId)
        }
        if (paymentMethod != null) {
            baseQuery = baseQuery.whereEqualTo("paymentMethod", paymentMethod)
        }
        if (startDate != null && endDate != null) {
            baseQuery = baseQuery.whereGreaterThanOrEqualTo("createdAt", java.util.Date(startDate))
                .whereLessThanOrEqualTo("createdAt", java.util.Date(endDate + 86400000))
        }

        // Sum INCOME & PAYMENT
        val incomeQuery = baseQuery.whereIn("type", listOf(TransactionType.INCOME.name, TransactionType.PAYMENT.name))
        val incomeSnap = incomeQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await()
        val totalIncome = incomeSnap.getDouble(AggregateField.sum("amount")) ?: 0.0

        // Sum EXPENSE & REFUND
        val expenseQuery = baseQuery.whereIn("type", listOf(TransactionType.EXPENSE.name, TransactionType.REFUND.name))
        val expenseSnap = expenseQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await()
        val totalExpense = expenseSnap.getDouble(AggregateField.sum("amount")) ?: 0.0

        return totalIncome - totalExpense
    }

    suspend fun getTotalExpenses(
        warehouseId: String? = null,
        paymentMethod: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): Double {
        var baseQuery: Query = firestore.collection(Transaction.COLLECTION_NAME)
            .whereIn("type", listOf(TransactionType.EXPENSE.name, TransactionType.REFUND.name))

        if (warehouseId != null) {
            baseQuery = baseQuery.whereEqualTo("warehouseId", warehouseId)
        }
        if (paymentMethod != null) {
            baseQuery = baseQuery.whereEqualTo("paymentMethod", paymentMethod)
        }
        if (startDate != null && endDate != null) {
            baseQuery = baseQuery.whereGreaterThanOrEqualTo("createdAt", java.util.Date(startDate))
                .whereLessThanOrEqualTo("createdAt", java.util.Date(endDate + 86400000))
        }

        val snapshot = baseQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await()
        return snapshot.getDouble(AggregateField.sum("amount")) ?: 0.0
    }

    suspend fun getTransactionsPaginated(
        warehouseId: String? = null,
        paymentMethod: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<Transaction>, DocumentSnapshot?> {
        var query: Query = firestore.collection(Transaction.COLLECTION_NAME)

        if (warehouseId != null) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }
        if (paymentMethod != null) {
            query = query.whereEqualTo("paymentMethod", paymentMethod)
        }

        if (startDate != null && endDate != null) {
            query = query.whereGreaterThanOrEqualTo("createdAt", java.util.Date(startDate))
                .whereLessThanOrEqualTo("createdAt", java.util.Date(endDate + 86400000))
        }

        query = query.orderBy("createdAt", Query.Direction.DESCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        val snapshot = query.limit(limit).get().await()
        val transactions = snapshot.documents.mapNotNull { it.toObject(Transaction::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(transactions, lastDoc)
    }

    suspend fun addTransaction(transaction: Transaction) {
        val docRef = firestore.collection(Transaction.COLLECTION_NAME).document()
        val finalTransaction = transaction.copy(id = docRef.id)
        docRef.set(finalTransaction).await()
    }

    suspend fun addExpense(expense: Expense) {
        val expenseRef = firestore.collection(Expense.COLLECTION_NAME).document()
        val finalExpense = expense.copy(id = expenseRef.id)

        val transactionRef = firestore.collection(Transaction.COLLECTION_NAME).document()
        val transaction = Transaction(
            id = transactionRef.id,
            type = com.batterysales.data.models.TransactionType.EXPENSE,
            amount = expense.amount,
            description = expense.description,
            warehouseId = expense.warehouseId,
            paymentMethod = expense.paymentMethod,
            createdAt = expense.timestamp
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

    suspend fun deleteTransactionsByRelatedId(relatedId: String) {
        val snapshots = firestore.collection(Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", relatedId)
            .get()
            .await()

        if (snapshots.isEmpty) return

        val batch = firestore.batch()
        snapshots.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    suspend fun updateTransactionByRelatedId(relatedId: String, newAmount: Double? = null, newDescription: String? = null) {
        val snapshots = firestore.collection(Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", relatedId)
            .get()
            .await()

        if (snapshots.isEmpty) return

        val batch = firestore.batch()
        snapshots.documents.forEach { doc ->
            val updates = mutableMapOf<String, Any>()
            newAmount?.let { if (it >= 0) updates["amount"] = it }
            newDescription?.let { updates["description"] = it }

            if (updates.isNotEmpty()) {
                batch.update(doc.reference, updates)
            }
        }
        batch.commit().await()
    }
}
