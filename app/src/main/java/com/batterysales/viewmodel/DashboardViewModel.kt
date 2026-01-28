package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.BillStatus
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.StockEntry
import com.batterysales.data.repositories.BillRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.StockEntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

data class DashboardUiState(
    val pendingApprovalsCount: Int = 0,
    val lowStockVariants: List<LowStockItem> = emptyList(),
    val upcomingBills: List<com.batterysales.data.models.Bill> = emptyList(),
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
    private val productRepository: com.batterysales.data.repositories.ProductRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        stockEntryRepository.getAllStockEntriesFlow(),
        productVariantRepository.getAllVariantsFlow(),
        productRepository.getProducts(),
        billRepository.getAllBillsFlow()
    ) { allEntries, allVariants, allProducts, allBills ->

        val pendingCount = allEntries.count { it.status == StockEntry.STATUS_PENDING }

        val productMap = allProducts.associateBy { it.id }
        val lowStock = allVariants.filter { !it.isArchived && it.minQuantity > 0 }.mapNotNull { variant ->
            val currentQty = allEntries.filter { it.productVariantId == variant.id && it.status == StockEntry.STATUS_APPROVED }.sumOf { it.quantity }
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

        val now = Calendar.getInstance()
        val nextWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }

        val upcoming = allBills.filter {
            it.status == BillStatus.UNPAID &&
            it.dueDate.after(now.time) &&
            it.dueDate.before(nextWeek.time)
        }.sortedBy { it.dueDate }

        DashboardUiState(
            pendingApprovalsCount = pendingCount,
            lowStockVariants = lowStock,
            upcomingBills = upcoming,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
