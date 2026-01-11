package com.batterysales.data.repository

import com.batterysales.data.models.Product
import com.batterysales.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : BaseRepository() {

    suspend fun getAllProducts(): Result<List<Product>> = safeCall {
        val snapshot = firestore.collection(Constants.Collections.PRODUCTS)
            .whereEqualTo("isArchived", false)
            .orderBy("name", Query.Direction.ASCENDING)
            .get()
            .await()
        snapshot.toObjectList(Product::class.java)
    }

    suspend fun getProductById(productId: String): Result<Product?> = safeCall {
        val document = firestore.collection(Constants.Collections.PRODUCTS)
            .document(productId)
            .get()
            .await()
        document.toObject(Product::class.java)
    }

    suspend fun addProduct(product: Product): Result<String> = safeCall {
        val docRef = firestore.collection(Constants.Collections.PRODUCTS).document()
        val finalProduct = product.copy(id = docRef.id)
        docRef.set(finalProduct).await()
        finalProduct.id
    }

    suspend fun updateProduct(product: Product): Result<Unit> = safeCall {
        firestore.collection(Constants.Collections.PRODUCTS)
            .document(product.id)
            .set(product)
            .await()
    }

    suspend fun updateProductQuantity(productId: String, newQuantity: Int): Result<Unit> = safeCall {
        firestore.collection(Constants.Collections.PRODUCTS)
            .document(productId)
            .update("quantity", newQuantity)
            .await()
    }

    suspend fun deleteProduct(productId: String): Result<Unit> = safeCall {
        firestore.collection(Constants.Collections.PRODUCTS)
            .document(productId)
            .update("isArchived", true)
            .await()
    }
}
