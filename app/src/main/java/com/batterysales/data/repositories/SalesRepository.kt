package com.batterysales.data.repositories

import com.batterysales.data.models.CustomerSummary
import com.batterysales.data.models.SalesInvoice
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // قراءة موديل الملخص من الكاش كلياً لتصفير قراءات الكوتة
    fun getCustomerSalesSummaryFlow(customerId: String): Flow<CustomerSummary?> = callbackFlow {
        val listener = firestore.collection("customer_summaries").document(customerId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val summary = snapshot?.toObject(CustomerSummary::class.java)
                trySend(summary)
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    // طبقة الأمر للكتابة فقط (Write-Only Command Layer)
    suspend fun submitSalesInvoice(invoice: SalesInvoice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("sales_orders")
                .document(invoice.orderId)
                .set(invoice)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
