package com.batterysales.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Product
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class VariantPagingSource(
    private val firestore: FirebaseFirestore,
    private val searchQuery: String? = null
) : PagingSource<DocumentSnapshot, ProductVariant>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, ProductVariant>): DocumentSnapshot? = null

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, ProductVariant> {
        return try {
            var query = firestore.collection(ProductVariant.COLLECTION_NAME)
                .whereEqualTo("archived", false)

            if (!searchQuery.isNullOrBlank()) {
                query = query.whereGreaterThanOrEqualTo("productName", searchQuery)
                    .whereLessThanOrEqualTo("productName", searchQuery + "\uf8ff")
                    .orderBy("productName", Query.Direction.ASCENDING)
            } else {
                query = query.orderBy("productName", Query.Direction.ASCENDING)
                    .orderBy("capacity", Query.Direction.ASCENDING)
            }

            if (params.key != null) {
                query = query.startAfter(params.key!!)
            }

            val snapshot = query.limit(params.loadSize.toLong()).get().await()
            val variants = snapshot.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }
            val lastDoc = snapshot.documents.lastOrNull()

            LoadResult.Page(
                data = variants,
                prevKey = null,
                nextKey = if (snapshot.size() < params.loadSize) null else lastDoc
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
 
