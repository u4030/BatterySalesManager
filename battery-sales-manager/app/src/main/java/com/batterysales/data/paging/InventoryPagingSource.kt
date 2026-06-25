package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.*
import com.batterysales.data.repositories.StockEntryRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class InventoryPagingSource(
    private val firestore: FirebaseFirestore,
    private val stockEntryRepository: StockEntryRepository,
    private val productsMap: Map<String, Product>, 
    private val warehouseList: List<Warehouse>,
    private val searchQuery: String?,
    private val isSeller: Boolean = false,
    private val startDate: Long? = null,
    private val endDate: Long? = null
) : PagingSource<DocumentSnapshot, com.batterysales.data.models.InventoryReportItem>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, com.batterysales.data.models.InventoryReportItem>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, com.batterysales.data.models.InventoryReportItem> {
        return try {
            // Robust query on ProductVariants
            var query = firestore.collection(ProductVariant.COLLECTION_NAME)
                .whereEqualTo("archived", false)

            if (!searchQuery.isNullOrBlank()) {
                query = query.orderBy("productName", Query.Direction.ASCENDING)
                    .orderBy("capacity", Query.Direction.ASCENDING)
                    .whereGreaterThanOrEqualTo("productName", searchQuery)
                    .whereLessThanOrEqualTo("productName", searchQuery + "\uf8ff")
            } else {
                // Optimized sort using productName (denormalized)
                query = query.orderBy("productName", Query.Direction.ASCENDING)
                    .orderBy("capacity", Query.Direction.ASCENDING)
            }

            if (params.key != null) {
                query = query.startAfter(params.key!!)
            }

            val snapshot = query.limit(params.loadSize.toLong()).get().await()
            val variants = snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
            val lastDoc = snapshot.documents.lastOrNull()

            val data = coroutineScope {
                variants.map { variant ->
                    async {
                        // High Performance: Use currentStock map
                        // Fallback: If currentStock is null (migration pending), calculate on-the-fly for THIS document only
                        val warehouseIds = warehouseList.map { it.id }.toSet()
                        
                        // High Performance: Use currentStock map
                        // After migration, currentStock should always be present.
                        val whStock = variant.currentStock?.filter { warehouseIds.contains(it.key) } ?: emptyMap()
                        
                        val totalQty = whStock.values.sum()

                        // Date filtering
                        if (startDate != null && endDate != null) {
                            val start = com.batterysales.utils.DateUtils.getStartOfDay(startDate)
                            val end = com.batterysales.utils.DateUtils.getEndOfDay(endDate)
                            val hasActivity = stockEntryRepository.hasActivityInRange(variant.id, start, end)
                            if (!hasActivity) return@async null
                        }

                        if (isSeller && totalQty <= 0) return@async null

                        com.batterysales.data.models.InventoryReportItem(
                            product = Product(id = variant.productId, name = variant.productName ?: "", specification = variant.productSpecification ?: ""),
                            variant = variant,
                            warehouseQuantities = whStock,
                            totalQuantity = totalQty,
                            averageCost = variant.weightedAverageCost,
                            totalCostValue = totalQty * variant.weightedAverageCost
                        )
                    }
                }.awaitAll().filterNotNull()
            }

            LoadResult.Page(
                data = data,
                prevKey = null,
                nextKey = if (snapshot.size() < params.loadSize) null else lastDoc
            )
        } catch (e: Exception) {
            android.util.Log.e("InventoryPagingSource", "Load error", e)
            LoadResult.Error(e)
        }
    }
}
