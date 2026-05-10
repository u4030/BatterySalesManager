package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.*
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.viewmodel.InventoryReportItem
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
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
            // BASE QUERY: documentId is 100% reliable for paginated visibility.
            var query: Query = firestore.collection(ProductVariant.COLLECTION_NAME)

            val isBarcodeSearch = !searchQuery.isNullOrBlank() && searchQuery.all { it.isDigit() }

            if (!searchQuery.isNullOrBlank()) {
                if (isBarcodeSearch) {
                    query = query.whereEqualTo("barcode", searchQuery)
                } else {
                    // Search by Name: Requires index on productName.
                    query = query.orderBy("productName", Query.Direction.ASCENDING)
                        .whereGreaterThanOrEqualTo("productName", searchQuery)
                        .whereLessThanOrEqualTo("productName", searchQuery + "\uf8ff")
                }
            } else {
                query = query.orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            }

            if (params.key != null) {
                query = query.startAfter(params.key!!)
            }

            val snapshot = query.limit(params.loadSize.toLong()).get().await()
            val variants = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(ProductVariant::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    null
                }
            }
            val lastDoc = snapshot.documents.lastOrNull()

            val data = coroutineScope {
                variants.map { variant ->
                    async {
                        // MEMORY FILTER: Treat missing/null archived as false
                        if (variant.archived) return@async null

                        val warehouseIds = warehouseList.map { it.id }.toSet()

                        // STOCK CALCULATION: Robust fallback
                        val whStock = try {
                            if (variant.currentStock != null) {
                                variant.currentStock.filter { warehouseIds.contains(it.key) }
                            } else {
                                if (warehouseList.isEmpty()) {
                                    emptyMap<String, Int>()
                                } else {
                                    warehouseList.associate { wh ->
                                        wh.id to stockEntryRepository.getVariantQuantity(variant.id, wh.id)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            emptyMap<String, Int>()
                        }

                        val totalQty = whStock.values.sum()

                        // DATE FILTERING:
                        if (startDate != null && endDate != null) {
                            val start = com.batterysales.utils.DateUtils.getStartOfDay(startDate)
                            val end = com.batterysales.utils.DateUtils.getEndOfDay(endDate)
                            val hasActivity = try {
                                stockEntryRepository.hasActivityInRange(variant.id, start, end)
                            } catch (e: Exception) {
                                true
                            }
                            if (!hasActivity) return@async null
                        }

                        if (isSeller && totalQty <= 0) return@async null

                        // FALLBACK FOR NAMES:
                        val pName = variant.productName?.takeIf { it.isNotEmpty() } ?: productsMap[variant.productId]?.name ?: "منتج غير معروف"
                        val pSpec = variant.productSpecification?.takeIf { it.isNotEmpty() } ?: productsMap[variant.productId]?.specification ?: ""

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
