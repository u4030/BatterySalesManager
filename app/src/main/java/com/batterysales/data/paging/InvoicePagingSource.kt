package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.Invoice
import com.batterysales.data.repositories.InvoiceRepository
import com.google.firebase.firestore.DocumentSnapshot

class InvoicePagingSource(
    private val repository: InvoiceRepository,
    private val warehouseId: String?,
    private val status: String?,
    private val startDate: Long?,
    private val endDate: Long?,
    private val searchQuery: String?
) : PagingSource<DocumentSnapshot, Invoice>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, Invoice>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, Invoice> {
        return try {
            val result = repository.getInvoicesPaginated(
                warehouseId = warehouseId,
                status = status,
                startDate = startDate,
                endDate = endDate,
                searchQuery = searchQuery,
                lastDocument = params.key,
                limit = params.loadSize.toLong()
            )

            LoadResult.Page(
                data = result.first,
                prevKey = null,
                nextKey = result.second
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
