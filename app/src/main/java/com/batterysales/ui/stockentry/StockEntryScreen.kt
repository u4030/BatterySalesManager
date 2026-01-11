package com.batterysales.ui.stockentry

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
fun StockEntryScreen(
    viewModel: StockEntryViewModel = hiltViewModel()
) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var quantity by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var expandedProduct by remember { mutableStateOf(false) }
    var expandedWarehouse by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expandedProduct,
            onExpandedChange = { expandedProduct = !expandedProduct }
        ) {
            TextField(
                modifier = Modifier.menuAnchor(),
                readOnly = true,
                value = selectedProduct?.name ?: "",
                onValueChange = {},
                label = { Text("Product") },
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

        ExposedDropdownMenuBox(
            expanded = expandedWarehouse,
            onExpandedChange = { expandedWarehouse = !expandedWarehouse }
        ) {
            TextField(
                modifier = Modifier.menuAnchor(),
                readOnly = true,
                value = selectedWarehouse?.name ?: "",
                onValueChange = {},
                label = { Text("Warehouse") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWarehouse) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expandedWarehouse,
                onDismissRequest = { expandedWarehouse = false },
            ) {
                viewModel.warehouses.value.forEach { warehouse ->
                    DropdownMenuItem(
                        text = { Text(warehouse.name) },
                        onClick = {
                            selectedWarehouse = warehouse
                            expandedWarehouse = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it },
            label = { Text("Quantity") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = costPrice,
            onValueChange = { costPrice = it },
            label = { Text("Cost Price") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val product = selectedProduct
                val warehouse = selectedWarehouse
                if (product != null && warehouse != null) {
                    viewModel.addStockEntry(
                        productId = product.id,
                        warehouseId = warehouse.id,
                        quantity = quantity.toIntOrNull() ?: 0,
                        costPrice = costPrice.toDoubleOrNull() ?: 0.0
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Stock Entry")
        }
    }
}
