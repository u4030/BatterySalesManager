package com.batterysales.data.repositories

import com.batterysales.data.models.SystemAlert
import com.google.firebase.firestore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemAlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    /**
     * Clean real-time snapshot listener for active system alerts.
     * Allows valid Firestore document snapshots to immediately map to model objects
     * on Dispatchers.IO without complex cache locks or blocking channel states.
     */
    fun getSystemAlertsFlowClean(): Flow<List<SystemAlert>> = callbackFlow {
        val query = firestore.collection(SystemAlert.COLLECTION_NAME)
            .whereEqualTo("resolved", false)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            snapshot?.let { snap ->
                val alerts = snap.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(SystemAlert::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(alerts)
            }
        }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    fun listenToSystemAlerts(limit: Long = 20): Flow<List<SystemAlert>> = getSystemAlertsFlowClean()

    suspend fun getAlertsPaginated(
        warehouseId: String? = null,
        type: String? = null,
        lastDocument: DocumentSnapshot? = null,
        limit: Long = 20,
        forceRefresh: Boolean = false,
        source: Source? = null
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

        val targetSource = source ?: if (!forceRefresh && lastDocument == null) Source.CACHE else Source.SERVER

        val snapshot = try {
            query.get(targetSource).await()
        } catch (e: Exception) {
            if (targetSource == Source.CACHE) {
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
