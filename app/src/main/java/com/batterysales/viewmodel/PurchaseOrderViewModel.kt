package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.local.PendingSyncOperation
import com.batterysales.data.local.PersistentSyncManager
import com.batterysales.data.models.StockEntry
import com.batterysales.data.repositories.BillRepository
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.StockEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseOrderUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PurchaseOrderViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val billRepository: BillRepository,
    private val persistentSyncManager: PersistentSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseOrderUiState())
    val uiState = _uiState.asStateFlow()

    fun processPurchaseReturn(entry: StockEntry, returnQty: Int, notes: String = "") {
        if (returnQty <= 0) return
        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            val opId = "po_ret_${System.currentTimeMillis()}_${entry.id}"
            try {
                persistentSyncManager.recordPendingOperation(
                    PendingSyncOperation(
                        operationId = opId,
                        type = "PURCHASE_RETURN",
                        relatedId = entry.id,
                        status = "PENDING_SYNC"
                    )
                )

                stockEntryRepository.processReturn(entry, returnQty, "PURCHASE_RETURN", notes)
                // ربط الكمبيالة المتأثرة بالطلب لتوسيمها تلقائياً بدون حذف
                billRepository.tagBillsForPurchaseReturn(
                    entryId = entry.id,
                    orderId = entry.orderId,
                    invoiceNumber = entry.invoiceNumber,
                    warningNote = "تنبيه: تم إرجاع مواد من هذه الطلبية - يرجى تعديل قيمة الكمبيالة أو إلغاؤها يدوياً"
                )
                persistentSyncManager.removePendingOperation(opId)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _uiState.update { it.copy(errorMessage = "فشل معالجة المرتجع: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
 
