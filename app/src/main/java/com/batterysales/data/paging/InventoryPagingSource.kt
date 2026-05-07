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
    private val startDate: Long? = null,
    private val endDate: Long? = null
) : PagingSource<DocumentSnapshot, InventoryReportItem>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, InventoryReportItem>): DocumentSnapshot? = null

    private fun getProduct(productId: String, list: List<Product>, map: Map<String, Product>): Product? {
        return list.find { it.id == productId } ?: map[productId]
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, InventoryReportItem> {
        return try {
            val variants = mutableListOf<ProductVariant>()
            val products = mutableListOf<Product>()
            var lastDoc: DocumentSnapshot? = null

            var rawFetchedSize = 0
            coroutineScope {
            if (searchQuery != null && searchQuery.isNotBlank()) {
                // If this is the FIRST page, try barcode and invoice lookup
                if (params.key == null) {
                    // 1. Barcode lookup
                    val barcodeSnap = firestore.collection(ProductVariant.COLLECTION_NAME)
                        .whereEqualTo("barcode", searchQuery)
                        .get().await()

                    val foundBarcodeVariants = barcodeSnap.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }.filter { !it.archived }
                    variants.addAll(foundBarcodeVariants)

                    // 2. Invoice/Reference Number lookup in StockEntry
                    val entrySnap = firestore.collection(StockEntry.COLLECTION_NAME)
                        .whereEqualTo("invoiceNumber", searchQuery)
                        .get().await()
                    
                    val invoiceVariantIds = entrySnap.documents.mapNotNull { it.getString("productVariantId") }.distinct()
                        .filter { vid -> variants.none { it.id == vid } }
                    
                    if (invoiceVariantIds.isNotEmpty()) {
                        val invoiceVariants = invoiceVariantIds.chunked(30).flatMap { ids ->
                            firestore.collection(ProductVariant.COLLECTION_NAME)
                                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), ids)
                                .get().await().documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
                        }.filter { !it.archived }
                        variants.addAll(invoiceVariants)
                    }

                    // Fetch missing products for all found variants
                    val allProductIds = variants.map { it.productId }.distinct()
                    allProductIds.forEach { pid ->
                        if (products.none { it.id == pid }) {
                            productsMap[pid]?.let { products.add(it) } ?: run {
                                val doc = firestore.collection(Product.COLLECTION_NAME).document(pid).get().await()
                                doc.toObject(Product::class.java)?.copy(id = doc.id)?.let { products.add(it) }
                            }
                        }
                    }
                }

                // Also search by name (Combined search)
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

                // Add name-matched products, avoiding duplicates from barcode search
                fetchedProducts.forEach { fp ->
                    if (products.none { it.id == fp.id }) {
                        products.add(fp)
                    }
                }
                lastDoc = snapshot.documents.lastOrNull()

                // Fetch variants for name-matched products in bulk
                if (fetchedProducts.isNotEmpty()) {
                    val productIds = fetchedProducts.map { it.id }
                    val variantJobs = productIds.chunked(30).map { ids ->
                        async {
                            firestore.collection(ProductVariant.COLLECTION_NAME)
                                .whereIn("productId", ids)
                                .get()
                                .await()
                        }
                    }
                    val snapshots = variantJobs.awaitAll()
                    snapshots.forEach { snap ->
                        val pVariants = snap.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }.filter { !it.archived }
                        pVariants.forEach { pv ->
                            if (variants.none { it.id == pv.id }) {
                                variants.add(pv)
                            }
                        }
                    }
                }
                
                // Final sort for the result page
                val sortedVariants = variants.sortedWith(compareBy<ProductVariant> { getProduct(it.productId, products, productsMap)?.name ?: "" }.thenBy { it.capacity })
                variants.clear()
                variants.addAll(sortedVariants)

            } else {
                var query = firestore.collection(Product.COLLECTION_NAME)
                    .orderBy("name", com.google.firebase.firestore.Query.Direction.ASCENDING)

                if (params.key != null) {
                    query = query.startAfter(params.key!!)
                }

                val snapshot = query.limit(params.loadSize.toLong()).get().await()
                rawFetchedSize = snapshot.size()
                val fetchedProducts = snapshot.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) }
                    .filter { !it.archived } // Filter in memory to avoid index requirements and missing field issues

                products.addAll(fetchedProducts)
                lastDoc = snapshot.documents.lastOrNull()

                // Fetch variants for all products in bulk
                if (fetchedProducts.isNotEmpty()) {
                    val productIds = fetchedProducts.map { it.id }
                    val variantJobs = productIds.chunked(30).map { ids ->
                        async {
                            firestore.collection(ProductVariant.COLLECTION_NAME)
                                .whereIn("productId", ids)
                                .get()
                                .await()
                        }
                    }
                    val snapshots = variantJobs.awaitAll()
                    val allFetchedVariants = snapshots.flatMap { snap ->
                        snap.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
                    }.filter { !it.archived }
                    
                    variants.addAll(allFetchedVariants)
                }
                
                // Final sort for the result page
                val sortedVariants = variants.sortedWith(compareBy<ProductVariant> { getProduct(it.productId, products, productsMap)?.name ?: "" }.thenBy { it.capacity })
                variants.clear()
                variants.addAll(sortedVariants)
            }
            }

            val data = coroutineScope {
                val variantIds = variants.map { it.id }

                variants.mapNotNull { variant ->
                    async {
                        val finalProduct = getProduct(variant.productId, products, productsMap) ?: Product(name = "Unknown")
                        val warehouseIds = warehouseList.map { it.id }.toSet()

                        // 1. Efficient Stock Retrieval from pre-aggregated currentStock map
                        val (whQuantities, totalQty) = if (variant.currentStock != null) {
                            val filtered = variant.currentStock.filter { warehouseIds.contains(it.key) }
                            filtered to filtered.values.sum()
                        } else {
                            // Dangerous fallback: Summing raw entries. Ideally variants should have currentStock initialized.
                            // We use a light targeted aggregation here instead of fetching all entries.
                            val quantities = warehouseList.associate { wh ->
                                wh.id to stockEntryRepository.getVariantQuantity(variant.id, wh.id)
                            }
                            quantities to quantities.values.sum()
                        }

                        // Filter by date if applicable (requires entries, but we only fetch if necessary)
                        if (startDate != null && endDate != null) {
                            val start = com.batterysales.utils.DateUtils.getStartOfDay(startDate)
                            val end = com.batterysales.utils.DateUtils.getEndOfDay(endDate)
                            val hasActivity = stockEntryRepository.hasActivityInRange(variant.id, start, end)
                            if (!hasActivity) return@async null
                        }

                        // 2. Efficient Cost Calculation using denormalized value
                        val averageCost = variant.weightedAverageCost

                        InventoryReportItem(
                            product = finalProduct,
                            variant = variant,
                            warehouseQuantities = whQuantities,
                            totalQuantity = totalQty,
                            averageCost = averageCost,
                            totalCostValue = totalQty * averageCost
                        )
                    }
                }.awaitAll().filterNotNull().filter { !isSeller || it.totalQuantity > 0 }
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
