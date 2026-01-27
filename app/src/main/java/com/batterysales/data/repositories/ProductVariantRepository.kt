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
        return firestore.collection(ProductVariant.COLLECTION_NAME)
            .whereEqualTo("productId", productId)
            .get()
            .await()
            .toObjects(ProductVariant::class.java)
    }

    suspend fun getVariant(variantId: String): ProductVariant? {
        return firestore.collection(ProductVariant.COLLECTION_NAME)
            .document(variantId)
            .get()
            .await()
            .toObject(ProductVariant::class.java)
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
                    val variants = snapshot.toObjects(ProductVariant::class.java)
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
                    val variants = snapshot.toObjects(ProductVariant::class.java)
                    trySend(variants).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addVariant(variant: ProductVariant) {
        firestore.collection(ProductVariant.COLLECTION_NAME)
            .add(variant)
            .await()
    }

    suspend fun updateVariant(variant: ProductVariant) {
        firestore.collection(ProductVariant.COLLECTION_NAME)
            .document(variant.id)
            .set(variant)
            .await()
    }

    suspend fun getAllVariants(): List<ProductVariant> {
        return firestore.collection(ProductVariant.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(ProductVariant::class.java)
    }
}
