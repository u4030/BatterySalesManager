package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.User
import com.batterysales.data.repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.Log
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val warehouseRepository: com.batterysales.data.repositories.WarehouseRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        checkUserStatus()
    }

    fun checkUserStatus() {
        viewModelScope.launch {
            try {
                if (userRepository.isUserLoggedIn()) {
                    fetchCurrentUser()
                } else {
                    _isLoggedIn.value = false
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error checking user status", e)
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                userRepository.loginUser(email, password)
                fetchCurrentUser()
                _isLoggedIn.value = true
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login error", e)
                _errorMessage.value = e.message ?: "فشل تسجيل الدخول"
                _isLoggedIn.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                userRepository.registerUser(email, password, displayName)
                fetchCurrentUser()
                _isLoggedIn.value = true
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Registration error", e)
                _errorMessage.value = e.message ?: "فشل إنشاء الحساب"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchCurrentUser() {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                if (user != null) {
                    if (!user.isActive) {
                        _errorMessage.value = "عذراً، هذا الحساب موقوف حالياً. يرجى مراجعة الإدارة."
                        userRepository.logout()
                        _currentUser.value = null
                        _isLoggedIn.value = false
                        return@launch
                    }

                    if (user.role == "seller" && user.warehouseId != null) {
                        val warehouse = warehouseRepository.getWarehouse(user.warehouseId)
                        if (warehouse != null && !warehouse.isActive) {
                            _errorMessage.value = "عذراً، المستودع المرتبط بحسابك متوقف حالياً. يرجى مراجعة الإدارة."
                            userRepository.logout()
                            _currentUser.value = null
                            _isLoggedIn.value = false
                            return@launch
                        }
                    }
                }
                _currentUser.value = user
                _isLoggedIn.value = _currentUser.value != null
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error fetching current user", e)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.logout()
            _currentUser.value = null
            _isLoggedIn.value = false
            _errorMessage.value = null
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
