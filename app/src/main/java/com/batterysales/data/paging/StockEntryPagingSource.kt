package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.StockEntry
import com.batterysales.data.repositories.StockEntryRepository
import com.google.firebase.firestore.DocumentSnapshot

class StockEntryPagingSource(
    private val repository: StockEntryRepository,
    private val productVariantId: String,
    private val warehouseId: String? = null
) : PagingSource<DocumentSnapshot, StockEntry>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, StockEntry>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, StockEntry> {
        return try {
            val result = repository.getEntriesPaginated(
                productVariantId = productVariantId,
                warehouseId = warehouseId,
                lastDocument = params.key,
                limit = params.loadSize.toLong()
            )

            LoadResult.Page(
                data = result.first,
                prevKey = null,
                nextKey = if (result.first.size < params.loadSize) null else result.second
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
