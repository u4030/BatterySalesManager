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
    private val warehouseRepository: WarehouseRepository,
    private val approvalRepository: ApprovalRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _approvalItems = MutableStateFlow<List<ApprovalItem>>(emptyList())
    val approvalItems: StateFlow<List<ApprovalItem>> = _approvalItems.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentUser: com.batterysales.data.models.User? = null

    init {
        userRepository.getCurrentUserFlow().onEach { currentUser = it }.launchIn(viewModelScope)
        loadPendingEntries()
    }

    private fun loadPendingEntries() {
        viewModelScope.launch {
            combine(
                stockEntryRepository.getPendingEntriesFlow(),
                approvalRepository.getPendingRequestsFlow(),
                productRepository.getProducts(),
                productVariantRepository.getAllVariantsFlow(),
                warehouseRepository.getWarehouses()
            ) { pendingEntries, requests, products, variants, warehouses ->
                val stockItems = pendingEntries.map { entry ->
                    val variant = variants.find { it.id == entry.productVariantId }
                    val product = products.find { it.id == variant?.productId }
                    val warehouse = warehouses.find { it.id == entry.warehouseId }

                    ApprovalItem(
                        entry = entry,
                        productName = product?.name ?: "منتج غير معروف",
                        variantCapacity = if (variant != null) "${variant.capacity}A" else "",
                        warehouseName = warehouse?.name ?: "مستودع غير معروف",
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

                (stockItems + requestItems).sortedByDescending { it.entry?.timestamp ?: it.request?.timestamp }
            }.collect { items ->
                _approvalItems.value = items
                _isLoading.value = false
            }
        }
    }

    fun approveEntry(entryId: String) {
        viewModelScope.launch {
            stockEntryRepository.approveEntry(entryId)
        }
    }

    fun rejectEntry(entryId: String) {
        viewModelScope.launch {
            stockEntryRepository.deleteStockEntry(entryId)
        }
    }

    fun approveRequest(request: ApprovalRequest) {
        viewModelScope.launch {
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
                            request.variantData?.let { productVariantRepository.updateVariant(it) }
                        } else if (request.actionType == ApprovalRequest.ACTION_DELETE) {
                            val variant = productVariantRepository.getVariant(request.targetId)
                            variant?.let { productVariantRepository.updateVariant(it.copy(archived = true)) }
                        }
                    }
                }
                approvalRepository.updateRequestStatus(request.id, ApprovalRequest.STATUS_APPROVED, currentUser?.id)
            } catch (e: Exception) {
                // Log or handle error
            }
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            approvalRepository.updateRequestStatus(requestId, ApprovalRequest.STATUS_REJECTED, currentUser?.id)
        }
    }
}
