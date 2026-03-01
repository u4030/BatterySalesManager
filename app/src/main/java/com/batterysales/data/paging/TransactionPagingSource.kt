package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.Transaction
import com.batterysales.data.repositories.AccountingRepository
import com.google.firebase.firestore.DocumentSnapshot

class TransactionPagingSource(
    private val repository: AccountingRepository,
    private val warehouseId: String?,
    private val paymentMethod: String?,
    private val types: List<String>?,
    private val startDate: Long?,
    private val endDate: Long?
) : PagingSource<DocumentSnapshot, Transaction>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, Transaction>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, Transaction> {
        return try {
            val result = repository.getTransactionsPaginated(
                warehouseId = warehouseId,
                paymentMethod = paymentMethod,
                types = types,
                startDate = startDate,
                endDate = endDate,
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
