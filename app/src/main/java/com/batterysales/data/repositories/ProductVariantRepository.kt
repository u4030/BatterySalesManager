package com.batterysales.data.repositories

import com.batterysales.data.models.ProductVariant
import com.google.firebase.firestore.FirebaseFirestore
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

    suspend fun deleteVariant(variantId: String) {
        firestore.collection(ProductVariant.COLLECTION_NAME)
            .document(variantId)
            .delete()
            .await()
    }
}
