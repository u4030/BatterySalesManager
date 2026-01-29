package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.BillStatus
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.StockEntry
import com.batterysales.data.repositories.BillRepository
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.repositories.WarehouseRepository
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
    val minQuantity: Int,
    val warehouseName: String
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stockEntryRepository: StockEntryRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productRepository: com.batterysales.data.repositories.ProductRepository,
    private val billRepository: BillRepository,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        stockEntryRepository.getAllStockEntriesFlow(),
        productVariantRepository.getAllVariantsFlow(),
        productRepository.getProducts(),
        billRepository.getAllBillsFlow(),
        warehouseRepository.getWarehouses()
    ) { allEntries, allVariants, allProducts, allBills, allWarehouses ->

        val pendingCount = allEntries.count { it.status == StockEntry.STATUS_PENDING }

        val productMap = allProducts.associateBy { it.id }

        // Optimize: Group approved entries by Pair(variantId, warehouseId)
        val stockMap = allEntries.filter { it.status == StockEntry.STATUS_APPROVED }
            .groupBy { Pair(it.productVariantId, it.warehouseId) }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }

        val lowStock = mutableListOf<LowStockItem>()

        val activeVariants = allVariants.filter { !it.archived && it.minQuantity > 0 }

        for (variant in activeVariants) {
            val product = productMap[variant.productId] ?: continue

            for (warehouse in allWarehouses) {
                val currentQty = stockMap[Pair(variant.id, warehouse.id)] ?: 0
                if (currentQty <= variant.minQuantity) {
                    lowStock.add(
                        LowStockItem(
                            variantId = variant.id,
                            productName = product.name,
                            capacity = variant.capacity,
                            currentQuantity = currentQty,
                            minQuantity = variant.minQuantity,
                            warehouseName = warehouse.name
                        )
                    )
                }
            }
        }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextWeek = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val upcoming = allBills.filter {
            it.status != BillStatus.PAID &&
                    !it.dueDate.before(today.time) &&
                    !it.dueDate.after(nextWeek.time)
        }.sortedBy { it.dueDate }

        DashboardUiState(
            pendingApprovalsCount = pendingCount,
            lowStockVariants = lowStock,
            upcomingBills = upcoming,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
