package com.batterysales.data.repositories

import com.batterysales.data.models.ApprovalRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ApprovalRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun addRequest(request: ApprovalRequest): String {
        val docRef = firestore.collection(ApprovalRequest.COLLECTION_NAME).document()
        val finalRequest = request.copy(id = docRef.id)
        docRef.set(finalRequest).await()
        return docRef.id
    }

    fun getPendingRequestsFlow(): Flow<List<ApprovalRequest>> = callbackFlow {
        val listenerRegistration = firestore.collection(ApprovalRequest.COLLECTION_NAME)
            .whereEqualTo("status", ApprovalRequest.STATUS_PENDING)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { it.toObject(ApprovalRequest::class.java)?.copy(id = it.id) }
                    trySend(requests).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updateRequestStatus(requestId: String, status: String, adminId: String? = null, adminNote: String? = null) {
        val updates = mutableMapOf<String, Any>(
            "status" to status
        )
        adminId?.let { updates["adminId"] = it }
        adminNote?.let { updates["adminNote"] = it }
        
        firestore.collection(ApprovalRequest.COLLECTION_NAME)
            .document(requestId)
            .update(updates)
            .await()
    }
    
    suspend fun getRequest(requestId: String): ApprovalRequest? {
        val snapshot = firestore.collection(ApprovalRequest.COLLECTION_NAME)
            .document(requestId)
            .get()
            .await()
        return snapshot.toObject(ApprovalRequest::class.java)?.copy(id = snapshot.id)
    }
}
