package com.batterysales.data.repositories

import com.batterysales.data.models.SystemAlert
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemAlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getAlertsPaginated(
        warehouseId: String? = null,
        type: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20,
        forceRefresh: Boolean = false
    ): Pair<List<SystemAlert>, DocumentSnapshot?> {
        var query: Query = firestore.collection(SystemAlert.COLLECTION_NAME)
            .whereEqualTo("resolved", false)

        if (!warehouseId.isNullOrBlank()) {
            query = query.whereEqualTo("warehouseId", warehouseId)
        }

        if (!type.isNullOrBlank()) {
            query = query.whereEqualTo("type", type)
        }

        query = query.orderBy("timestamp", Query.Direction.DESCENDING)

        if (lastDocument != null) {
            query = query.startAfter(lastDocument)
        }

        query = query.limit(limit)

        val source = if (!forceRefresh && lastDocument == null) Source.CACHE else Source.SERVER

        val snapshot = try {
            query.get(source).await()
        } catch (e: Exception) {
            if (source == Source.CACHE) {
                query.get(Source.SERVER).await()
            } else throw e
        }

        val alerts = snapshot.documents.mapNotNull { it.toObject(SystemAlert::class.java)?.copy(id = it.id) }
        val lastDoc = snapshot.documents.lastOrNull()

        return Pair(alerts, lastDoc)
    }

    suspend fun resolveAlert(alertId: String) {
        firestore.collection(SystemAlert.COLLECTION_NAME)
            .document(alertId)
            .update("resolved", true)
            .await()
    }
}
