package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import com.batterysales.data.models.ProductVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PrintQueueItem(
    val productName: String,
    val variant: ProductVariant,
    val quantity: Int = 1
)

@HiltViewModel
class PrintQueueViewModel @Inject constructor() : ViewModel() {

    private val _queue = MutableStateFlow<List<PrintQueueItem>>(emptyList())
    val queue: StateFlow<List<PrintQueueItem>> = _queue.asStateFlow()

    fun addItem(productName: String, variant: ProductVariant, quantity: Int = 1) {
        _queue.update { current ->
            val existing = current.find { it.variant.id == variant.id }
            if (existing != null) {
                current.map {
                    if (it.variant.id == variant.id) it.copy(quantity = it.quantity + quantity) else it
                }
            } else {
                current + PrintQueueItem(productName, variant, quantity)
            }
        }
    }

    fun removeItem(variantId: String) {
        _queue.update { current -> current.filter { it.variant.id != variantId } }
    }

    fun updateQuantity(variantId: String, newQuantity: Int) {
        if (newQuantity <= 0) {
            removeItem(variantId)
            return
        }
        _queue.update { current ->
            current.map {
                if (it.variant.id == variantId) it.copy(quantity = newQuantity) else it
            }
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }

    fun getItemsForPrinting(): List<Pair<String, ProductVariant>> {
        return _queue.value.flatMap { item ->
            List(item.quantity) { Pair(item.productName, item.variant) }
        }
    }
}
