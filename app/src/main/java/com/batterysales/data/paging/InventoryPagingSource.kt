package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.*
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.viewmodel.InventoryReportItem
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class InventoryPagingSource(
    private val firestore: FirebaseFirestore,
    private val stockEntryRepository: StockEntryRepository,
    private val productsMap: Map<String, Product>, // Legacy, kept for compatibility if needed
    private val warehouseList: List<Warehouse>,
    private val searchQuery: String?,
    private val isSeller: Boolean = false,
    private val startDate: Long? = null,
    private val endDate: Long? = null
) : PagingSource<DocumentSnapshot, InventoryReportItem>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, InventoryReportItem>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, InventoryReportItem> {
        return try {
            // Direct query on ProductVariants using denormalized productName for sorting
            var query = firestore.collection(ProductVariant.COLLECTION_NAME)
                .whereEqualTo("archived", false)
                .orderBy("productName", Query.Direction.ASCENDING)
                .orderBy("capacity", Query.Direction.ASCENDING)

            if (!searchQuery.isNullOrBlank()) {
                // If searching, we filter by productName
                query = query.whereGreaterThanOrEqualTo("productName", searchQuery)
                    .whereLessThanOrEqualTo("productName", searchQuery + "\uf8ff")
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
                        val whStock = variant.currentStock ?: emptyMap()
                        val warehouseIds = warehouseList.map { it.id }.toSet()
                        val filteredWhStock = whStock.filter { warehouseIds.contains(it.key) }
                        val totalQty = filteredWhStock.values.sum()

                        // Date filtering logic (still requires one efficient count query per item ONLY if date filter is active)
                        if (startDate != null && endDate != null) {
                            val start = com.batterysales.utils.DateUtils.getStartOfDay(startDate)
                            val end = com.batterysales.utils.DateUtils.getEndOfDay(endDate)
                            val hasActivity = stockEntryRepository.hasActivityInRange(variant.id, start, end)
                            if (!hasActivity) return@async null
                        }

                        if (isSeller && totalQty <= 0) return@async null

                        InventoryReportItem(
                            product = Product(id = variant.productId, name = variant.productName, specification = variant.productSpecification),
                            variant = variant,
                            warehouseQuantities = filteredWhStock,
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
            android.util.Log.e("InventoryPagingSource", "Zero-Logic Load error", e)
            LoadResult.Error(e)
        }
    }
}
