package com.batterysales.data.helper

import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Supplier
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceManager @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun updateVariantStock(
        transaction: Transaction,
        variantId: String,
        warehouseId: String,
        delta: Int
    ) {
        if (variantId.isEmpty()) return
        val variantRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(variantId)
        val variant = transaction.get(variantRef).toObject(ProductVariant::class.java)
        if (variant != null) {
            val stockLevels = variant.stockLevels.toMutableMap()
            val currentStock = stockLevels[warehouseId] ?: 0
            stockLevels[warehouseId] = currentStock + delta
            transaction.update(variantRef, "stockLevels", stockLevels)
        }
    }

    fun updateSupplierBalance(
        transaction: Transaction,
        supplierId: String,
        debitDelta: Double,
        creditDelta: Double
    ) {
        if (supplierId.isEmpty()) return
        val supplierRef = firestore.collection(Supplier.COLLECTION_NAME).document(supplierId)
        val supplier = transaction.get(supplierRef).toObject(Supplier::class.java)
        if (supplier != null) {
            val updates = mutableMapOf<String, Any>()
            if (debitDelta != 0.0) updates["totalDebit"] = supplier.totalDebit + debitDelta
            if (creditDelta != 0.0) updates["totalCredit"] = supplier.totalCredit + creditDelta
            if (updates.isNotEmpty()) transaction.update(supplierRef, updates)
        }
    }
}
