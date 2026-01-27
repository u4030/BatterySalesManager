package com.batterysales.ui.stocktransfer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.stockentry.Dropdown
import com.batterysales.viewmodel.StockTransferViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferScreen(
    navController: NavController,
    viewModel: StockTransferViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            navController.popBackStack()
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
            label = "المنتج",
            selectedValue = uiState.selectedProduct?.name ?: "",
            options = uiState.products.map { it.name },
            onOptionSelected = { index -> viewModel.onProductSelected(uiState.products[index]) },
            enabled = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        Dropdown(
            label = "الصنف (السعة)",
            selectedValue = uiState.selectedVariant?.let { "${it.capacity} أمبير" } ?: "",
            options = uiState.variants.map { "${it.capacity} أمبير" },
            onOptionSelected = { index -> viewModel.onVariantSelected(uiState.variants[index]) },
            enabled = uiState.selectedProduct != null
        )
        Spacer(modifier = Modifier.height(16.dp))

        Dropdown(
            label = "من المستودع",
            selectedValue = uiState.sourceWarehouse?.name ?: "",
            options = uiState.warehouses.map { it.name },
            onOptionSelected = { index -> viewModel.onSourceWarehouseSelected(uiState.warehouses[index]) },
            enabled = !uiState.isSourceWarehouseFixed
        )
        Spacer(modifier = Modifier.height(16.dp))

        Dropdown(
            label = "إلى المستودع",
            selectedValue = uiState.destinationWarehouse?.name ?: "",
            options = uiState.warehouses.map { it.name },
            onOptionSelected = { index -> viewModel.onDestinationWarehouseSelected(uiState.warehouses[index]) },
            enabled = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.quantity,
            onValueChange = viewModel::onQuantityChanged,
            label = { Text("الكمية") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = viewModel::onTransferStock,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectedVariant != null && uiState.sourceWarehouse != null && uiState.destinationWarehouse != null && uiState.quantity.isNotBlank()
        ) {
            Text("ترحيل المخزون")
        }
    }
}
