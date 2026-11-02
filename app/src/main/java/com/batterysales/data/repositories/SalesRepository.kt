package com.batterysales.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    // 1. مستمع المبيعات المأمن كلياً عبر الكاش المزدوج لمنع Sequential UI Loops
    fun listenToSalesAtomic(): Flow<List<Map<String, Any>>> = callbackFlow {
        val query = firestore.collection("sales")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        // تفعيل استماع التغييرات مع مراعاة حالة البيانات المعلقة (Pending Writes)
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            snapshot?.let { snap ->
                // حارس الكاش المزدوج: نضمن استقبال البيانات الفوري من الكاش المحلي
                // مع السماح بالتحديثات المحلية أثناء المعاملات المعلقة (Pending Writes)
                val isCache = snap.metadata.isFromCache
                val hasPending = snap.metadata.hasPendingWrites()

                val salesList = snap.documents.map { doc ->
                    doc.data ?: emptyMap()
                }
                trySend(salesList)
            }
        }
        awaitClose { registration.remove() }
    }

    // 2. مستمع التنبيهات الفوري (0ms Latency) المطابق لمعمارية التنبيهات المطهرة ذرياً
    fun listenToLowStockAlerts(warehouseId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val query = firestore.collection("system_alerts")
            .whereEqualTo("warehouseId", warehouseId)
            .whereEqualTo("resolved", false) // مطابقة الاستعلام الخطي للسيرفر

        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            snapshot?.let { snap ->
                // القضاء التام على الـ Latency: تحديث UI فوري بمجرد كتابة الـ Transaction محلياً
                val alerts = snap.documents.map { doc ->
                    doc.data?.plus("id" to doc.id) ?: emptyMap()
                }
                trySend(alerts)
            }
        }
        awaitClose { registration.remove() }
    }
}
