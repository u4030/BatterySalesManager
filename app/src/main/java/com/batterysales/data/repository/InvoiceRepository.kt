package com.batterysales.data.repository

import com.batterysales.data.models.Invoice
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoiceRepository @Inject constructor(

    private val firestore: FirebaseFirestore,
    private val productRepository: ProductRepository
) : BaseRepository() {

    private val collectionName = "invoices"

    suspend fun createInvoice(invoice: Invoice): Result<String> = safeCall {
        val docRef = firestore.collection(collectionName).document()
        val finalInvoice = invoice.copy(id = docRef.id, createdAt = Date(), updatedAt = Date())
        docRef.set(finalInvoice).await()
        // Update product quantities
        invoice.items.forEach { item ->
            val productResult = productRepository.getProductById(item.productId)
            productResult.getOrNull()?.let { product ->
                val newQuantity = product.quantity - item.quantity
                productRepository.updateProductQuantity(item.productId, newQuantity)
            }
        }
        finalInvoice.id
    }

    suspend fun getInvoice(invoiceId: String): Result<Invoice> = safeCall {
        val document = firestore.collection(collectionName)
            .document(invoiceId)
            .get()
            .await()

        document.toObject(Invoice::class.java) ?: throw Exception("Invoice not found")
    }

    suspend fun getAllInvoices(): Result<List<Invoice>> = safeCall {
        val snapshot = firestore.collection(collectionName)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        snapshot.toObjectList(Invoice::class.java)
    }

    suspend fun updateInvoiceStatus(invoiceId: String, status: String): Result<Unit> = safeCall {
        val updates = mapOf(
            "status" to status,
            "updatedAt" to Date(),
            "paidDate" to if (status == "paid") Date() else null
        )
        firestore.collection(collectionName).document(invoiceId).update(updates).await()
    }

    suspend fun recordPayment(invoiceId: String, amount: Double): Result<Unit> = safeCall {
        val invoice = getInvoice(invoiceId).getOrThrow()
        val newPaidAmount = invoice.paidAmount + amount
        val newRemainingAmount = (invoice.totalAmount - newPaidAmount).coerceAtLeast(0.0)
        val newStatus = if (newPaidAmount >= invoice.totalAmount) "paid" else "pending"

        val updates = mapOf(
            "paidAmount" to newPaidAmount,
            "remainingAmount" to newRemainingAmount,
            "status" to newStatus,
            "updatedAt" to Date()
        )
        firestore.collection(collectionName).document(invoiceId).update(updates).await()
    }
}
