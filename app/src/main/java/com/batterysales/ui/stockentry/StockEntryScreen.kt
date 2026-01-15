package com.batterysales.ui.stockentry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
    var expandedProduct by remember { mutableStateOf(false) }
    var expandedWarehouse by remember { mutableStateOf(false) }
    var showAddWarehouseDialog by remember { mutableStateOf(false) }
    var newWarehouseName by remember { mutableStateOf("") }

    if (showAddWarehouseDialog) {
        AlertDialog(
            onDismissRequest = { showAddWarehouseDialog = false },
            title = { Text("إضافة مستودع جديد") },
            text = {
                OutlinedTextField(
                    value = newWarehouseName,
                    onValueChange = { newWarehouseName = it },
                    label = { Text("اسم المستودع") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWarehouseName.isNotBlank()) {
                            viewModel.addWarehouse(newWarehouseName)
                            newWarehouseName = ""
                            showAddWarehouseDialog = false
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                Button(onClick = { showAddWarehouseDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row {
            // Warehouse Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedWarehouse,
                onExpandedChange = { expandedWarehouse = !expandedWarehouse },
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedWarehouse?.name ?: "",
                    onValueChange = {},
                    label = { Text("المستودع") },
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
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { showAddWarehouseDialog = true }) {
                Text("إضافة مستودع")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Total Cost and Amperes
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = viewModel.totalCost.value,
                onValueChange = { viewModel.totalCost.value = it },
                label = { Text("التكلفة الإجمالية") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = viewModel.totalAmperes.value,
                onValueChange = { /* Read-only */ },
                label = { Text("إجمالي الأمبيرات") },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add Product Section
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Product Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedProduct,
                onExpandedChange = { expandedProduct = !expandedProduct },
                modifier = Modifier.weight(2f)
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

            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("الكمية") },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    val product = selectedProduct
                    if (product != null) {
                        viewModel.addProductToEntry(product, quantity.toIntOrNull() ?: 0)
                        quantity = ""
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("إضافة")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stock Items List
        LazyColumn {
            items(viewModel.stockItems) { item ->
                ListItem(
                    headlineContent = { Text(item.product.name) },
                    supportingContent = { Text("الكمية: ${item.quantity}") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeProductFromEntry(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "إزالة")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                val warehouse = selectedWarehouse
                if (warehouse != null) {
                    viewModel.saveStockEntry(warehouse.id)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.stockItems.isNotEmpty() && selectedWarehouse != null
        ) {
            Text("حفظ إدخال المخزون")
        }
    }
}
