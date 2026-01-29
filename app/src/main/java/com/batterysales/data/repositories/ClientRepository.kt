package com.batterysales.data.repositories

import com.batterysales.data.models.Client
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

class ClientRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getAllClients(): List<Client> {
        val snapshot = firestore.collection(Client.COLLECTION_NAME)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Client::class.java)?.copy(id = it.id) }
    }

    suspend fun addClient(client: Client) {
        val docRef = firestore.collection(Client.COLLECTION_NAME).document()
        val finalClient = client.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        docRef.set(finalClient).await()
    }

    suspend fun deleteClient(clientId: String) {
        firestore.collection(Client.COLLECTION_NAME)
            .document(clientId)
            .delete()
            .await()
    }
}
