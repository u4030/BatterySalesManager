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
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                stockEntryRepository.approveEntry(entryId)

                // تحديث الروابط التلقائية للمورد بعد الموافقة
                stockEntryRepository.getStockEntryById(entryId)?.let { entry ->
                    if (entry.supplierId.isNotEmpty()) {
                        billRepository.autoLinkBillsForSupplier(entry.supplierId)
                    }
                }
            } catch (e: Exception) {
                Log.e("ApprovalsViewModel", "Error approving entry", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun rejectEntry(entryId: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                stockEntryRepository.deleteStockEntry(entryId)
            } catch (e: Exception) {
                Log.e("ApprovalsViewModel", "Error rejecting entry", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun approveRequest(request: ApprovalRequest) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
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
            } catch (e: Exception) {
                Log.e("ApprovalsViewModel", "Error approving request", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                approvalRepository.updateRequestStatus(requestId, ApprovalRequest.STATUS_REJECTED, currentUser?.id)
            } catch (e: Exception) {
                Log.e("ApprovalsViewModel", "Error rejecting request", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
 
