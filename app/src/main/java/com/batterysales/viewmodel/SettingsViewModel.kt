package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.utils.SettingsManager
import com.batterysales.data.repositories.*
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val stockEntryRepository: StockEntryRepository,
    private val productRepository: ProductRepository,
    private val supplierRepository: SupplierRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    private val _migrationStatus = MutableStateFlow<String?>(null)
    val migrationStatus = _migrationStatus.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating = _isMigrating.asStateFlow()

    val fontSizeScale: StateFlow<Float> = settingsManager.fontSizeScale
    val isBold: StateFlow<Boolean> = settingsManager.isBold
    val scaleInputText: StateFlow<Boolean> = settingsManager.scaleInputText

    fun setFontSizeScale(scale: Float) {
        viewModelScope.launch {
            settingsManager.setFontSizeScale(scale)
        }
    }

    fun setIsBold(bold: Boolean) {
        viewModelScope.launch {
            settingsManager.setIsBold(bold)
        }
    }

    fun setScaleInputText(scale: Boolean) {
        viewModelScope.launch {
            settingsManager.setScaleInputText(scale)
        }
    }

    fun startDataMigration() {
        if (_isMigrating.value) return
        viewModelScope.launch {
            try {
                _isMigrating.value = true
                _migrationStatus.value = "جاري ترحيل البيانات... يرجى عدم إغلاق التطبيق"

                stockEntryRepository.migrateStockEntries()
                stockEntryRepository.migrateAllVariants(productRepository, supplierRepository, billRepository)

                settingsManager.setMigrationDone(true)
                _migrationStatus.value = "تم ترحيل البيانات بنجاح"
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Migration error", e)
                _migrationStatus.value = "فشل الترحيل: ${e.message}"
            } finally {
                _isMigrating.value = false
            }
        }
    }

    fun clearMigrationStatus() {
        _migrationStatus.value = null
    }
}
