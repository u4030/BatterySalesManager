package com.batterysales.utils

import com.batterysales.data.models.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log

object DataSanitizer {
    suspend fun sanitizeVariants(firestore: FirebaseFirestore) {
        Log.d("Sanitizer", "Starting variant sanitization...")
        val variantsSnap = firestore.collection(ProductVariant.COLLECTION_NAME).get().await()
        val allVariants = variantsSnap.documents.mapNotNull { it.toObject(ProductVariant::class.java)?.copy(id = it.id) }

        val groups = allVariants.filter { !it.archived }.groupBy {
            "${it.productId}_${it.capacity}_${it.specification.trim().lowercase()}"
        }

        groups.forEach { (key, list) ->
            if (list.size > 1) {
                Log.w("Sanitizer", "Found ${list.size} duplicates for identity: $key")
                val master = list.minByOrNull { it.createdAt } ?: return@forEach
                val slaves = list.filter { it.id != master.id }

                slaves.forEach { slave ->
                    mergeVariants(firestore, master, slave)
                }
            }
        }
    }

    private suspend fun mergeVariants(firestore: FirebaseFirestore, master: ProductVariant, slave: ProductVariant) {
        Log.d("Sanitizer", "Merging Slave ${slave.id} into Master ${master.id}")

        // 1. Update Stock Entries
        val entriesSnap = firestore.collection(StockEntry.COLLECTION_NAME)
            .whereEqualTo("productVariantId", slave.id)
            .get().await()

        if (!entriesSnap.isEmpty) {
            entriesSnap.documents.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc ->
                    batch.update(doc.reference, "productVariantId", master.id)
                }
                batch.commit().await()
            }
        }

        // 2. Update Invoices
        val invoicesSnap = firestore.collection(Invoice.COLLECTION_NAME).get().await()
        invoicesSnap.documents.forEach { doc ->
            val invoice = doc.toObject(Invoice::class.java) ?: return@forEach
            var changed = false
            val newItems = invoice.items.map { item ->
                if (item.productId == slave.id) {
                    changed = true
                    item.copy(productId = master.id)
                } else item
            }
            if (changed) {
                doc.reference.update("items", newItems).await()
            }
        }

        // 3. Update Master Stock and Archive Slave
        val masterRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(master.id)
        val slaveRef = firestore.collection(ProductVariant.COLLECTION_NAME).document(slave.id)

        val masterStock = master.currentStock?.toMutableMap() ?: mutableMapOf()
        slave.currentStock?.forEach { (whId, qty) ->
            masterStock[whId] = (masterStock[whId] ?: 0) + qty
        }

        firestore.runTransaction { transaction ->
            transaction.update(masterRef, "currentStock", masterStock)
            transaction.update(slaveRef, "archived", true)
            // Rename slave barcode to avoid conflict
            transaction.update(slaveRef, "barcode", "merged_${slave.id}_${slave.barcode}")
        }.await()
    }
}
