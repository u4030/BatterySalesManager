package com.batterysales.data.repositories

import com.batterysales.data.models.ProductVariant
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ProductVariantRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun updateVariantSimple(variant: ProductVariant) {
        firestore.collection(ProductVariant.COLLECTION_NAME)
            .document(variant.id)
            .set(variant)
            .await()
    }

    suspend fun getVariantsPaginated(
        productId: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20,
        forceRefresh: Boolean = false
    ): Pair<List<ProductVariant>, DocumentSnapshot?> {
        var query: Query = firestore.collection(ProductVariant.COLLECTION_NAME)
            .whereEqualTo("archived", false)

        if (!productId.isNullOrBlank()) {
            query = query.whereEqualTo("productId", productId)
        }

        query = query.orderBy("capacity", Query.Direction.ASCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        query = query.limit(limit)

        val source = if (!forceRefresh && lastDocument == null) Source.CACHE else Source.SERVER

        val snapshot = try {
            query.get(source).await()
        } catch (e: Exception) {
            if (source == Source.CACHE) {
                query.get(Source.SERVER).await()
            } else throw e
        }

        val variants = snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(variants, lastDoc)
    }

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

    @Deprecated("Use getVariantsForProductFlow or VariantPagingSource to avoid broad listens")
    fun getAllVariantsFlow(limit: Long = 100): Flow<List<ProductVariant>> = callbackFlow {
        val snapshot = try {
            firestore.collection(ProductVariant.COLLECTION_NAME)
                .limit(limit)
                .get(Source.CACHE)
                .await()
        } catch (e: Exception) {
            firestore.collection(ProductVariant.COLLECTION_NAME)
                .limit(limit)
                .get(Source.SERVER)
                .await()
        }
        val variants = snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
        trySend(variants).isSuccess
        close()
        awaitClose { }
    }

    suspend fun addVariant(variant: ProductVariant, summaryRepository: SummaryRepository? = null) {
        val docRef = if (variant.id.isNotEmpty()) firestore.collection(ProductVariant.COLLECTION_NAME).document(variant.id)
                     else firestore.collection(ProductVariant.COLLECTION_NAME).document()
        val finalVariant = variant.copy(id = docRef.id)
        docRef.set(finalVariant).await()
    }

    suspend fun updateVariant(variant: ProductVariant, summaryRepository: SummaryRepository? = null) {
        updateVariantSimple(variant)
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
