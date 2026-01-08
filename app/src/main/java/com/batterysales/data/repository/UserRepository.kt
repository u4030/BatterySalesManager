package com.batterysales.data.repository

import com.batterysales.data.models.User
import com.batterysales.data.models.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : BaseRepository() {

    /**
     * تسجيل مستخدم جديد وحفظ بياناته في Firestore
     */
    suspend fun registerUser(email: String, password: String, displayName: String): Result<String> = safeCall {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val userId = authResult.user?.uid ?: throw Exception("فشل إنشاء المستخدم")

        val user = User(
            id = userId,
            email = email,
            displayName = displayName,
            role = UserRole.SELLER.name,
            isActive = true,
            createdAt = Date()
        )

        firestore.collection("users").document(userId).set(user).await()
        userId
    }

    /**
     * تسجيل الدخول وتحديث وقت الدخول
     * تم استخدام set مع SetOptions.merge() لضمان إنشاء الوثيقة إذا لم تكن موجودة وتجنب خطأ NOT_FOUND
     */
    suspend fun loginUser(email: String, password: String): Result<String> = safeCall {
        val authResult = auth.signInWithEmailAndPassword(email, password).await()
        val userId = authResult.user?.uid ?: throw Exception("فشل تسجيل الدخول")

        val updates = mapOf("lastLoginAt" to Date())
        firestore.collection("users").document(userId).set(updates, SetOptions.merge()).await()
        userId
    }

    /**
     * جلب بيانات المستخدم الحالي
     */
    suspend fun getCurrentUser(): Result<User?> = safeCall {
        val userId = auth.currentUser?.uid ?: return@safeCall null
        val snapshot = firestore.collection("users").document(userId).get().await()
        snapshot.toObject(User::class.java)
    }

    /**
     * جلب كافة المستخدمين (للمدير فقط)
     */
    suspend fun getAllUsers(): Result<List<User>> = safeCall {
        val snapshot = firestore.collection("users").get().await()
        snapshot.toObjects(User::class.java)
    }

    /**
     * تحديث دور المستخدم
     */
    suspend fun updateUserRole(userId: String, role: UserRole): Result<Unit> = safeCall {
        firestore.collection("users").document(userId).update("role", role.name).await()
    }

    /**
     * تسجيل الخروج
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * التحقق من حالة تسجيل الدخول
     */
    fun isUserLoggedIn(): Boolean = auth.currentUser != null
}
