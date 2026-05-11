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
            firestoreTransaction.set(docRef, finalTransaction)
        }.await()

        // Update ScrapWarehouse totals incrementally to save quota
        updateScrapWarehouseTotals(
            parentWarehouseId = transaction.warehouseId,
            quantityDelta = when (transaction.type) {
                OldBatteryTransactionType.INTAKE -> transaction.quantity
                OldBatteryTransactionType.SALE -> -transaction.quantity
                OldBatteryTransactionType.ADJUSTMENT -> transaction.quantity
            },
            amperesDelta = when (transaction.type) {
                OldBatteryTransactionType.INTAKE -> transaction.totalAmperes
                OldBatteryTransactionType.SALE -> -transaction.totalAmperes
                OldBatteryTransactionType.ADJUSTMENT -> transaction.totalAmperes
            }
        )
        return idToUse
    }

    suspend fun updateTransaction(transaction: OldBatteryTransaction) {
        val oldDoc = firestore.collection(OldBatteryTransaction.COLLECTION_NAME).document(transaction.id).get().await()
        val oldTrans = oldDoc.toObject(OldBatteryTransaction::class.java)

        if (oldTrans != null) {
            // 1. Revert old totals
            updateScrapWarehouseTotals(
                parentWarehouseId = oldTrans.warehouseId,
                quantityDelta = when (oldTrans.type) {
                    OldBatteryTransactionType.INTAKE -> -oldTrans.quantity
                    OldBatteryTransactionType.SALE -> oldTrans.quantity
                    OldBatteryTransactionType.ADJUSTMENT -> -oldTrans.quantity
                },
                amperesDelta = when (oldTrans.type) {
                    OldBatteryTransactionType.INTAKE -> -oldTrans.totalAmperes
                    OldBatteryTransactionType.SALE -> oldTrans.totalAmperes
                    OldBatteryTransactionType.ADJUSTMENT -> -oldTrans.totalAmperes
                }
            )
        }

        firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .document(transaction.id)
            .set(transaction)
            .await()
        
        // 2. Apply new totals
        updateScrapWarehouseTotals(
            parentWarehouseId = transaction.warehouseId,
            quantityDelta = when (transaction.type) {
                OldBatteryTransactionType.INTAKE -> transaction.quantity
                OldBatteryTransactionType.SALE -> -transaction.quantity
                OldBatteryTransactionType.ADJUSTMENT -> transaction.quantity
            },
            amperesDelta = when (transaction.type) {
                OldBatteryTransactionType.INTAKE -> transaction.totalAmperes
                OldBatteryTransactionType.SALE -> -transaction.totalAmperes
                OldBatteryTransactionType.ADJUSTMENT -> transaction.totalAmperes
            }
        )
    }

    suspend fun deleteTransaction(id: String) {
        val oldDoc = firestore.collection(OldBatteryTransaction.COLLECTION_NAME).document(id).get().await()
        val oldTrans = oldDoc.toObject(OldBatteryTransaction::class.java)

        if (oldTrans != null) {
            updateScrapWarehouseTotals(
                parentWarehouseId = oldTrans.warehouseId,
                quantityDelta = when (oldTrans.type) {
                    OldBatteryTransactionType.INTAKE -> -oldTrans.quantity
                    OldBatteryTransactionType.SALE -> oldTrans.quantity
                    OldBatteryTransactionType.ADJUSTMENT -> -oldTrans.quantity
                },
                amperesDelta = when (oldTrans.type) {
                    OldBatteryTransactionType.INTAKE -> -oldTrans.totalAmperes
                    OldBatteryTransactionType.SALE -> oldTrans.totalAmperes
                    OldBatteryTransactionType.ADJUSTMENT -> -oldTrans.totalAmperes
                }
            )
        }

        firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
            .document(id)
            .delete()
            .await()
    }

    private suspend fun updateScrapWarehouseTotals(parentWarehouseId: String, quantityDelta: Int, amperesDelta: Double) {
        if (quantityDelta == 0 && Math.abs(amperesDelta) < 0.001) return

        val snapshot = firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME)
            .whereEqualTo("parentWarehouseId", parentWarehouseId)
            .get()
            .await()
        
        if (snapshot.isEmpty) {
            syncScrapWarehouse(parentWarehouseId)
        } else {
            val docRef = snapshot.documents.first().reference
            firestore.runTransaction { transaction ->
                transaction.update(docRef, "totalQuantity", com.google.firebase.firestore.FieldValue.increment(quantityDelta.toLong()))
                transaction.update(docRef, "totalAmperes", com.google.firebase.firestore.FieldValue.increment(amperesDelta))
            }.await()
        }
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

    /**
     * Optimized summary retrieval from pre-calculated ScrapWarehouse documents.
     */
    suspend fun getStockSummary(warehouseId: String? = null): Pair<Int, Double> {
        if (warehouseId != null) {
            val snap = firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME)
                .whereEqualTo("parentWarehouseId", warehouseId)
                .limit(1).get().await()
            val scrapWh = snap.documents.firstOrNull()?.toObject(com.batterysales.data.models.ScrapWarehouse::class.java)
            if (scrapWh != null) return Pair(scrapWh.totalQuantity, scrapWh.totalAmperes)
        } else {
            val snap = firestore.collection(com.batterysales.data.models.ScrapWarehouse.COLLECTION_NAME).get().await()
            val list = snap.documents.mapNotNull { it.toObject(com.batterysales.data.models.ScrapWarehouse::class.java) }
            if (list.isNotEmpty()) return Pair(list.sumOf { it.totalQuantity }, list.sumOf { it.totalAmperes })
        }

        // Deep fallback only if summary document is missing
        var baseQuery: Query = firestore.collection(OldBatteryTransaction.COLLECTION_NAME)
        if (warehouseId != null) {
            baseQuery = baseQuery.whereEqualTo("warehouseId", warehouseId)
        }

        val intakeSnap = baseQuery.whereEqualTo("type", OldBatteryTransactionType.INTAKE.name)
            .aggregate(AggregateField.sum("quantity"), AggregateField.sum("totalAmperes")).get(AggregateSource.SERVER).await()
        val saleSnap = baseQuery.whereEqualTo("type", OldBatteryTransactionType.SALE.name)
            .aggregate(AggregateField.sum("quantity"), AggregateField.sum("totalAmperes")).get(AggregateSource.SERVER).await()
        val adjSnap = baseQuery.whereEqualTo("type", OldBatteryTransactionType.ADJUSTMENT.name)
            .aggregate(AggregateField.sum("quantity"), AggregateField.sum("totalAmperes")).get(AggregateSource.SERVER).await()

        val totalQty = (intakeSnap.getLong(AggregateField.sum("quantity")) ?: 0) - (saleSnap.getLong(AggregateField.sum("quantity")) ?: 0) + (adjSnap.getLong(AggregateField.sum("quantity")) ?: 0)
        val totalAmps = (intakeSnap.getDouble(AggregateField.sum("totalAmperes")) ?: 0.0) - (saleSnap.getDouble(AggregateField.sum("totalAmperes")) ?: 0.0) + (adjSnap.getDouble(AggregateField.sum("totalAmperes")) ?: 0.0)

        return Pair(totalQty.toInt(), totalAmps)
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
