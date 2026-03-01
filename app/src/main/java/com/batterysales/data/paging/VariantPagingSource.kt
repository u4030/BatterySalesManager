package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.ProductVariant
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await

class VariantPagingSource(
    private val firestore: FirebaseFirestore,
    private val barcode: String?
) : PagingSource<DocumentSnapshot, ProductVariant>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, ProductVariant>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, ProductVariant> {
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

            LoadResult.Page(
                data = variants,
                prevKey = null,
                nextKey = snapshot.documents.lastOrNull()
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
