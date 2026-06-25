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

            firestore.runTransaction { transaction ->
                val variantRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(variant.id)
                
                // 1. Reads
                val snapshots = summaryRepository.getSummarySnapshots(transaction, warehouseIds)

                // 2. Writes
                transaction.set(variantRef, variant)

                // Update summaries for all warehouses
                warehouseIds.forEach { whId ->
                    summaryRepository.applyInventoryUpdate(
                        transaction = transaction,
                        snapshots = snapshots,
                        warehouseId = whId,
                        variantId = variant.id,
                        variant = variant,
                        qtyChange = 0 // No stock change, just metadata/status update
                    )
                }

                // If discontinued, cleanup alerts
                if (variant.isDiscontinued) {
                    warehouseIds.forEach { whId ->
                        val alertRef = firestore.collection("system_alerts").document("low_stock_${variant.id}_$whId")
                        transaction.delete(alertRef)
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
 
