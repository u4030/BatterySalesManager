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

import com.batterysales.viewmodel.ReportsViewModel
import kotlinx.coroutines.sync.withPermit

class InventoryPagingSource(
    private val firestore: FirebaseFirestore,
    private val stockEntryRepository: StockEntryRepository,
    private val productsMap: Map<String, Product>,
    private val warehouseList: List<Warehouse>,
    private val barcode: String?,
    private val viewModel: ReportsViewModel? = null
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
                val variantIds = variants.map { it.id }

                // Fetch all approved entries for these variants at once
                val allEntriesMap = stockEntryRepository.getEntriesForVariants(variantIds)

                variants.map { variant ->
                    async {
                        var product = productsMap[variant.productId]
                        if (product == null && variant.productId.isNotBlank()) {
                            try {
                                val doc = firestore.collection(Product.COLLECTION_NAME)
                                    .document(variant.productId)
                                    .get()
                                    .await()
                                product = doc.toObject(Product::class.java)?.copy(id = doc.id)
                            } catch (e: Exception) {
                                Log.e("InventoryPagingSource", "Error fetching product ${variant.productId}", e)
                            }
                        }

                        val finalProduct = product ?: Product(name = "Unknown (${variant.productId.take(5)})")

                        val entries = allEntriesMap[variant.id] ?: emptyList()
                        val globalSummary = stockEntryRepository.calculateSummary(entries)
                        
                        val whQuantities = warehouseList.associate { wh ->
                            wh.id to stockEntryRepository.calculateSummary(entries.filter { it.warehouseId == wh.id }).first
                        }

                        InventoryReportItem(
                            product = finalProduct,
                            variant = variant,
                            warehouseQuantities = whQuantities,
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
