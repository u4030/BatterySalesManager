package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.*
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.viewmodel.InventoryReportItem
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class VariantPagingSource(
    private val firestore: FirebaseFirestore,
    private val productRepository: ProductRepository,
    private val searchQuery: String?,
    private val warehouseId: String? = null,
    private val onlyWithStock: Boolean = false
) : PagingSource<DocumentSnapshot, InventoryReportItem>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, InventoryReportItem>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, InventoryReportItem> {
        return try {
            val items = mutableListOf<InventoryReportItem>()
            var lastDoc: DocumentSnapshot? = null

            // We iterate by products to maintain name sorting
            var productQuery = firestore.collection(Product.COLLECTION_NAME)
                .whereEqualTo("archived", false)
                .orderBy("name", Query.Direction.ASCENDING)

            if (!searchQuery.isNullOrBlank()) {
                productQuery = productQuery.whereGreaterThanOrEqualTo("name", searchQuery)
                    .whereLessThanOrEqualTo("name", searchQuery + "\uf8ff")
            }

            if (params.key != null) {
                productQuery = productQuery.startAfter(params.key!!)
            }

            val productSnapshot = productQuery.limit(params.loadSize.toLong()).get().await()
            val products = productSnapshot.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) }
            lastDoc = productSnapshot.documents.lastOrNull()

            if (products.isNotEmpty()) {
                val productIds = products.map { it.id }
                val pMap = products.associateBy { it.id }

                // Fetch variants for these products
                val variantSnapshot = firestore.collection(ProductVariant.COLLECTION_NAME)
                    .whereIn("productId", productIds)
                    .whereEqualTo("archived", false)
                    .get().await()

                val allVariants = variantSnapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }

                allVariants.forEach { variant ->
                    val product = pMap[variant.productId] ?: return@forEach

                    val whStock = variant.currentStock ?: emptyMap()
                    val totalQty = if (warehouseId != null) {
                        whStock[warehouseId] ?: 0
                    } else {
                        whStock.values.sum()
                    }

                    if (onlyWithStock && totalQty <= 0) return@forEach

                    items.add(
                        InventoryReportItem(
                            product = product,
                            variant = variant,
                            warehouseQuantities = if (warehouseId != null) mapOf(warehouseId to totalQty) else whStock,
                            totalQuantity = totalQty,
                            averageCost = variant.weightedAverageCost,
                            totalCostValue = totalQty * variant.weightedAverageCost
                        )
                    )
                }
            }

            // Sort items by product name and capacity
            val sortedItems = items.sortedWith(compareBy<InventoryReportItem> { it.product.name }.thenBy { it.variant.capacity })

            LoadResult.Page(
                data = sortedItems,
                prevKey = null,
                nextKey = if (productSnapshot.size() < params.loadSize) null else lastDoc
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
