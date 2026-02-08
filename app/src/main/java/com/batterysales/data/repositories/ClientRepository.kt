package com.batterysales.data.repositories

import com.batterysales.data.models.Client
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ClientRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun getAllClients(): List<Client> {
        return firestore.collection(Client.COLLECTION_NAME)
            .get()
            .await()
            .toObjects(Client::class.java)
    }

    suspend fun addClient(client: Client) {
        firestore.collection(Client.COLLECTION_NAME)
            .add(client)
            .await()
    }

    suspend fun deleteClient(clientId: String) {
        firestore.collection(Client.COLLECTION_NAME)
            .document(clientId)
            .delete()
            .await()
    }
}
