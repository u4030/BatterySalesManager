package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse
import com.batterysales.data.models.User
import com.batterysales.data.repositories.ProductVariantRepository
import com.batterysales.data.repositories.WarehouseRepository
import com.batterysales.data.repositories.UserRepository
import com.batterysales.data.repositories.StockEntryRepository
import com.batterysales.data.paging.VariantPagingSource
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WarehouseUiState(
    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouseId: String = "",
    val isAdmin: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val variantRepository: ProductVariantRepository,
    private val warehouseRepository: WarehouseRepository,
    private val userRepository: UserRepository,
    private val stockEntryRepository: StockEntryRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarehouseUiState())
    val uiState: StateFlow<WarehouseUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val variants: Flow<PagingData<ProductVariant>> = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            Pager(PagingConfig(pageSize = 25)) {
                VariantPagingSource(firestore, query.ifBlank { null })
            }.flow.cachedIn(viewModelScope)
        }

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        userRepository.getCurrentUserFlow().onEach { user ->
            val isAdmin = user?.role == User.ROLE_ADMIN
            warehouseRepository.getWarehouses().take(1).collect { allWh ->
                val filteredWh = if (isAdmin) allWh else allWh.filter { it.id == user?.warehouseId }
                _uiState.update { it.copy(
                    warehouses = filteredWh,
                    isAdmin = isAdmin,
                    selectedWarehouseId = if (isAdmin) (filteredWh.firstOrNull()?.id ?: "") else user?.warehouseId ?: "",
                    isLoading = false
                ) }
            }
        }.launchIn(viewModelScope)
    }

    fun onWarehouseSelected(id: String) {
        _uiState.update { it.copy(selectedWarehouseId = id) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQuery.value = query
    }

    fun addWarehouse(name: String, location: String, isMain: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val warehouse = Warehouse(name = name, location = location, isMain = isMain)
                warehouseRepository.addWarehouse(warehouse)
                loadInitialData()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateWarehouse(warehouse: Warehouse) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                warehouseRepository.updateWarehouse(warehouse)
                loadInitialData()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteWarehouse(warehouseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                warehouseRepository.deleteWarehouse(warehouseId)
                loadInitialData()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
