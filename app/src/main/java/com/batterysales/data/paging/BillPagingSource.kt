package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.Bill
import com.batterysales.data.repositories.BillRepository
import com.google.firebase.firestore.DocumentSnapshot

class BillPagingSource(
    private val repository: BillRepository,
    private val searchQuery: String? = null,
    private val supplierId: String? = null
) : PagingSource<DocumentSnapshot, Bill>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, Bill>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, Bill> {
        return try {
            val result = repository.getBillsPaginated(
                searchQuery = searchQuery,
                supplierId = supplierId,
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
 
