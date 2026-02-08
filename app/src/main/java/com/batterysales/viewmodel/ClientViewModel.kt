package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.Client
import com.batterysales.data.repositories.ClientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val clientRepository: ClientRepository
) : ViewModel() {

    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients = _clients.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    init {
        loadClients()
    }

    fun loadClients() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _clients.value = clientRepository.getAllClients()
            } catch (e: Exception) {
                _errorMessage.value = "فشل في تحميل العملاء"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addClient(name: String, phone: String, email: String, address: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val client = Client(name = name, phone = phone, email = email, address = address)
                clientRepository.addClient(client)
                _successMessage.value = "تم إضافة العميل بنجاح"
                loadClients()
            } catch (e: Exception) {
                _errorMessage.value = "فشل في إضافة العميل"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteClient(clientId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                clientRepository.deleteClient(clientId)
                _successMessage.value = "تم حذف العميل بنجاح"
                loadClients()
            } catch (e: Exception) {
                _errorMessage.value = "فشل في حذف العميل"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
