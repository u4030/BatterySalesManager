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
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val blockingConflictError: String? = null
)

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val warehouseRepository: WarehouseRepository,
    private val persistentSyncManager: com.batterysales.data.local.PersistentSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)

    fun dismissBlockingConflict() {
        _uiState.update { it.copy(blockingConflictError = null) }
        viewModelScope.launch {
            persistentSyncManager.removePendingOperation("CRITICAL_USER_CONFLICT")
        }
    }

    init {
        viewModelScope.launch {
            persistentSyncManager.pendingOperationsFlow.collect { ops ->
                val conflictOp = ops.find { it.operationId == "CRITICAL_USER_CONFLICT" && it.status == "PENDING_SYNC" }
                if (conflictOp != null && _uiState.value.blockingConflictError == null) {
                    _uiState.update { it.copy(blockingConflictError = conflictOp.payloadJson) }
                }
            }
        }

        refreshTrigger.onEach {
            loadData()
        }.launchIn(viewModelScope)
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Use snapshot listener for users as it's a small collection and changes are rare but important
                // However, for warehouses, use one-time fetch.
                val warehouses = warehouseRepository.getWarehousesOnce()
                userRepository.getAllUsersFlow().collect { users ->
                    _uiState.update { it.copy(
                        users = users,
                        warehouses = warehouses,
                        isLoading = false
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "خطأ في تحميل البيانات") }
            }
        }
    }

    fun refresh() {
        refreshTrigger.value += 1
    }

    fun updateUserRole(user: User, newRole: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val updatedUser = user.copy(role = newRole)
                userRepository.updateUser(updatedUser)
                _uiState.update { it.copy(successMessage = "تم تحديث الدور بنجاح") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تحديث الدور: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun toggleUserStatus(user: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val updatedUser = user.copy(isActive = !user.isActive)
                userRepository.updateUser(updatedUser)
                val statusText = if (updatedUser.isActive) "تنشيط" else "إيقاف"
                _uiState.update { it.copy(successMessage = "تم $statusText المستخدم بنجاح") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تحديث حالة المستخدم: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun togglePermission(user: User, permission: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val currentPermissions = user.permissions.toMutableList()
                if (currentPermissions.contains(permission)) {
                    currentPermissions.remove(permission)
                } else {
                    currentPermissions.add(permission)
                }
                val updatedUser = user.copy(permissions = currentPermissions)
                userRepository.updateUser(updatedUser)
                _uiState.update { it.copy(successMessage = "تم تحديث الصلاحيات") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تحديث الصلاحيات") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                userRepository.deleteUser(userId)
                _uiState.update { it.copy(successMessage = "تم حذف المستخدم بنجاح") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل حذف المستخدم: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun linkUserToWarehouse(user: User, warehouseId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                val updatedUser = user.copy(warehouseId = warehouseId)
                userRepository.updateUser(updatedUser)
                _uiState.update { it.copy(successMessage = "تم ربط المستودع بنجاح") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل ربط المستودع: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun addUserOptimistic(email: String, displayName: String, role: String, warehouseId: String?) {
        val tempUser = User(
            id = "temp_${System.currentTimeMillis()}",
            email = email,
            displayName = displayName,
            role = role,
            warehouseId = warehouseId,
            isActive = true
        )
        _uiState.update { it.copy(users = listOf(tempUser) + it.users.filter { u -> u.id != tempUser.id }) }
    }

    fun createUser(email: String, password: String, displayName: String, role: String, warehouseId: String?) {
        if (email.isBlank() || password.length < 6 || displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "الرجاء التأكد من صحة البيانات (كلمة المرور 6 أحرف على الأقل)") }
            return
        }

        val tempUser = User(
            id = "temp_${System.currentTimeMillis()}",
            email = email,
            displayName = displayName,
            role = role,
            warehouseId = warehouseId,
            isActive = true
        )
        val previousUsers = _uiState.value.users

        // الحقن التفاؤلي الفوري للمستخدم الجديد في القوائم المنسدلة والـ LazyColumn بـ (0ms)
        _uiState.update { it.copy(users = listOf(tempUser) + it.users, isSubmitting = true) }

        viewModelScope.launch {
            val opId = "user_add_${System.currentTimeMillis()}"
            try {
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = opId,
                        type = "USER_ADD",
                        relatedId = email,
                        status = "PENDING_SYNC"
                    )
                )

                userRepository.registerUser(email, password, displayName, role, warehouseId)
                persistentSyncManager.removePendingOperation(opId)
                _uiState.update { it.copy(
                    isSubmitting = false,
                    successMessage = "تم إنشاء المستخدم بنجاح"
                ) }
            } catch (e: Exception) {
                persistentSyncManager.removePendingOperation(opId)
                _uiState.update { it.copy(
                    users = previousUsers,
                    isSubmitting = false
                ) }
                val errorMsg = "تنبيه مزامنة حرج: فشل اعتماد إضافة المستخدم [$displayName] في السيرفر بسبب تعارض في البيانات أثناء انقطاع اتصالك. تم إعادة فهارس الكاش لحالتها الحقيقية تلقائياً، يرجى النقر لتأكيد الاطلاع وتوقيع التسوية يدوياً."
                _uiState.update { it.copy(blockingConflictError = errorMsg) }
                persistentSyncManager.recordPendingOperation(
                    com.batterysales.data.local.PendingSyncOperation(
                        operationId = "CRITICAL_USER_CONFLICT",
                        type = "CONFLICT_LOCK",
                        relatedId = email,
                        payloadJson = errorMsg,
                        status = "PENDING_SYNC"
                    )
                )
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
 
