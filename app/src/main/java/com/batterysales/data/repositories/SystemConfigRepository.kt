package com.batterysales.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemConfigRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        const val COLLECTION_NAME = "system_config"
        const val DOCUMENT_ID = "v2_migration"
        const val FIELD_COMPLETED = "completed"
    }

    suspend fun isMigrationCompleted(): Boolean {
        return try {
            val snapshot = firestore.collection(COLLECTION_NAME).document(DOCUMENT_ID).get().await()
            snapshot.getBoolean(FIELD_COMPLETED) ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setMigrationCompleted(completed: Boolean) {
        firestore.collection(COLLECTION_NAME).document(DOCUMENT_ID)
            .set(mapOf(FIELD_COMPLETED to completed))
            .await()
    }
}
