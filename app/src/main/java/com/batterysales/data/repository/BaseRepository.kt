package com.batterysales.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * BaseRepository - الفئة الأساسية لكافة المستودعات
 *
 * توفر معالجة موحدة للعمليات الآمنة (Safe Calls) والتعامل مع الأخطاء
 * لضمان استقرار التطبيق وتقليل تكرار الكود.
 */
abstract class BaseRepository {

    /**
     * تنفيذ عملية بشكل آمن ومعالجة الاستثناءات
     */
    protected suspend fun <T> safeCall(call: suspend () -> T): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(call())
            } catch (e: Exception) {
                Timber.e(e, "Error in Repository safeCall")
                Result.failure(e)
            }
        }
    }

    /**
     * دالة مساعدة لتحويل نتائج Firestore إلى قائمة من الكائنات
     */
    protected fun <T> com.google.firebase.firestore.QuerySnapshot.toObjectList(clazz: Class<T>): List<T> {
        return this.toObjects(clazz)
    }
}
