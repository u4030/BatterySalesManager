package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.StockEntry
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.StockEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardUiState(
    val pendingApprovalsCount: Int = 0,
    val lowStockVariants: List<LowStockItem> = emptyList(),
    val isLoading: Boolean = true
)

data class LowStockItem(
    val variantId: String,
    val productName: String,
    val capacity: Int,
    val currentQuantity: Int,
    val minQuantity: Int
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productRepository: com.batterysales.data.repositories.ProductRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        stockEntryRepository.getAllStockEntriesFlow(),
        productVariantRepository.getAllVariantsFlow(),
        productRepository.getProducts()
    ) { allEntries, allVariants, allProducts ->

        val pendingCount = allEntries.count { it.status == "pending" }

        val productMap = allProducts.associateBy { it.id }
        val lowStock = allVariants.filter { !it.isArchived && it.minQuantity > 0 }.mapNotNull { variant ->
            val currentQty = allEntries.filter { it.productVariantId == variant.id && it.status == "approved" }.sumOf { it.quantity }
            if (currentQty <= variant.minQuantity) {
                val product = productMap[variant.productId]
                LowStockItem(
                    variantId = variant.id,
                    productName = product?.name ?: "Unknown",
                    capacity = variant.capacity,
                    currentQuantity = currentQty,
                    minQuantity = variant.minQuantity
                )
            } else {
                null
            }
        }

        DashboardUiState(
            pendingApprovalsCount = pendingCount,
            lowStockVariants = lowStock,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
