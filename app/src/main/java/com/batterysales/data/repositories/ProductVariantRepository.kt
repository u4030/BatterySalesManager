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

    suspend fun addVariant(variant: ProductVariant) {
        val docRef = firestore.collection(ProductVariant.COLLECTION_NAME).document()
        val finalVariant = variant.copy(id = docRef.id)
        docRef.set(finalVariant).await()
    }

    suspend fun updateVariant(variant: ProductVariant) {
        firestore.collection(ProductVariant.COLLECTION_NAME)
            .document(variant.id)
            .set(variant)
            .await()
    }

    suspend fun getAllVariants(): List<ProductVariant> {
        val snapshot = firestore.collection(ProductVariant.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
    }
}
