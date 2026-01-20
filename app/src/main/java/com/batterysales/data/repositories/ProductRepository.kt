package com.batterysales.data.repositories

import com.batterysales.data.models.Product
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ProductRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getProducts(): List<Product> {
        return firestore.collection(Product.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(Product::class.java)
    }

    suspend fun getProduct(productId: String): Product? {
        return firestore.collection(Product.COLLECTION_NAME)
            .document(productId)
            .get()
            .await()
            .toObject(Product::class.java)
    }

    suspend fun addProduct(product: Product) {
        firestore.collection(Product.COLLECTION_NAME)
            .add(product)
            .await()
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
