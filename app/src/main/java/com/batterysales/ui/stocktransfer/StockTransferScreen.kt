package com.batterysales.ui.stocktransfer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.batterysales.data.models.Product
import com.batterysales.data.models.Warehouse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferScreen(
    viewModel: StockTransferViewModel = hiltViewModel()
) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var sourceWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var destinationWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var quantity by remember { mutableStateOf("") }
    var expandedProduct by remember { mutableStateOf(false) }
    var expandedSourceWarehouse by remember { mutableStateOf(false) }
    var expandedDestinationWarehouse by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        // Product Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedProduct,
            onExpandedChange = { expandedProduct = !expandedProduct }
        ) {
            TextField(
                modifier = Modifier.menuAnchor(),
                readOnly = true,
                value = selectedProduct?.name ?: "",
                onValueChange = {},
                label = { Text("المنتج") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
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
                            expandedProduct = false
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
                modifier = Modifier.menuAnchor(),
                readOnly = true,
                value = sourceWarehouse?.name ?: "",
                onValueChange = {},
                label = { Text("من المستودع") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSourceWarehouse) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
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
                modifier = Modifier.menuAnchor(),
                readOnly = true,
                value = destinationWarehouse?.name ?: "",
                onValueChange = {},
                label = { Text("إلى المستودع") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDestinationWarehouse) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
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
                val product = selectedProduct
                val source = sourceWarehouse
                val destination = destinationWarehouse
                if (product != null && source != null && destination != null && source.id != destination.id) {
                    viewModel.transferStock(
                        productId = product.id,
                        sourceWarehouseId = source.id,
                        destinationWarehouseId = destination.id,
                        quantity = quantity.toIntOrNull() ?: 0
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ترحيل المخزون")
        }
    }
}
