package com.batterysales.data.repositories

import com.batterysales.data.models.OldBatteryTransaction
import com.batterysales.data.models.OldBatteryTransactionType
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

    suspend fun getStockSummary(warehouseId: String? = null): Pair<Int, Double> {
        var baseQuery = firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
        if (warehouseId != null) {
            baseQuery = baseQuery.whereEqualTo("warehouseId", warehouseId) as com.google.firebase.firestore.CollectionReference
        }

        // Aggregate INTAKE
        val intakeQuery = baseQuery.whereEqualTo("type", OldBatteryTransactionType.INTAKE.name)
        val intakeSnap = intakeQuery.aggregate(
            AggregateField.sum("quantity"),
            AggregateField.sum("totalAmperes")
        ).get(AggregateSource.SERVER).await()

        // Aggregate SALE
        val saleQuery = baseQuery.whereEqualTo("type", OldBatteryTransactionType.SALE.name)
        val saleSnap = saleQuery.aggregate(
            AggregateField.sum("quantity"),
            AggregateField.sum("totalAmperes")
        ).get(AggregateSource.SERVER).await()

        // Aggregate ADJUSTMENT
        val adjQuery = baseQuery.whereEqualTo("type", OldBatteryTransactionType.ADJUSTMENT.name)
        val adjSnap = adjQuery.aggregate(
            AggregateField.sum("quantity"),
            AggregateField.sum("totalAmperes")
        ).get(AggregateSource.SERVER).await()

        val intakeQty = intakeSnap.getLong(AggregateField.sum("quantity"))?.toInt() ?: 0
        val intakeAmps = intakeSnap.getDouble(AggregateField.sum("totalAmperes")) ?: 0.0

        val saleQty = saleSnap.getLong(AggregateField.sum("quantity"))?.toInt() ?: 0
        val saleAmps = saleSnap.getDouble(AggregateField.sum("totalAmperes")) ?: 0.0

        val adjQty = adjSnap.getLong(AggregateField.sum("quantity"))?.toInt() ?: 0
        val adjAmps = adjSnap.getDouble(AggregateField.sum("totalAmperes")) ?: 0.0

        val totalQty = intakeQty - saleQty + adjQty
        val totalAmps = intakeAmps - saleAmps + adjAmps

        return Pair(totalQty, totalAmps)
    }

    suspend fun getTransactionsPaginated(
        warehouseId: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20
    ): Pair<List<OldBatteryTransaction>, DocumentSnapshot?> {
        var query: Query = firestore.collection(OldBatteryTransaction.COLLECTION_NAME)

        if (warehouseId != null) {
            query = query.whereEqualTo("warehouseId", warehouseId)
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
        val transactions = snapshot.documents.mapNotNull { it.toObject(OldBatteryTransaction::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(transactions, lastDoc)
    }
}
