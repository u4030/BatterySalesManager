package com.batterysales.data.repositories

import android.content.Context
import com.batterysales.data.models.User
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) {

    suspend fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    suspend fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun registerUser(
        email: String,
        password: String,
        displayName: String,
        role: String = "seller",
        warehouseId: String? = null
    ) {
        // This method will now use a secondary Firebase App to prevent logging out the Admin.
        val options = FirebaseApp.getInstance().options
        val secondaryAppName = "SecondaryApp_${System.currentTimeMillis()}"
        val secondaryApp = FirebaseApp.initializeApp(context, options, secondaryAppName)

        try {
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            val authResult = secondaryAuth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user!!.uid

            val user = User(
                id = userId,
                email = email,
                displayName = displayName,
                role = role,
                warehouseId = warehouseId
            )

            // Still use the main firestore instance (as the admin is still logged in there)
            firestore.collection(User.COLLECTION_NAME).document(userId).set(user).await()

            // Sign out from the secondary app (optional but good practice)
            secondaryAuth.signOut()
        } finally {
            secondaryApp.delete()
        }
    }

    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toObject(User::class.java)?.copy(id = snapshot.id)
    }

    fun getCurrentUserFlow(): Flow<User?> = callbackFlow {
        var snapshotListener: com.google.firebase.firestore.ListenerRegistration? = null
        
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            snapshotListener?.remove()
            
            if (uid == null) {
                trySend(null)
            } else {
                snapshotListener = firestore.collection("users").document(uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            trySend(snapshot.toObject(User::class.java)?.copy(id = snapshot.id))
                        }
                    }
            }
        }
        auth.addAuthStateListener(authListener)
        awaitClose { 
            auth.removeAuthStateListener(authListener)
            snapshotListener?.remove()
        }
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
                    val users = snapshot.documents.mapNotNull { it.toObject(User::class.java)?.copy(id = it.id) }
                    trySend(users).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updateUser(user: User) {
        firestore.collection(User.COLLECTION_NAME).document(user.id).set(user).await()
    }

    suspend fun deleteUser(userId: String) {
        firestore.collection(User.COLLECTION_NAME).document(userId).delete().await()
    }
}
