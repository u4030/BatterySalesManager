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

    suspend fun addTransaction(transaction: OldBatteryTransaction): String {
        val docRef = if (transaction.id.isNotBlank()) {
            firestore.collection(OldBatteryTransaction.COLLECTION_NAME).document(transaction.id)
        } else {
            firestore.collection(OldBatteryTransaction.COLLECTION_NAME).document()
        }
        val idToUse = if (transaction.id.isNotBlank()) transaction.id else docRef.id
        val finalTransaction = transaction.copy(id = idToUse)

        firestore.runTransaction { firestoreTransaction ->
            // Note: Query in transaction requires knowing the document ID.
            // We'll perform the sync after the transaction if we can't get the ID here,
            // but ideally we should update the model to store the scrapWarehouseId directly.
            // For now, we'll use a safer approach: execute addition and then use a helper to sync.
            firestoreTransaction.set(docRef, finalTransaction)
        }.await()
        syncScrapWarehouse(transaction.warehouseId)
        return idToUse
    }

    suspend fun updateTransaction(transaction: OldBatteryTransaction) {
        // Fetch old transaction to calculate diff
        val oldDoc = firestore.collection(OldBatteryTransaction.COLLECTION_NAME).document(transaction.id).get().await()
        val oldTrans = oldDoc.toObject(OldBatteryTransaction::class.java)

        firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .document(transaction.id)
            .set(transaction)
            .await()
        
        syncScrapWarehouse(transaction.warehouseId)
        if (oldTrans != null && oldTrans.warehouseId != transaction.warehouseId) {
            syncScrapWarehouse(oldTrans.warehouseId)
        }
    }

    suspend fun deleteTransaction(id: String) {
        val oldDoc = firestore.collection(OldBatteryTransaction.COLLECTION_NAME).document(id).get().await()
        val oldTrans = oldDoc.toObject(OldBatteryTransaction::class.java)

        firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .document(id)
            .delete()
            .await()
        
        oldTrans?.let { syncScrapWarehouse(it.warehouseId) }
    }

    /**
     * Recalculates and updates the ScrapWarehouse totals for a specific parent warehouse.
     */
    suspend fun syncScrapWarehouse(parentWarehouseId: String) {
        val summary = getStockSummary(parentWarehouseId)
        
        val snapshot = firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME)
            .whereEqualTo("parentWarehouseId", parentWarehouseId)
            .get()
            .await()
        
        if (snapshot.isEmpty) {
            // Create if missing (Migration fallback)
            val parentWh = firestore.collection("warehouses").document(parentWarehouseId).get().await()
            val name = parentWh.getString("name") ?: "غير معروف"
            val scrapWh = com.batterysales.data.models.ScrapWarehouse(
                name = "سكراب - $name",
                parentWarehouseId = parentWarehouseId,
                totalQuantity = summary.first,
                totalAmperes = summary.second
            )
            firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME).add(scrapWh).await()
        } else {
            val docRef = snapshot.documents.first().reference
            firestore.runTransaction { transaction ->
                transaction.update(docRef, "totalQuantity", summary.first)
                transaction.update(docRef, "totalAmperes", summary.second)
            }.await()
        }
    }

    suspend fun deleteTransactionsByInvoiceId(invoiceId: String) {
        val snapshot = firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .whereEqualTo("invoiceId", invoiceId)
            .get()
            .await()

        val batch = firestore.batch()
        snapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    suspend fun getStockSummary(warehouseId: String? = null): Pair<Int, Double> {
        var baseQuery: Query = firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
        if (warehouseId != null) {
            baseQuery = baseQuery.whereEqualTo("warehouseId", warehouseId)
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
            query = query.whereGreaterThanOrEqualTo("date", java.util.Date(com.batterysales.utils.DateUtils.getStartOfDay(startDate)))
                .whereLessThanOrEqualTo("date", java.util.Date(com.batterysales.utils.DateUtils.getEndOfDay(endDate)))
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
