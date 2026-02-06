package com.batterysales.data.repositories

import com.batterysales.data.models.OldBatteryTransaction
import com.batterysales.data.models.OldBatteryTransactionType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class OldBatteryRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun getAllTransactionsFlow(): Flow<List<OldBatteryTransaction>> {
        return firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(OldBatteryTransaction::class.java)?.copy(id = it.id) }
            }
    }

    suspend fun addTransaction(transaction: OldBatteryTransaction) {
        val docRef = firestore.collection(OldBatteryTransaction.COLLECTION_NAME).document()
        val finalTransaction = transaction.copy(id = docRef.id)
        docRef.set(finalTransaction).await()
    }

    suspend fun updateTransaction(transaction: OldBatteryTransaction) {
        firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .document(transaction.id)
            .set(transaction)
            .await()
    }

    suspend fun deleteTransaction(id: String) {
        firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .document(id)
            .delete()
            .await()
    }

    suspend fun getStockSummary(): Pair<Int, Double> {
        val transactions = firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .get()
            .await()
            .documents.mapNotNull { it.toObject(OldBatteryTransaction::class.java)?.copy(id = it.id) }

        var totalQty = 0
        var totalAmperes = 0.0

        transactions.forEach {
            when (it.type) {
                OldBatteryTransactionType.INTAKE -> {
                    totalQty += it.quantity
                    totalAmperes += it.totalAmperes
                }
                OldBatteryTransactionType.SALE -> {
                    totalQty -= it.quantity
                    totalAmperes -= it.totalAmperes
                }
                OldBatteryTransactionType.ADJUSTMENT -> {
                    // Assuming adjustment carries the absolute change
                    totalQty += it.quantity
                    totalAmperes += it.totalAmperes
                }
            }
        }
        return Pair(totalQty, totalAmperes)
    }
}
