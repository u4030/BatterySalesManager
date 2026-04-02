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
    private val searchQuery: String?,
    private val isSeller: Boolean = false,
    private val viewModel: ReportsViewModel? = null
) : PagingSource<DocumentSnapshot, InventoryReportItem>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, InventoryReportItem>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, InventoryReportItem> {
        return try {
            val variants = mutableListOf<ProductVariant>()
            val products = mutableListOf<Product>()
            var lastDoc: DocumentSnapshot? = null

            var rawFetchedSize = 0
            if (searchQuery != null && searchQuery.isNotBlank()) {
                // If it looks like a barcode (mostly numeric and length >= 6), try direct barcode lookup first
                val isProbablyBarcode = searchQuery.all { it.isDigit() } && searchQuery.length >= 6

                if (isProbablyBarcode) {
                    val barcodeSnap = firestore.collection(ProductVariant.COLLECTION_NAME)
                        .whereEqualTo("barcode", searchQuery)
                        .get().await()

                    val foundVariants = barcodeSnap.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }.filter { !it.archived }
                    variants.addAll(foundVariants)

                    val productIds = variants.map { it.productId }.distinct()
                    productIds.forEach { pid ->
                        productsMap[pid]?.let { products.add(it) } ?: run {
                            val doc = firestore.collection(Product.COLLECTION_NAME).document(pid).get().await()
                            doc.toObject(Product::class.java)?.copy(id = doc.id)?.let { products.add(it) }
                        }
                    }
                }

                // Also search by name if not enough or always (Combined search)
                var query = firestore.collection(Product.COLLECTION_NAME)
                    .orderBy("name")
                    .whereGreaterThanOrEqualTo("name", searchQuery)
                    .whereLessThanOrEqualTo("name", searchQuery + "\uf8ff")

                if (params.key != null) {
                    query = query.startAfter(params.key!!)
                }

                val snapshot = query.limit(params.loadSize.toLong()).get().await()
                rawFetchedSize = snapshot.size()
                val fetchedProducts = snapshot.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) }
                    .filter { !it.archived }

                products.addAll(fetchedProducts)
                lastDoc = snapshot.documents.lastOrNull()

                for (product in fetchedProducts) {
                    val vSnap = firestore.collection(ProductVariant.COLLECTION_NAME)
                        .whereEqualTo("productId", product.id)
                        .get().await()
                    variants.addAll(vSnap.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }.filter { !it.archived })
                }

                // Deduplicate variants
                val uniqueVariants = variants.distinctBy { it.id }.sortedBy { it.capacity }
                variants.clear()
                variants.addAll(uniqueVariants)

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
                        val warehouseIds = warehouseList.map { it.id }.toSet()
                        val entries = allEntriesMap[variant.id] ?: emptyList()

                        val (whQuantities, totalQty) = if (variant.currentStock != null) {
                            val filtered = variant.currentStock.filter { warehouseIds.contains(it.key) }
                            filtered to filtered.values.sum()
                        } else {
                            // Fallback to calculation
                            val quantities = warehouseList.associate { wh ->
                                wh.id to stockEntryRepository.calculateSummary(entries.filter { it.warehouseId == wh.id }).first
                            }
                            quantities to stockEntryRepository.calculateSummary(if (warehouseList.isNotEmpty()) entries.filter { warehouseIds.contains(it.warehouseId) } else entries).first
                        }

                        // For cost, we still need entries (weighted average calculation)
                        val relevantEntries = if (warehouseList.isNotEmpty()) {
                            entries.filter { warehouseIds.contains(it.warehouseId) }
                        } else {
                            entries
                        }
                        val summary = stockEntryRepository.calculateSummary(relevantEntries)

                        InventoryReportItem(
                            product = finalProduct,
                            variant = variant,
                            warehouseQuantities = whQuantities,
                            totalQuantity = totalQty,
                            averageCost = summary.second,
                            totalCostValue = totalQty * summary.second
                        )
                    }
                }.awaitAll().filter { !isSeller || it.totalQuantity > 0 }
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
