package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse
import com.batterysales.data.models.Supplier
import com.batterysales.data.repositories.ProductRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.WarehouseRepository
import com.batterysales.data.repositories.SupplierRepository
import com.batterysales.data.repositories.UserRepository
import com.batterysales.data.repositories.ApprovalRepository
import com.batterysales.data.models.ApprovalRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductManagementUiState(
    val products: List<Product> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val selectedProduct: Product? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductManagementViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val summaryRepository: com.batterysales.data.repositories.SummaryRepository,
    private val warehouseRepository: WarehouseRepository,
    private val supplierRepository: SupplierRepository,
    private val userRepository: UserRepository,
    private val approvalRepository: ApprovalRepository
) : ViewModel() {

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isSubmitting = MutableStateFlow(false)
    private val _barcodeFilter = MutableStateFlow("")
    private var currentUser: com.batterysales.data.models.User? = null

    init {
        userRepository.getCurrentUserFlow().onEach {
            currentUser = it
        }.launchIn(viewModelScope)
    }

    private val refreshTrigger = MutableStateFlow(0)

    private val staticData = flow {
        val warehouses = warehouseRepository.getWarehousesOnce()
        val suppliers = supplierRepository.getSuppliersOnce()
        emit(Pair(warehouses, suppliers))
    }

    private val localState = combine(
        _barcodeFilter,
        _selectedProduct,
        _errorMessage,
        _isSubmitting
    ) { barcode, selected, error, submitting ->
        LocalState(barcode, selected, error, submitting)
    }

    data class LocalState(
        val barcodeFilter: String,
        val selectedProduct: Product?,
        val errorMessage: String?,
        val isSubmitting: Boolean
    )

    val uiState: StateFlow<ProductManagementUiState> = combine(
        productRepository.getProducts(),
        productVariantRepository.getAllVariantsFlow(),
        localState,
        staticData
    ) { products: List<Product>, allVariants: List<ProductVariant>, local: LocalState, static: Pair<List<Warehouse>, List<Supplier>> ->
        val (warehouses, suppliers) = static

        val filteredProducts = if (local.barcodeFilter.isBlank()) {
            products.filter { !it.archived }
        } else {
            val matchingVariants = allVariants
                .filter { it.barcode.contains(local.barcodeFilter, ignoreCase = true) }
            val productIdsWithMatchingBarcode = matchingVariants.map { it.productId }.toSet()
            products.filter { !it.archived && (it.id in productIdsWithMatchingBarcode || it.name.contains(local.barcodeFilter, ignoreCase = true)) }
        }

        ProductManagementUiState(
            products = filteredProducts,
            selectedProduct = local.selectedProduct,
            warehouses = warehouses,
            suppliers = suppliers,
            isLoading = false,
            isSubmitting = local.isSubmitting,
            errorMessage = local.errorMessage
        )
    }.flatMapLatest { state ->
        if (state.selectedProduct != null) {
            productVariantRepository.getVariantsForProductFlow(state.selectedProduct.id)
                .map { variants ->
                    state.copy(variants = variants.filter { !it.archived }.sortedBy { it.capacity })
                }
        } else {
            flowOf(state.copy(variants = emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductManagementUiState(isLoading = true))

    fun refresh() {
        refreshTrigger.value += 1
    }

    fun onBarcodeFilterChanged(query: String) {
        _barcodeFilter.value = query
    }

    fun generateUniqueBarcode(): String {
        return System.currentTimeMillis().toString()
    }


    fun selectProduct(product: Product) {
        if (_selectedProduct.value?.id == product.id) {
            _selectedProduct.value = null
        } else {
            _selectedProduct.value = product
        }
    }

    fun addProduct(name: String, supplierId: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val existing = productRepository.getProductsOnce()
                if (existing.any { it.name.equals(name, ignoreCase = true) && !it.archived }) {
                    _errorMessage.value = "هذا المنتج موجود مسبقاً"
                    return@launch
                }

                val product = Product(name = name, supplierId = supplierId)
                if (product.isValid()) {
                    productRepository.addProduct(product)
                } else {
                    _errorMessage.value = product.getValidationError()
                }
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error adding product", e)
                _errorMessage.value = "Failed to add product: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun addVariant(capacity: Int, sellingPrice: Double, barcode: String, minQuantity: Int, minQuantities: Map<String, Int>, specification: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _selectedProduct.value?.let { product ->
                try {
                    val existing = productVariantRepository.getVariantsForProduct(product.id)
                    if (existing.any { it.capacity == capacity && it.specification.equals(specification, ignoreCase = true) && !it.archived }) {
                        _errorMessage.value = "هذه السعة والمواصفة موجودة مسبقاً لهذا المنتج"
                        return@launch
                    }

                    val variant = ProductVariant(
                        productId = product.id,
                        productName = product.name,
                        capacity = capacity,
                        sellingPrice = sellingPrice,
                        barcode = barcode,
                        minQuantity = minQuantity,
                        minQuantities = minQuantities,
                        specification = specification
                    )
                    if (variant.isValid()) {
                        productVariantRepository.addVariant(variant, summaryRepository)
                    } else {
                        _errorMessage.value = variant.getValidationError()
                    }
                } catch (e: Exception) {
                    Log.e("ProductMgmtVM", "Error adding variant", e)
                    _errorMessage.value = "Failed to add variant: ${e.message}"
                } finally {
                    _isSubmitting.value = false
                }
            } ?: run { _isSubmitting.value = false }
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val existing = productRepository.getProductsOnce()
                if (existing.any { it.name.equals(product.name, ignoreCase = true) && it.id != product.id && !it.archived }) {
                    _errorMessage.value = "يوجد منتج آخر بنفس هذا الاسم"
                    return@launch
                }

                if (!product.isValid()) {
                    _errorMessage.value = product.getValidationError()
                    return@launch
                }

                if (currentUser?.role == "seller") {
                    val oldProduct = productRepository.getProduct(product.id)
                    approvalRepository.addRequest(ApprovalRequest(
                        requesterId = currentUser?.id ?: "",
                        requesterName = currentUser?.displayName ?: "",
                        targetType = ApprovalRequest.TARGET_PRODUCT,
                        actionType = ApprovalRequest.ACTION_EDIT,
                        targetId = product.id,
                        productName = product.name,
                        productData = product,
                        oldProductData = oldProduct
                    ))
                    _errorMessage.value = "تم إرسال طلب التعديل للمدير للموافقة"
                } else {
                    productRepository.updateProduct(product)
                }
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error updating product", e)
                _errorMessage.value = "Failed to update product: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun archiveProduct(product: Product) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                if (currentUser?.role == "seller") {
                    val oldProduct = productRepository.getProduct(product.id)
                    approvalRepository.addRequest(ApprovalRequest(
                        requesterId = currentUser?.id ?: "",
                        requesterName = currentUser?.displayName ?: "",
                        targetType = ApprovalRequest.TARGET_PRODUCT,
                        actionType = ApprovalRequest.ACTION_DELETE,
                        targetId = product.id,
                        productName = product.name,
                        oldProductData = oldProduct
                    ))
                    _errorMessage.value = "تم إرسال طلب الحذف للمدير للموافقة"
                } else {
                    val archivedProduct = product.copy(archived = true)
                    productRepository.updateProduct(archivedProduct)

                    // Trigger removal of all variants from summaries when a product is archived
                    val variants = productVariantRepository.getVariantsForProduct(product.id)
                    variants.forEach { variant ->
                        val archivedVariant = variant.copy(archived = true)
                        productVariantRepository.updateVariant(archivedVariant, summaryRepository)
                    }

                    _selectedProduct.value = null // Deselect after archiving
                }
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error archiving product", e)
                _errorMessage.value = "Failed to archive product: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun updateVariant(variant: ProductVariant) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val product = productRepository.getProduct(variant.productId)
                val variantWithMeta = variant.copy(productName = product?.name ?: variant.productName)

                val existing = productVariantRepository.getVariantsForProduct(variant.productId)
                val isDuplicate = existing.any {
                    it.capacity == variant.capacity &&
                            it.specification.trim().equals(variant.specification.trim(), ignoreCase = true) &&
                            it.id != variant.id &&
                            !it.archived
                }

                if (isDuplicate) {
                    _errorMessage.value = "هذه السعة والمواصفة موجودة مسبقاً لهذا المنتج"
                    return@launch
                }

                if (!variant.isValid()) {
                    _errorMessage.value = variant.getValidationError()
                    return@launch
                }

                if (currentUser?.role == "seller") {
                    val oldVariant = productVariantRepository.getVariant(variant.id)
                    approvalRepository.addRequest(ApprovalRequest(
                        requesterId = currentUser?.id ?: "",
                        requesterName = currentUser?.displayName ?: "",
                        targetType = ApprovalRequest.TARGET_VARIANT,
                        actionType = ApprovalRequest.ACTION_EDIT,
                        targetId = variant.id,
                        productName = product?.name ?: "",
                        variantCapacity = variant.capacity.toString(),
                        variantData = variantWithMeta,
                        oldVariantData = oldVariant
                    ))
                    _errorMessage.value = "تم إرسال طلب التعديل للمدير للموافقة"
                } else {
                    productVariantRepository.updateVariant(variantWithMeta, summaryRepository)
                }
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error updating variant", e)
                _errorMessage.value = "Failed to update variant: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun archiveVariant(variant: ProductVariant) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                if (currentUser?.role == "seller") {
                    val product = productRepository.getProduct(variant.productId)
                    val oldVariant = productVariantRepository.getVariant(variant.id)
                    approvalRepository.addRequest(ApprovalRequest(
                        requesterId = currentUser?.id ?: "",
                        requesterName = currentUser?.displayName ?: "",
                        targetType = ApprovalRequest.TARGET_VARIANT,
                        actionType = ApprovalRequest.ACTION_DELETE,
                        targetId = variant.id,
                        productName = product?.name ?: "",
                        variantCapacity = variant.capacity.toString(),
                        oldVariantData = oldVariant
                    ))
                    _errorMessage.value = "تم إرسال طلب الحذف للمدير للموافقة"
                } else {
                    val archivedVariant = variant.copy(archived = true)
                    productVariantRepository.updateVariant(archivedVariant, summaryRepository)
                }
            } catch (e: Exception) {
                Log.e("ProductMgmtVM", "Error archiving variant", e)
                _errorMessage.value = "Failed to archive variant: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
 
