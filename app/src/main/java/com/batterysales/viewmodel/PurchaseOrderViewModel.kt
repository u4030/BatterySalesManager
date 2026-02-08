package com.batterysales.viewmodel

import androidx.lifecycle.ViewModel
import com.batterysales.data.repositories.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PurchaseOrderViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {
    // ViewModel logic here
}
