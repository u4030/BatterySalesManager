package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.OldBatteryTransaction
import com.batterysales.data.repositories.OldBatteryRepository
import com.google.firebase.firestore.DocumentSnapshot

class OldBatteryPagingSource(
    private val repository: OldBatteryRepository,
    private val warehouseId: String?,
    private val startDate: Long?,
    private val endDate: Long?
) : PagingSource<DocumentSnapshot, OldBatteryTransaction>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, OldBatteryTransaction>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, OldBatteryTransaction> {
        return try {
            val result = repository.getTransactionsPaginated(
                warehouseId = warehouseId,
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
