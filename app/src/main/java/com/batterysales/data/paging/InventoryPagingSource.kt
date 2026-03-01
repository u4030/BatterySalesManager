package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.*
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.viewmodel.InventoryReportItem
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class InventoryPagingSource(
    private val firestore: FirebaseFirestore,
    private val stockEntryRepository: StockEntryRepository,
    private val productsMap: Map<String, Product>,
    private val warehouseList: List<Warehouse>,
    private val barcode: String?
) : PagingSource<DocumentSnapshot, InventoryReportItem>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, InventoryReportItem>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, InventoryReportItem> {
        return try {
            var query = firestore.collection(ProductVariant.COLLECTION_NAME)
                .whereEqualTo("archived", false)

            if (barcode != null) {
                query = query.whereEqualTo("barcode", barcode)
            }

            if (params.key != null) {
                query = query.startAfter(params.key!!)
            }

            val snapshot = query.limit(params.loadSize.toLong()).get().await()
            val variants = snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }

            val data = coroutineScope {
                variants.map { variant ->
                    async {
                        val product = productsMap[variant.productId] ?: Product(name = "Unknown")
                        val globalSummary = stockEntryRepository.getVariantSummary(variant.id, null)

                        val whSummaries = warehouseList.map { wh ->
                            wh.id to async { stockEntryRepository.getVariantSummary(variant.id, wh.id).first }
                        }

                        InventoryReportItem(
                            product = product,
                            variant = variant,
                            warehouseQuantities = whSummaries.associate { it.first to it.second.await() },
                            totalQuantity = globalSummary.first,
                            averageCost = globalSummary.second,
                            totalCostValue = globalSummary.third
                        )
                    }
                }.awaitAll()
            }

            LoadResult.Page(
                data = data,
                prevKey = null,
                nextKey = if (variants.size < params.loadSize) null else snapshot.documents.lastOrNull()
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
