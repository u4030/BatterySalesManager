package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batterysales.data.models.*
import com.batterysales.data.repositories.*
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val productVariantRepository: ProductVariantRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    fun runDiagnostic() {
        viewModelScope.launch {
            try {
                val variants = productVariantRepository.getAllVariants()
                val products = productRepository.getProductsOnce()
                val productsMap = products.associateBy { it.id }

                val groups = variants.groupBy { v ->
                    val pName = productsMap[v.productId]?.name ?: "Unknown"
                    "$pName | ${v.capacity}A | ${v.specification}"
                }

                Log.d("DIAGNOSTIC", "--- START INVENTORY DIAGNOSTIC ---")
                groups.forEach { (key, list) ->
                    if (list.size > 1) {
                        Log.d("DIAGNOSTIC", "DUPLICATE FOUND: $key")
                        list.forEach { v ->
                            Log.d("DIAGNOSTIC", "  -> ID: ${v.id}, Archived: ${v.archived}, Stock: ${v.currentStock}")
                        }
                    }
                }
                Log.d("DIAGNOSTIC", "--- END INVENTORY DIAGNOSTIC ---")
            } catch (e: Exception) {
                Log.e("DIAGNOSTIC", "Error", e)
            }
        }
    }
}
