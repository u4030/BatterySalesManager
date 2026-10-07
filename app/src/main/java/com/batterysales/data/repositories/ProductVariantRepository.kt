package com.batterysales.data.repositories

import com.batterysales.data.models.ProductVariant
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ProductVariantRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getVariantsForProduct(productId: String): List<ProductVariant> {
        val snapshot = firestore.collection(ProductVariant.COLLECTION_NAME)
            .whereEqualTo("productId", productId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
    }

    suspend fun getVariant(variantId: String): ProductVariant? {
        val snapshot = firestore.collection(ProductVariant.COLLECTION_NAME)
            .document(variantId)
            .get()
            .await()
        return snapshot.toObject(ProductVariant::class.java)?.copy(id = snapshot.id)
    }

    fun getVariantsForProductFlow(productId: String): Flow<List<ProductVariant>> = callbackFlow {
        val listenerRegistration = firestore.collection(ProductVariant.COLLECTION_NAME)
            .whereEqualTo("productId", productId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val variants = snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
                    trySend(variants).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    fun getAllVariantsFlow(): Flow<List<ProductVariant>> = callbackFlow {
        val listenerRegistration = firestore.collection(ProductVariant.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val variants = snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
                    trySend(variants).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addVariant(variant: ProductVariant, summaryRepository: SummaryRepository? = null) {
        val docRef = firestore.collection(ProductVariant.COLLECTION_NAME).document()
        val finalVariant = variant.copy(id = docRef.id)

        if (summaryRepository != null) {
            val warehousesSnap = firestore.collection("warehouses").get().await()
            val warehouseIds = warehousesSnap.documents.map { it.id }

            firestore.runTransaction { transaction ->
                // 1. Reads
                val snapshots = summaryRepository.getSummarySnapshots(transaction, warehouseIds)

                // 2. Writes
                transaction.set(docRef, finalVariant)

                // Initialize in all warehouse summaries
                warehouseIds.forEach { whId ->
                    summaryRepository.applyInventoryUpdate(
                        transaction = transaction,
                        snapshots = snapshots,
                        warehouseId = whId,
                        variantId = finalVariant.id,
                        variant = finalVariant,
                        qtyChange = 0
                    )
                }
            }.await()
        } else {
            docRef.set(finalVariant).await()
        }
    }

    suspend fun updateVariant(variant: ProductVariant, summaryRepository: SummaryRepository? = null) {
        if (summaryRepository != null) {
            val warehousesSnap = firestore.collection("warehouses").get().await()
            val warehouseIds = warehousesSnap.documents.map { it.id }

            // NUCLEAR ARCHIVING STRATEGY: Find all linked stock entries to archive them too
            val linkedEntries = if (variant.archived) {
                firestore.collection("stock_entries")
                    .whereEqualTo("productVariantId", variant.id)
                    .get().await().documents
            } else emptyList()

            val productDoc = firestore.collection("products").document(variant.productId).get().await()
            val supplierId = productDoc.getString("supplierId") ?: ""

            firestore.runTransaction { transaction ->
                val variantRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(variant.id)
                
                // 1. Reads
                val snapshots = summaryRepository.getSummarySnapshots(transaction, warehouseIds)

                // 2. Writes
                transaction.set(variantRef, variant)

                if (supplierId.isNotEmpty()) {
                    summaryRepository.invalidateSupplierReportCache(transaction, supplierId)
                }

                if (variant.archived) {
                    summaryRepository.removeInventoryItem(transaction, snapshots, warehouseIds, variant.id)
                    
                    // Archive linked stock entries and reverse their financial impact
                    linkedEntries.forEach { doc ->
                        val entry = doc.toObject(com.batterysales.data.models.StockEntry::class.java)
                        if (entry != null && entry.status == "approved" && entry.supplierId.isNotEmpty()) {
                            val cost = entry.getNetCost()
                            val supplierRef = firestore.collection("suppliers").document(entry.supplierId)
                            
                            if (cost > 0.001) {
                                transaction.update(supplierRef, "totalDebit", com.google.firebase.firestore.FieldValue.increment(-cost))
                                transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-cost))
                                summaryRepository.applySupplierUpdate(transaction, snapshots, entry.supplierId, variant.productName ?: "", debitChange = -cost)
                            } else if (cost < -0.001) {
                                transaction.update(supplierRef, "totalCredit", com.google.firebase.firestore.FieldValue.increment(cost))
                                transaction.update(supplierRef, "currentBalance", com.google.firebase.firestore.FieldValue.increment(-cost))
                                summaryRepository.applySupplierUpdate(transaction, snapshots, entry.supplierId, variant.productName ?: "", creditChange = cost)
                            }
                            summaryRepository.invalidateSupplierReportCache(transaction, entry.supplierId)
                            
                            // Reverse from Global Stats
                            val statsRef = firestore.collection(com.batterysales.data.models.SystemStats.COLLECTION_NAME).document(com.batterysales.data.models.SystemStats.DOCUMENT_ID)
                            transaction.set(statsRef, mapOf(
                                "totalSupplierDebt" to com.google.firebase.firestore.FieldValue.increment(-cost),
                                "totalInventoryValue" to com.google.firebase.firestore.FieldValue.increment(-cost),
                                "totalInventoryQuantity" to com.google.firebase.firestore.FieldValue.increment(-(entry.quantity - entry.returnedQuantity).toLong())
                            ), com.google.firebase.firestore.SetOptions.merge())
                        }
                        transaction.update(doc.reference, "status", "archived")
                    }
                } else {
                    warehouseIds.forEach { whId ->
                        summaryRepository.applyInventoryUpdate(
                            transaction = transaction,
                            snapshots = snapshots,
                            warehouseId = whId,
                            variantId = variant.id,
                            variant = variant,
                            qtyChange = 0
                        )
                    }
                }

                // Alert sync on discontinuation state change
                if (variant.isDiscontinued) {
                    warehouseIds.forEach { whId ->
                        val alertRef = firestore.collection("system_alerts").document("low_stock_${variant.id}_$whId")
                        transaction.delete(alertRef)
                    }
                } else if (!variant.archived) {
                    warehouseIds.forEach { whId ->
                        val currentQty = (variant.currentStock?.get(whId) as? Number)?.toInt() ?: 0
                        val threshold = variant.minQuantities[whId] ?: variant.minQuantity
                        val alertRef = firestore.collection("system_alerts").document("low_stock_${variant.id}_$whId")

                        if (threshold > 0 && currentQty <= threshold) {
                            val whSnap = snapshots.warehouseSummaries[whId]
                            val whName = whSnap?.id?.removePrefix("inventory_wh_") ?: "مخزن"
                            val specSuffix = if (variant.specification.isNotBlank()) " | ${variant.specification}" else ""

                            transaction.set(alertRef, com.batterysales.data.models.SystemAlert(
                                id = alertRef.id,
                                type = com.batterysales.data.models.SystemAlert.TYPE_LOW_STOCK,
                                title = "مخزون منخفض: ${variant.productName ?: ""}",
                                message = "${variant.capacity}A$specSuffix في $whName | الكمية: $currentQty (الحد: $threshold)",
                                relatedId = variant.id,
                                warehouseId = whId,
                                warehouseName = whName,
                                timestamp = java.util.Date(),
                                data = mapOf(
                                    "capacity" to variant.capacity,
                                    "specification" to variant.specification,
                                    "currentStock" to currentQty,
                                    "threshold" to threshold
                                )
                            ))
                        } else {
                            transaction.delete(alertRef)
                        }
                    }
                }
            }.await()
        } else {
            firestore.collection(ProductVariant.COLLECTION_NAME)
                .document(variant.id)
                .set(variant)
                .await()
        }
    }

    suspend fun getAllVariants(): List<ProductVariant> {
        val snapshot = firestore.collection(ProductVariant.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
    }

    suspend fun getVariantByBarcode(barcode: String): ProductVariant? {
        val snapshot = firestore.collection(ProductVariant.COLLECTION_NAME)
            .whereEqualTo("barcode", barcode)
            .whereEqualTo("archived", false)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(ProductVariant::class.java)?.copy(id = snapshot.documents.firstOrNull()?.id ?: "")
    }
}
 
