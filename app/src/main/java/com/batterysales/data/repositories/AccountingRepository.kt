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
    private val firestore: FirebaseFirestore,
    private val summaryRepository: SummaryRepository
) {

    suspend fun getAllTransactions(): List<Transaction> {
        val snapshot = firestore.collection(Transaction.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Transaction::class.java)?.copy(id = it.id) }
    }

    /**
     * Warning: Fetches the ENTIRE collection.
     */
    suspend fun getAllExpenses(limit: Long = 1000): List<Expense> {
        val snapshot = firestore.collection(Expense.COLLECTION_NAME)
            .limit(limit)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Expense::class.java)?.copy(id = it.id) }
    }

    suspend fun getCurrentBalance(
        warehouseId: String? = null,
        paymentMethod: String? = null,
        endDate: Long? = null
    ): Double {
        var baseQuery: Query = firestore.collection(Transaction.COLLECTION_NAME)
        if (warehouseId != null) {
            baseQuery = baseQuery.whereEqualTo("warehouseId", warehouseId)
        }
        if (paymentMethod != null) {
            baseQuery = baseQuery.whereEqualTo("paymentMethod", paymentMethod)
        }
        if (endDate != null) {
            baseQuery = baseQuery.whereLessThanOrEqualTo("createdAt", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(endDate)))
        }

        // Robust type matching (handle mixed casing and multiple types)
        val incomeTypes = listOf("INCOME", "PAYMENT", "income", "payment", "Income", "Payment")
        val expenseTypes = listOf("EXPENSE", "REFUND", "expense", "refund", "Expense", "Refund")

        // Sum INCOME & PAYMENT
        val incomeQuery = baseQuery.whereIn("type", incomeTypes)
        val incomeSnap = try { incomeQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await() } catch(e: Exception) { null }
        val totalIncome = (incomeSnap?.get(AggregateField.sum("amount")) as? Number)?.toDouble() ?: 0.0

        // Sum EXPENSE & REFUND
        val expenseQuery = baseQuery.whereIn("type", expenseTypes)
        val expenseSnap = try { expenseQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await() } catch(e: Exception) { null }
        val totalExpense = (expenseSnap?.get(AggregateField.sum("amount")) as? Number)?.toDouble() ?: 0.0

        return totalIncome - totalExpense
    }

    suspend fun getTotalExpenses(
        warehouseId: String? = null,
        paymentMethod: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): Double {
        val expenseTypes = listOf("EXPENSE", "REFUND", "expense", "refund", "Expense", "Refund")
        var baseQuery: Query = firestore.collection(Transaction.COLLECTION_NAME)
            .whereIn("type", expenseTypes)

        if (warehouseId != null) {
            baseQuery = baseQuery.whereEqualTo("warehouseId", warehouseId)
        }
        if (paymentMethod != null) {
            baseQuery = baseQuery.whereEqualTo("paymentMethod", paymentMethod)
        }
        if (startDate != null && endDate != null) {
            baseQuery = baseQuery.whereGreaterThanOrEqualTo("createdAt", java.util.Date(com.batterysales.utils.DateUtils.getStartOfDay(startDate)))
                .whereLessThanOrEqualTo("createdAt", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(endDate)))
        }

        val snapshot = try { baseQuery.aggregate(AggregateField.sum("amount")).get(AggregateSource.SERVER).await() } catch(e: Exception) { null }
        return (snapshot?.get(AggregateField.sum("amount")) as? Number)?.toDouble() ?: 0.0
    }

    suspend fun getTransactionsPaginated(
        warehouseId: String? = null,
        paymentMethod: String? = null,
        types: List<String>? = null,
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
        if (types != null && types.isNotEmpty()) {
            query = query.whereIn("type", types)
        }

        if (startDate != null && endDate != null) {
            query = query.whereGreaterThanOrEqualTo("createdAt", java.util.Date(com.batterysales.utils.DateUtils.getStartOfDay(startDate)))
                .whereLessThanOrEqualTo("createdAt", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(endDate)))
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

    suspend fun addTransaction(transaction: Transaction): String {
        val docRef = if (transaction.id.isEmpty()) firestore.collection(Transaction.COLLECTION_NAME).document()
        else firestore.collection(Transaction.COLLECTION_NAME).document(transaction.id)
        val finalTransaction = transaction.copy(id = docRef.id)

        firestore.runTransaction { transactionOp ->
            // 1. All Reads First
            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, finalTransaction.warehouseId)
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // 2. All Writes
            transactionOp.set(docRef, finalTransaction)

            val change = when (finalTransaction.type) {
                TransactionType.INCOME, TransactionType.PAYMENT -> finalTransaction.amount
                TransactionType.EXPENSE, TransactionType.REFUND -> -finalTransaction.amount
            }

            // Update Summaries
            summaryRepository.applyFinancialUpdate(
                transaction = transactionOp,
                snapshots = snapshots,
                warehouseId = finalTransaction.warehouseId ?: "",
                cashChange = if (finalTransaction.paymentMethod == "cash") change else 0.0,
                bankChange = if (finalTransaction.paymentMethod == "bank") change else 0.0
            )

            // Update Global Cash Balance
            if (finalTransaction.paymentMethod == "cash") {
                transactionOp.update(statsRef, "totalCashBalance", com.google.firebase.firestore.FieldValue.increment(change))
            }
        }.await()

        return docRef.id
    }

    suspend fun addTransactionsBatch(transactions: List<Transaction>) {
        val batch = firestore.batch()
        transactions.forEach { transaction ->
            val docRef = if (transaction.id.isEmpty()) firestore.collection(Transaction.COLLECTION_NAME).document()
            else firestore.collection(Transaction.COLLECTION_NAME).document(transaction.id)
            batch.set(docRef, transaction.copy(id = docRef.id))
        }
        batch.commit().await()
    }

    suspend fun addExpense(expense: Expense) {
        val expenseRef = firestore.collection(Expense.COLLECTION_NAME).document()
        val finalExpense = expense.copy(id = expenseRef.id)

        val transactionRef = firestore.collection(Transaction.COLLECTION_NAME).document()
        val transactionData = Transaction(
            id = transactionRef.id,
            type = com.batterysales.data.models.TransactionType.EXPENSE,
            amount = expense.amount,
            description = expense.description,
            warehouseId = expense.warehouseId,
            paymentMethod = expense.paymentMethod,
            createdAt = expense.timestamp
        )

        firestore.runTransaction { transactionOp ->
            // 1. Reads
            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, transactionData.warehouseId)
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // 2. Writes
            transactionOp.set(expenseRef, finalExpense)
            transactionOp.set(transactionRef, transactionData)

            // Update Summaries
            summaryRepository.applyFinancialUpdate(
                transaction = transactionOp,
                snapshots = snapshots,
                warehouseId = transactionData.warehouseId ?: "",
                cashChange = if (transactionData.paymentMethod == "cash") -transactionData.amount else 0.0,
                bankChange = if (transactionData.paymentMethod == "bank") -transactionData.amount else 0.0
            )

            // Update Global Cash Balance
            if (transactionData.paymentMethod == "cash") {
                transactionOp.update(statsRef, "totalCashBalance", com.google.firebase.firestore.FieldValue.increment(-transactionData.amount))
            }
        }.await()
    }

    suspend fun updateTransaction(transaction: Transaction, forceSystemUpdate: Boolean = false) {
        val docRef = firestore.collection(Transaction.COLLECTION_NAME).document(transaction.id)

        firestore.runTransaction { transactionOp ->
            // 1. Reads
            val oldDoc = transactionOp.get(docRef)
            val oldTrans = oldDoc.toObject(Transaction::class.java)

            if (oldTrans?.isSystemManaged == true && !forceSystemUpdate) {
                throw Exception("هذا القيد مدار من قبل النظام (فاتورة/كمبيالة)، يرجى تعديله من المصدر لضمان دقة البيانات.")
            }
            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, transaction.warehouseId)
            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)

            // 2. Writes
            transactionOp.set(docRef, transaction)

            // Calculate Changes
            val oldChange = if (oldTrans != null) {
                when (oldTrans.type) {
                    TransactionType.INCOME, TransactionType.PAYMENT -> -oldTrans.amount
                    TransactionType.EXPENSE, TransactionType.REFUND -> oldTrans.amount
                }
            } else 0.0

            val newChange = when (transaction.type) {
                TransactionType.INCOME, TransactionType.PAYMENT -> transaction.amount
                TransactionType.EXPENSE, TransactionType.REFUND -> -transaction.amount
            }

            val totalChange = oldChange + newChange

            // Update Summaries
            summaryRepository.applyFinancialUpdate(
                transaction = transactionOp,
                snapshots = snapshots,
                warehouseId = transaction.warehouseId ?: "",
                cashChange = if (transaction.paymentMethod == "cash") totalChange else 0.0,
                bankChange = if (transaction.paymentMethod == "bank") totalChange else 0.0
            )

            // Update Global Cash Balance
            if (transaction.paymentMethod == "cash") {
                transactionOp.update(statsRef, "totalCashBalance", com.google.firebase.firestore.FieldValue.increment(totalChange))
            }
        }.await()

        val batch = firestore.batch()
        // If this transaction has a relatedId, or is pointed to by another transaction via relatedId, sync them.
        // For simple transfers, we sync amount and part of description if they are linked.
        val relatedId = transaction.relatedId
        if (relatedId != null) {
            val relatedDoc = firestore.collection(Transaction.COLLECTION_NAME).document(relatedId).get().await()
            if (relatedDoc.exists()) {
                val relatedTrans = relatedDoc.toObject(Transaction::class.java)
                if (relatedTrans != null) {
                    batch.update(relatedDoc.reference, "amount", transaction.amount)
                }
            }
        }

        // Also check if any transaction points TO this one
        val pointingSnap = firestore.collection(Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", transaction.id)
            .get().await()

        pointingSnap.documents.forEach { doc ->
            batch.update(doc.reference, "amount", transaction.amount)
        }

        batch.commit().await()
    }

    suspend fun deleteTransaction(transactionId: String, forceSystemUpdate: Boolean = false) {
        val docRef = firestore.collection(Transaction.COLLECTION_NAME).document(transactionId)

        val relatedId = firestore.runTransaction { transactionOp ->
            // 1. Reads
            val doc = transactionOp.get(docRef)
            val trans = doc.toObject(Transaction::class.java)

            if (trans?.isSystemManaged == true && !forceSystemUpdate) {
                throw Exception("هذا القيد مدار من قبل النظام (فاتورة/كمبيالة)، يرجى حذفه من المصدر لضمان دقة البيانات.")
            }
            val snapshots = summaryRepository.getSummarySnapshots(transactionOp, trans?.warehouseId)
            val relId = trans?.relatedId

            // 2. Writes
            transactionOp.delete(docRef)

            if (trans != null) {
                val change = when (trans.type) {
                    TransactionType.INCOME, TransactionType.PAYMENT -> -trans.amount
                    TransactionType.EXPENSE, TransactionType.REFUND -> trans.amount
                }

                // Update Summaries
                summaryRepository.applyFinancialUpdate(
                    transaction = transactionOp,
                    snapshots = snapshots,
                    warehouseId = trans.warehouseId ?: "",
                    cashChange = if (trans.paymentMethod == "cash") change else 0.0,
                    bankChange = if (trans.paymentMethod == "bank") change else 0.0
                )

                // Update Global Cash Balance
                if (trans.paymentMethod == "cash") {
                    val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
                    transactionOp.update(statsRef, "totalCashBalance", com.google.firebase.firestore.FieldValue.increment(change))
                }
            }
            relId
        }.await()

        val batch = firestore.batch()
        // Delete the counterpart if it exists
        if (relatedId != null) {
            batch.delete(firestore.collection(Transaction.COLLECTION_NAME).document(relatedId))
        }

        // Also delete any transaction that points TO this one
        val pointingSnap = firestore.collection(Transaction.COLLECTION_NAME)
            .whereEqualTo("relatedId", transactionId)
            .get().await()

        pointingSnap.documents.forEach { pointingDoc ->
            batch.delete(pointingDoc.reference)
        }

        batch.commit().await()
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
