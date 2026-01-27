package com.batterysales.data.repositories

import com.batterysales.data.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    suspend fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    suspend fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun registerUser(email: String, password: String, displayName: String) {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val user = User(
            id = authResult.user!!.uid,
            email = email,
            displayName = displayName
        )
        firestore.collection("users").document(user.id).set(user).await()
    }

    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).get().await().toObject(User::class.java)
    }

    fun logout() {
        auth.signOut()
    }

    fun getAllUsersFlow(): Flow<List<User>> = callbackFlow {
        val listenerRegistration = firestore.collection(User.COLLECTION_NAME)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val users = snapshot.toObjects(User::class.java)
                    trySend(users).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updateUser(user: User) {
        firestore.collection(User.COLLECTION_NAME).document(user.id).set(user).await()
    }
}
