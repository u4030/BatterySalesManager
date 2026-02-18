package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log
import javax.inject.Inject

@HiltViewModel
class AdminToolsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val productVariantRepository: ProductVariantRepository,
    private val supplierRepository: SupplierRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val billRepository: BillRepository,
    private val systemConfigRepository: SystemConfigRepository
) : ViewModel() {

    private val _migrationStatus = MutableStateFlow<String?>(null)
    val migrationStatus = _migrationStatus.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating = _isMigrating.asStateFlow()

    fun runDataMigration() {
        viewModelScope.launch {
            _isMigrating.value = true
            _migrationStatus.value = "Starting migration..."
            try {
                // 1. Reset all balances first (optional but safer)
                _migrationStatus.value = "Resetting current balances..."
                val variants = productVariantRepository.getAllVariants()
                val suppliers = supplierRepository.getSuppliersOnce()

                // 2. Recalculate Stock Levels
                _migrationStatus.value = "Recalculating Stock Levels..."
                val allEntries = stockEntryRepository.getAllStockEntries()
                val approvedEntries = allEntries.filter { it.status == StockEntry.STATUS_APPROVED }

                val newVariants = variants.map { variant ->
                    val variantEntries = approvedEntries.filter { it.productVariantId == variant.id }
                    val stockLevels = variantEntries.groupBy { it.warehouseId }
                        .mapValues { (_, entries) -> entries.sumOf { it.quantity - it.returnedQuantity } }
                    variant.copy(stockLevels = stockLevels)
                }

                // 3. Recalculate Supplier Balances
                _migrationStatus.value = "Recalculating Supplier Balances..."
                val allBills = billRepository.getAllBills()

                val newSuppliers = suppliers.map { supplier ->
                    val supplierEntries = approvedEntries.filter {
                        it.supplierId == supplier.id &&
                        (supplier.resetDate == null || !it.timestamp.before(supplier.resetDate))
                    }
                    val supplierBills = allBills.filter {
                        it.supplierId == supplier.id &&
                        (supplier.resetDate == null || !it.createdAt.before(supplier.resetDate))
                    }

                    supplier.copy(
                        totalDebit = supplierEntries.sumOf { it.totalCost },
                        totalCredit = supplierBills.sumOf { it.paidAmount }
                    )
                }

                // 4. Batch update everything
                _migrationStatus.value = "Saving new balances to Firestore..."
                val batch = firestore.batch()

                newVariants.forEach { v ->
                    batch.set(firestore.collection(ProductVariant.COLLECTION_NAME).document(v.id), v)
                }
                newSuppliers.forEach { s ->
                    batch.set(firestore.collection(Supplier.COLLECTION_NAME).document(s.id), s)
                }

                batch.commit().await()

                systemConfigRepository.setMigrationCompleted(true)

                _migrationStatus.value = "Migration completed successfully!"
            } catch (e: Exception) {
                Log.e("AdminToolsViewModel", "Migration failed", e)
                _migrationStatus.value = "Migration failed: ${e.message}"
            } finally {
                _isMigrating.value = false
            }
        }
    }
}
