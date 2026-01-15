package com.batterysales.ui.stocktransfer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferScreen(
    viewModel: StockTransferViewModel = hiltViewModel()
) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedVariant by remember { mutableStateOf<ProductVariant?>(null) }
    var sourceWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var destinationWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var quantity by remember { mutableStateOf("") }

    var expandedProduct by remember { mutableStateOf(false) }
    var expandedVariant by remember { mutableStateOf(false) }
    var expandedSourceWarehouse by remember { mutableStateOf(false) }
    var expandedDestinationWarehouse by remember { mutableStateOf(false) }

    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null || successMessage != null) {
            // You can show a Snackbar or Toast here
            // For now, we'll just rely on the Text composable
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        if (successMessage != null) {
            Text(text = successMessage!!, color = MaterialTheme.colorScheme.primary)
        }

        // Product Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedProduct,
            onExpandedChange = { expandedProduct = !expandedProduct }
        ) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = selectedProduct?.name ?: "",
                onValueChange = {},
                label = { Text("المنتج") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
            )
            ExposedDropdownMenu(
                expanded = expandedProduct,
                onDismissRequest = { expandedProduct = false },
            ) {
                viewModel.products.value.forEach { product ->
                    DropdownMenuItem(
                        text = { Text(product.name) },
                        onClick = {
                            selectedProduct = product
                            selectedVariant = null // Reset variant
                            viewModel.fetchVariantsForProduct(product.id)
                            expandedProduct = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Variant Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedVariant,
            onExpandedChange = { expandedVariant = !expandedVariant }
        ) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = selectedVariant?.let { "${it.capacity} أمبير" } ?: "",
                onValueChange = {},
                label = { Text("الصنف (السعة)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVariant) },
                enabled = selectedProduct != null
            )
            ExposedDropdownMenu(
                expanded = expandedVariant,
                onDismissRequest = { expandedVariant = false },
            ) {
                viewModel.variants.value.forEach { variant ->
                    DropdownMenuItem(
                        text = { Text("${variant.capacity} أمبير") },
                        onClick = {
                            selectedVariant = variant
                            expandedVariant = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Source Warehouse Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedSourceWarehouse,
            onExpandedChange = { expandedSourceWarehouse = !expandedSourceWarehouse }
        ) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = sourceWarehouse?.name ?: "",
                onValueChange = {},
                label = { Text("من المستودع") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSourceWarehouse) },
            )
            ExposedDropdownMenu(
                expanded = expandedSourceWarehouse,
                onDismissRequest = { expandedSourceWarehouse = false },
            ) {
                viewModel.warehouses.value.forEach { warehouse ->
                    DropdownMenuItem(
                        text = { Text(warehouse.name) },
                        onClick = {
                            sourceWarehouse = warehouse
                            expandedSourceWarehouse = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Destination Warehouse Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedDestinationWarehouse,
            onExpandedChange = { expandedDestinationWarehouse = !expandedDestinationWarehouse }
        ) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = destinationWarehouse?.name ?: "",
                onValueChange = {},
                label = { Text("إلى المستودع") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDestinationWarehouse) },
            )
            ExposedDropdownMenu(
                expanded = expandedDestinationWarehouse,
                onDismissRequest = { expandedDestinationWarehouse = false },
            ) {
                viewModel.warehouses.value.forEach { warehouse ->
                    DropdownMenuItem(
                        text = { Text(warehouse.name) },
                        onClick = {
                            destinationWarehouse = warehouse
                            expandedDestinationWarehouse = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it },
            label = { Text("الكمية") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.clearMessages()
                val variant = selectedVariant
                val source = sourceWarehouse
                val destination = destinationWarehouse
                if (variant != null && source != null && destination != null) {
                    viewModel.transferStock(
                        productVariantId = variant.id,
                        sourceWarehouseId = source.id,
                        destinationWarehouseId = destination.id,
                        quantity = quantity.toIntOrNull() ?: 0
                    )
                } else {
                    // Optionally, you can set an error message in the ViewModel
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedVariant != null && sourceWarehouse != null && destinationWarehouse != null && quantity.isNotBlank()
        ) {
            Text("ترحيل المخزون")
        }
    }
}
