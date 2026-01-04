package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.User
import com.batterysales.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository
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
            if (userRepository.isUserLoggedIn()) {
                fetchCurrentUser()
            } else {
                _isLoggedIn.value = false
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            userRepository.loginUser(email, password)
                .onSuccess {
                    fetchCurrentUser()
                    _isLoggedIn.value = true
                }
                .onFailure {
                    _errorMessage.value = it.message ?: "فشل تسجيل الدخول"
                    _isLoggedIn.value = false
                }
            _isLoading.value = false
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            userRepository.registerUser(email, password, displayName)
                .onSuccess {
                    fetchCurrentUser()
                    _isLoggedIn.value = true
                }
                .onFailure {
                    _errorMessage.value = it.message ?: "فشل إنشاء الحساب"
                }
            _isLoading.value = false
        }
    }

    private fun fetchCurrentUser() {
        viewModelScope.launch {
            userRepository.getCurrentUser()
                .onSuccess {
                    _currentUser.value = it
                    _isLoggedIn.value = it != null
                }
        }
    }

    fun logout() {
        userRepository.logout()
        _currentUser.value = null
        _isLoggedIn.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
