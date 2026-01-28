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

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _warehouses = MutableStateFlow<List<Warehouse>>(emptyList())
    val warehouses: StateFlow<List<Warehouse>> = _warehouses.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userRepository.getAllUsersFlow(),
                warehouseRepository.getWarehouses()
            ) { users, warehouses ->
                _users.value = users
                _warehouses.value = warehouses
                _isLoading.value = false
            }.collect()
        }
    }

    fun updateUserRole(user: User, newRole: String) {
        viewModelScope.launch {
            val updatedUser = user.copy(role = newRole)
            userRepository.updateUser(updatedUser)
        }
    }

    fun linkUserToWarehouse(user: User, warehouseId: String?) {
        viewModelScope.launch {
            val updatedUser = user.copy(warehouseId = warehouseId)
            userRepository.updateUser(updatedUser)
        }
    }

    fun createUser(email: String, password: String, displayName: String, role: String, warehouseId: String?) {
        viewModelScope.launch {
            try {
                userRepository.registerUser(email, password, displayName, role, warehouseId)
                // Note: On success, the current session will be switched to the new user.
                // The Admin will need to log back in.
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
