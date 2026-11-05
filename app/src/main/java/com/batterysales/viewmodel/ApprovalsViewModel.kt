package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.StockEntry
import com.batterysales.data.models.Warehouse
import com.batterysales.data.models.ApprovalRequest
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
import com.batterysales.data.repositories.ApprovalRepository
import com.batterysales.data.repositories.UserRepository
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApprovalItem(
    val entry: StockEntry? = null,
    val request: ApprovalRequest? = null,
    val productName: String,
    val variantCapacity: String,
    val warehouseName: String = "",
    val type: String // STOCK_ENTRY, PRODUCT_REQUEST, VARIANT_REQUEST
)

@HiltViewModel
class ApprovalsViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val summaryRepository: com.batterysales.data.repositories.SummaryRepository,
    private val warehouseRepository: WarehouseRepository,
    private val approvalRepository: ApprovalRepository,
    private val persistentSyncManager: com.batterysales.data.local.PersistentSyncManager,
    private val billRepository: com.batterysales.data.repositories.BillRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _approvalItems = MutableStateFlow<List<ApprovalItem>>(emptyList())
    val approvalItems: StateFlow<List<ApprovalItem>> = _approvalItems.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private var currentUser: com.batterysales.data.models.User? = null

    init {
        userRepository.getCurrentUserFlow().onEach { currentUser = it }.launchIn(viewModelScope)
        loadPendingEntries()
    }

    private val refreshTrigger = MutableStateFlow(0)

    private fun loadPendingEntries() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val warehouses = try { warehouseRepository.getWarehousesOnce() } catch (e: Exception) { emptyList() }

                combine(
                    stockEntryRepository.getPendingEntriesFlow().catch { emit(emptyList()) },
                    approvalRepository.getPendingRequestsFlow().catch { emit(emptyList()) },
                    refreshTrigger
                ) { entries, requests, _ ->
                    val stockItems = entries.map { entry ->
                        val warehouse = warehouses.find { it.id == entry.warehouseId }

                        ApprovalItem(
                            entry = entry,
                            productName = entry.productName.ifEmpty { "منتج غير معروف" },
                            variantCapacity = if (entry.capacity > 0) "${entry.capacity}A" else "",
                            warehouseName = warehouse?.name ?: "مخزن غير معروف",
                            type = "STOCK_ENTRY"
                        )
                    }

                    val requestItems = requests.map { req ->
                        ApprovalItem(
                            request = req,
                            productName = req.productName,
                            variantCapacity = if (req.variantCapacity.isNotEmpty()) "${req.variantCapacity}A" else "",
                            type = if (req.targetType == ApprovalRequest.TARGET_PRODUCT) "PRODUCT_REQUEST" else "VARIANT_REQUEST"
                        )
                    }

                    (stockItems + requestItems).sortedByDescending {
                        it.entry?.timestamp?.time ?: it.request?.timestamp?.time ?: 0L
                    }
                }.catch { e ->
                    Log.e("ApprovalsViewModel", "Error combining approval flows", e)
                    emit(emptyList())
                }.collect { items ->
                    _approvalItems.value = items
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("ApprovalsViewModel", "Exception in loadPendingEntries", e)
                _approvalItems.value = emptyList()
                _isLoading.value = false
            }
        }
    }


    fun refresh() {
        refreshTrigger.value += 1
    }

    fun approveEntry(entryId: String) {
        // --- طرد تفاؤلي فوري للطلب من القائمة بـ (0ms) ---
        val previousItems = _approvalItems.value
        _approvalItems.update { it.filter { item -> item.entry?.id != entryId } }

        viewModelScope.launch {
            _isSubmitting.value = true
            val opId = "appr_entry_${System.currentTimeMillis()}_$entryId"
            try {
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "APPROVAL_ENTRY",
                        relatedId = entryId,
                        status = "PENDING_SYNC"
                    )
                )

                stockEntryRepository.approveEntry(entryId)
                
                // تحديث الروابط التلقائية للمورد بعد الموافقة
                stockEntryRepository.getStockEntryById(entryId)?.let { entry ->
                    if (entry.supplierId.isNotEmpty()) {
                        billRepository.autoLinkBillsForSupplier(entry.supplierId)
                    }
                }
                persistentSyncManager.removePendingOperation(opId)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _approvalItems.value = previousItems
                Log.e("ApprovalsViewModel", "Error approving entry", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun rejectEntryWithRollback(requestId: String) {
        val previousList = _approvalItems.value
        _approvalItems.update { it.filter { item -> item.entry?.id != requestId && item.request?.id != requestId } }
        viewModelScope.launch {
            try {
                val target = previousList.find { it.entry?.id == requestId || it.request?.id == requestId }
                if (target?.entry != null) {
                    stockEntryRepository.deleteStockEntry(requestId)
                } else {
                    approvalRepository.updateRequestStatus(requestId, ApprovalRequest.STATUS_REJECTED, currentUser?.id)
                }
            } catch (e: Exception) {
                _approvalItems.value = previousList // حارس الارتداد عند الفشل
                Log.e("ApprovalsViewModel", "Error in rejectEntryWithRollback", e)
            }
        }
    }

    fun rejectEntry(entryId: String) {
        val previousItems = _approvalItems.value
        _approvalItems.update { it.filter { item -> item.entry?.id != entryId } }

        viewModelScope.launch {
            _isSubmitting.value = true
            val opId = "rej_entry_${System.currentTimeMillis()}_$entryId"
            try {
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "REJECT_ENTRY",
                        relatedId = entryId,
                        status = "PENDING_SYNC"
                    )
                )

                stockEntryRepository.deleteStockEntry(entryId)
                persistentSyncManager.removePendingOperation(opId)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _approvalItems.value = previousItems
                Log.e("ApprovalsViewModel", "Error rejecting entry", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun approveRequestOptimistic(requestId: String) {
        val targetItem = _approvalItems.value.find { it.request?.id == requestId || it.entry?.id == requestId }
        if (targetItem?.entry != null) {
            approveEntry(targetItem.entry.id)
        } else if (targetItem?.request != null) {
            approveRequest(targetItem.request)
        }
    }

    fun approveRequest(request: ApprovalRequest) {
        // --- طرد تفاؤلي فوري للطلب من القائمة بـ (0ms) مع Rollback Guard ---
        val previousItems = _approvalItems.value
        _approvalItems.update { it.filter { item -> item.request?.id != request.id } }

        viewModelScope.launch {
            _isSubmitting.value = true
            val opId = "appr_req_${System.currentTimeMillis()}_${request.id}"
            try {
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "APPROVAL_REQUEST",
                        relatedId = request.id,
                        status = "PENDING_SYNC"
                    )
                )

                when (request.targetType) {
                    ApprovalRequest.TARGET_PRODUCT -> {
                        if (request.actionType == ApprovalRequest.ACTION_EDIT) {
                            request.productData?.let { productRepository.updateProduct(it) }
                        } else if (request.actionType == ApprovalRequest.ACTION_DELETE) {
                            val product = productRepository.getProduct(request.targetId)
                            product?.let { productRepository.updateProduct(it.copy(archived = true)) }
                        }
                    }
                    ApprovalRequest.TARGET_VARIANT -> {
                        if (request.actionType == ApprovalRequest.ACTION_EDIT) {
                            request.variantData?.let { productVariantRepository.updateVariant(it, summaryRepository) }
                        } else if (request.actionType == ApprovalRequest.ACTION_DELETE) {
                            val variant = productVariantRepository.getVariant(request.targetId)
                            variant?.let { productVariantRepository.updateVariant(it.copy(archived = true), summaryRepository) }
                        }
                    }
                }
                approvalRepository.updateRequestStatus(request.id, ApprovalRequest.STATUS_APPROVED, currentUser?.id)
                persistentSyncManager.removePendingOperation(opId)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _approvalItems.value = previousItems
                Log.e("ApprovalsViewModel", "Error approving request", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun rejectRequest(requestId: String) {
        val previousItems = _approvalItems.value
        _approvalItems.update { it.filter { item -> item.request?.id != requestId } }

        viewModelScope.launch {
            _isSubmitting.value = true
            val opId = "rej_req_${System.currentTimeMillis()}_$requestId"
            try {
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "REJECT_REQUEST",
                        relatedId = requestId,
                        status = "PENDING_SYNC"
                    )
                )

                approvalRepository.updateRequestStatus(requestId, ApprovalRequest.STATUS_REJECTED, currentUser?.id)
                persistentSyncManager.removePendingOperation(opId)
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _approvalItems.value = previousItems
                Log.e("ApprovalsViewModel", "Error rejecting request", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
 
