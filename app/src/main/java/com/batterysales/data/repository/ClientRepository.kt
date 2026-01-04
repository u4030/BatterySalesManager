package com.batterysales.data.repository

import com.batterysales.data.models.Client
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : BaseRepository() {

    suspend fun addClient(client: Client): Result<String> = safeCall {
        val docRef = firestore.collection(Client.COLLECTION_NAME).document()
        val finalClient = client.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        docRef.set(finalClient).await()
        finalClient.id
    }

    suspend fun getAllClients(): Result<List<Client>> = safeCall {
        val snapshot = firestore.collection(Client.COLLECTION_NAME)
            .orderBy("name", Query.Direction.ASCENDING)
            .get()
            .await()
        snapshot.toObjectList(Client::class.java)
    }

    suspend fun updateClient(client: Client): Result<Unit> = safeCall {
        firestore.collection(Client.COLLECTION_NAME)
            .document(client.id)
            .set(client.copy(updatedAt = Date()))
            .await()
    }

    suspend fun deleteClient(clientId: String): Result<Unit> = safeCall {
        firestore.collection(Client.COLLECTION_NAME).document(clientId).delete().await()
    }
}
