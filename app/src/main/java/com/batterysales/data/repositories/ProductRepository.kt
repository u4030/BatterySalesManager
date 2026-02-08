package com.batterysales.data.repositories

import com.batterysales.data.models.Product
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ProductRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    fun getProducts(): Flow<List<Product>> = callbackFlow {
        val listenerRegistration = firestore.collection(Product.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val products = snapshot.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) }
                    trySend(products).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getProduct(productId: String): Product? {
        val snapshot = firestore.collection(Product.COLLECTION_NAME)
            .document(productId)
            .get()
            .await()
        return snapshot.toObject(Product::class.java)?.copy(id = snapshot.id)
    }

    suspend fun addProduct(product: Product) {
        val docRef = firestore.collection(Product.COLLECTION_NAME).document()
        val finalProduct = product.copy(id = docRef.id)
        docRef.set(finalProduct).await()
    }

    suspend fun updateProduct(product: Product) {
        firestore.collection(Product.COLLECTION_NAME)
            .document(product.id)
            .set(product)
            .await()
    }

    suspend fun deleteProduct(productId: String) {
        firestore.collection(Product.COLLECTION_NAME)
            .document(productId)
            .delete()
            .await()
    }
}