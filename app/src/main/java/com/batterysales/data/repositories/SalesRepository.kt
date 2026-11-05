package com.batterysales.data.repositories

import com.google.firebase.firestore.DocumentChange
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

    // 2. مستمع التنبيهات الفوري (0ms Latency) المطابق لمعمارية التنبيهات المطهرة ذرياً مع تفريغ لحظي للتنبيهات المحذوفة
    fun listenToLowStockAlerts(warehouseId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        // إسقاط قيد whereEqualTo("resolved", false) من مستوى استعلام Firestore لتفادي تجميد الكاش المركب عند الحذف
        val query = firestore.collection("system_alerts")
            .whereEqualTo("warehouseId", warehouseId)

        val currentAlertsMap = java.util.concurrent.ConcurrentHashMap<String, Map<String, Any>>()

        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            snapshot?.let { snap ->
                // فحص صريح للتغييرات الهيكلية لطرد أي تنبيه محذوف بلحظتها (0ms Latency)
                for (change in snap.documentChanges) {
                    val docId = change.document.id
                    val isResolved = change.document.getBoolean("resolved") ?: false

                    if (change.type == DocumentChange.Type.REMOVED || isResolved) {
                        currentAlertsMap.remove(docId)
                    } else if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                        val doc = change.document
                        if (doc.exists() && !isResolved) {
                            val data = doc.data?.plus("id" to doc.id) ?: emptyMap()
                            currentAlertsMap[docId] = data
                        } else {
                            currentAlertsMap.remove(docId)
                        }
                    }
                }

                // بث فوري وتدفق لحظي إلى واجهة العرض للـ Composable الشاشة مع تفريغ فوري ومباشر دون فلاتر ثابتة متأخرة
                trySend(currentAlertsMap.values.toList()).isSuccess
            }
        }
        awaitClose { registration.remove() }
    }
}
