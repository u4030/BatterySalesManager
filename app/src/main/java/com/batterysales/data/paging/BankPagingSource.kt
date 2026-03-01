package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.BankTransaction
import com.batterysales.data.repositories.BankRepository
import com.google.firebase.firestore.DocumentSnapshot

class BankPagingSource(
    private val repository: BankRepository,
    private val startDate: Long?,
    private val endDate: Long?
) : PagingSource<DocumentSnapshot, BankTransaction>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, BankTransaction>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, BankTransaction> {
        return try {
            val result = repository.getTransactionsPaginated(
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
