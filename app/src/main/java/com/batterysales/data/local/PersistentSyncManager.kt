package com.batterysales.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "pending_sync_operations")

data class PendingSyncOperation(
    val operationId: String,
    val type: String, // SALE, STOCK_TRANSFER, PAYMENT_DELETE, STOCK_ENTRY
    val relatedId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payloadJson: String = "",
    val status: String = "PENDING_SYNC"
)

@Singleton
class PersistentSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    companion object {
        private val PENDING_OPS_KEY = stringPreferencesKey("pending_operations_list")
    }

    val pendingOperationsFlow: Flow<List<PendingSyncOperation>> = context.syncDataStore.data.map { prefs ->
        val json = prefs[PENDING_OPS_KEY] ?: ""
        if (json.isBlank()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<PendingSyncOperation>>() {}.type
                gson.fromJson<List<PendingSyncOperation>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun recordPendingOperation(operation: PendingSyncOperation) {
        context.syncDataStore.edit { prefs ->
            val json = prefs[PENDING_OPS_KEY] ?: ""
            val currentList = if (json.isBlank()) {
                mutableListOf()
            } else {
                try {
                    val type = object : TypeToken<List<PendingSyncOperation>>() {}.type
                    gson.fromJson<List<PendingSyncOperation>>(json, type)?.toMutableList() ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
            currentList.removeAll { it.operationId == operation.operationId }
            currentList.add(operation)
            prefs[PENDING_OPS_KEY] = gson.toJson(currentList)
        }
    }

    suspend fun removePendingOperation(operationId: String) {
        context.syncDataStore.edit { prefs ->
            val json = prefs[PENDING_OPS_KEY] ?: ""
            if (json.isNotBlank()) {
                try {
                    val type = object : TypeToken<List<PendingSyncOperation>>() {}.type
                    val currentList = gson.fromJson<List<PendingSyncOperation>>(json, type)?.toMutableList() ?: mutableListOf()
                    val removed = currentList.removeAll { it.operationId == operationId }
                    if (removed) {
                        prefs[PENDING_OPS_KEY] = gson.toJson(currentList)
                    }
                } catch (e: Exception) {
                    // Ignore parse error on removal
                }
            }
        }
    }

    suspend fun clearAllPendingOperations() {
        context.syncDataStore.edit { prefs ->
            prefs.remove(PENDING_OPS_KEY)
        }
    }

    suspend fun hasLocalPendingOperations(): Boolean {
        return try {
            val ops = pendingOperationsFlow.first()
            ops.any { it.status == "PENDING_SYNC" }
        } catch (e: Exception) {
            false
        }
    }
}
