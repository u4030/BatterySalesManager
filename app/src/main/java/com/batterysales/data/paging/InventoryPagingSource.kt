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
            val variants = mutableListOf<ProductVariant>()
            val products = mutableListOf<Product>()
            var lastDoc: DocumentSnapshot? = null

            var rawFetchedSize = 0
            if (barcode != null) {
                var query = firestore.collection(ProductVariant.COLLECTION_NAME)
                    .whereEqualTo("barcode", barcode)

                if (params.key != null) {
                    query = query.startAfter(params.key!!)
                }

                val snapshot = query.limit(params.loadSize.toLong()).get().await()
                rawFetchedSize = snapshot.size()
                variants.addAll(snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }.filter { !it.archived })
                lastDoc = snapshot.documents.lastOrNull()

                // Fetch products for these variants
                val productIds = variants.map { it.productId }.distinct()
                productIds.forEach { pid ->
                    productsMap[pid]?.let { products.add(it) } ?: run {
                        val doc = firestore.collection(Product.COLLECTION_NAME).document(pid).get().await()
                        doc.toObject(Product::class.java)?.copy(id = doc.id)?.let { products.add(it) }
                    }
                }
            } else {
                var query = firestore.collection(Product.COLLECTION_NAME)
                    .orderBy("name")

                if (params.key != null) {
                    query = query.startAfter(params.key!!)
                }

                val snapshot = query.limit(params.loadSize.toLong()).get().await()
                rawFetchedSize = snapshot.size()
                val fetchedProducts = snapshot.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) }
                    .filter { !it.archived } // Filter in memory to avoid index requirements and missing field issues

                products.addAll(fetchedProducts)
                lastDoc = snapshot.documents.lastOrNull()

                // For each product, fetch its variants
                for (product in fetchedProducts) {
                    val vSnap = firestore.collection(ProductVariant.COLLECTION_NAME)
                        .whereEqualTo("productId", product.id)
                        .get()
                        .await()

                    val pVariants = vSnap.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
                        .filter { !it.archived }
                        .sortedBy { it.capacity }

                    variants.addAll(pVariants)
                }
            }

            val data = coroutineScope {
                val variantIds = variants.map { it.id }
                val allEntriesMap = stockEntryRepository.getEntriesForVariants(variantIds)
                val pLookup = products.associateBy { it.id }

                variants.map { variant ->
                    async {
                        val finalProduct = pLookup[variant.productId] ?: productsMap[variant.productId] ?: Product(name = "Unknown")
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
                nextKey = if (rawFetchedSize < params.loadSize) null else lastDoc
            )
        } catch (e: Exception) {
            android.util.Log.e("InventoryPagingSource", "Load error", e)
            LoadResult.Error(e)
        }
    }
}
