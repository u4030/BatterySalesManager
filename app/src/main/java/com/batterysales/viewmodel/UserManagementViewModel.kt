package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.User
import com.batterysales.data.models.Warehouse
import com.batterysales.data.repositories.UserRepository
import com.batterysales.data.repositories.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserManagementUiState(
    val users: List<User> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),
    val isLoading: Boolean = true,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userRepository.getAllUsersFlow(),
                warehouseRepository.getWarehouses()
            ) { users, warehouses ->
                _uiState.update { it.copy(
                    users = users,
                    warehouses = warehouses,
                    isLoading = false
                ) }
            }.collect()
        }
    }

    fun updateUserRole(user: User, newRole: String) {
        viewModelScope.launch {
            try {
                val updatedUser = user.copy(role = newRole)
                userRepository.updateUser(updatedUser)
                _uiState.update { it.copy(successMessage = "تم تحديث الدور بنجاح") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تحديث الدور: ${e.message}") }
            }
        }
    }

    fun linkUserToWarehouse(user: User, warehouseId: String?) {
        viewModelScope.launch {
            try {
                val updatedUser = user.copy(warehouseId = warehouseId)
                userRepository.updateUser(updatedUser)
                _uiState.update { it.copy(successMessage = "تم ربط المستودع بنجاح") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل ربط المستودع: ${e.message}") }
            }
        }
    }

    fun createUser(email: String, password: String, displayName: String, role: String, warehouseId: String?) {
        viewModelScope.launch {
            if (email.isBlank() || password.length < 6 || displayName.isBlank()) {
                _uiState.update { it.copy(errorMessage = "الرجاء التأكد من صحة البيانات (كلمة المرور 6 أحرف على الأقل)") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            try {
                userRepository.registerUser(email, password, displayName, role, warehouseId)
                _uiState.update { it.copy(
                    isLoading = false,
                    successMessage = "تم إنشاء المستخدم بنجاح"
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "فشل إنشاء المستخدم: ${e.message}"
                ) }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
