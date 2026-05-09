package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.*
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.viewmodel.InventoryReportItem
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
) : PagingSource<DocumentSnapshot, InventoryReportItem>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, InventoryReportItem>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, InventoryReportItem> {
        return try {
            // Base query.
            // IMPORTANT: Removed whereEqualTo("archived", false) to include legacy documents missing the field.
            // Sorting by productId and capacity to ensure visibility of documents missing "productName".
            var query: Query = firestore.collection(ProductVariant.COLLECTION_NAME)

            val isBarcodeSearch = !searchQuery.isNullOrBlank() && searchQuery.all { it.isDigit() }

            if (!searchQuery.isNullOrBlank()) {
                if (isBarcodeSearch) {
                    query = query.whereEqualTo("barcode", searchQuery)
                } else {
                    // If searching by name, we MUST use productName sort for Firestore inequality filter.
                    // This means pre-migration items won't appear in name-searched results until migrated.
                    query = query.orderBy("productName", Query.Direction.ASCENDING)
                        .orderBy("capacity", Query.Direction.ASCENDING)
                        .whereGreaterThanOrEqualTo("productName", searchQuery)
                        .whereLessThanOrEqualTo("productName", searchQuery + "\uf8ff")
                }
            } else {
                // DEFAULT SORT: Use productId to ensure ALL records are visible (including legacy).
                query = query.orderBy("productId", Query.Direction.ASCENDING)
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
                        // Filter archived in-memory to handle legacy missing fields
                        if (variant.archived) return@async null

                        val warehouseIds = warehouseList.map { it.id }.toSet()

                        // Fallback for stock if currentStock is null (migration in progress)
                        val whStock = if (variant.currentStock != null) {
                            variant.currentStock.filter { warehouseIds.contains(it.key) }
                        } else {
                            // On-the-fly calculation for visible warehouses
                            warehouseList.associate { wh ->
                                wh.id to stockEntryRepository.getVariantQuantity(variant.id, wh.id)
                            }
                        }

                        val totalQty = whStock.values.sum()

                        // Date filtering logic
                        if (startDate != null && endDate != null) {
                            val start = com.batterysales.utils.DateUtils.getStartOfDay(startDate)
                            val end = com.batterysales.utils.DateUtils.getEndOfDay(endDate)
                            val hasActivity = stockEntryRepository.hasActivityInRange(variant.id, start, end)
                            if (!hasActivity) return@async null
                        }

                        if (isSeller && totalQty <= 0) return@async null

                        // Fallback for names if denormalization hasn't run yet
                        val pName = variant.productName.ifEmpty { productsMap[variant.productId]?.name ?: "منتج غير معروف" }
                        val pSpec = variant.productSpecification.ifEmpty { productsMap[variant.productId]?.specification ?: "" }

                        InventoryReportItem(
                            product = Product(id = variant.productId, name = pName, specification = pSpec),
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
            android.util.Log.e("InventoryPagingSource", "Load error: ${e.message}", e)
            LoadResult.Error(e)
        }
    }
}
