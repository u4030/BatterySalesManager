package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.stockentry.Dropdown
import com.batterysales.viewmodel.SalesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(navController: NavController, viewModel: SalesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paidAmount by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            // Reset local fields or navigate away
            customerName = ""
            customerPhone = ""
            paidAmount = ""
            // Maybe navigate back or show a success message
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {

        if (uiState.errorMessage != null) {
            Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.isLoading) {
            CircularProgressIndicator()
        }

        Dropdown(
            label = "اختر المنتج",
            selectedValue = uiState.selectedProduct?.name ?: "",
            options = uiState.products.map { it.name },
            onOptionSelected = { index -> viewModel.onProductSelected(uiState.products[index]) },
            enabled = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        Dropdown(
            label = "اختر الصنف (السعة)",
            selectedValue = uiState.selectedVariant?.let { "${it.capacity} أمبير" } ?: "",
            options = uiState.variants.map { "${it.capacity} أمبير" },
            onOptionSelected = { index -> viewModel.onVariantSelected(uiState.variants[index]) },
            enabled = uiState.selectedProduct != null
        )
        Spacer(modifier = Modifier.height(16.dp))

        Dropdown(
            label = "اختر المستودع",
            selectedValue = uiState.selectedWarehouse?.name ?: "",
            options = uiState.warehouses.map { it.name },
            onOptionSelected = { index -> viewModel.onWarehouseSelected(uiState.warehouses[index]) },
            enabled = true
        )

        Spacer(modifier = Modifier.height(8.dp))
        val availableQty = uiState.selectedVariant?.let { uiState.stockLevels[Pair(it.id, uiState.selectedWarehouse?.id ?: "")] ?: 0 } ?: 0
        Text("الكمية المتاحة: $availableQty")


        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = uiState.quantity, onValueChange = viewModel::onQuantityChanged, label = { Text("الكمية") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = uiState.sellingPrice, onValueChange = viewModel::onSellingPriceChanged, label = { Text("سعر البيع") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("اسم العميل") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("رقم هاتف العميل") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = paidAmount, onValueChange = { paidAmount = it }, label = { Text("المبلغ المدفوع") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                viewModel.createSale(
                    customerName = customerName,
                    customerPhone = customerPhone,
                    paidAmount = paidAmount.toDoubleOrNull() ?: 0.0
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectedVariant != null && uiState.selectedWarehouse != null
        ) {
            Text("إنشاء عملية بيع")
        }
    }
}
