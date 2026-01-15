package com.batterysales.ui.productmanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProductManagementScreen(
    viewModel: ProductManagementViewModel = hiltViewModel()
) {
    var showAddProductDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddProductDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منتج")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.products.value) { product ->
                ListItem(
                    headlineContent = { Text(product.name) },
                    supportingContent = { Text(product.productType) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { /* Handle edit */ }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل")
                            }
                            IconButton(onClick = { viewModel.deleteProduct(product.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف")
                            }
                        }
                    }
                )
            }
        }

        if (showAddProductDialog) {
            AddProductDialog(
                onDismiss = { showAddProductDialog = false },
                onAddProduct = { name, capacity, productType, barcode, sellingPrice ->
                    viewModel.addProduct(name, capacity, productType, barcode, sellingPrice)
                    showAddProductDialog = false
                }
            )
        }
    }
}

@Composable
private fun AddProductDialog(
    onDismiss: () -> Unit,
    onAddProduct: (String, Int, String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var productType by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة منتج") },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") })
                TextField(value = capacity, onValueChange = { capacity = it }, label = { Text("السعة") })
                TextField(value = productType, onValueChange = { productType = it }, label = { Text("نوع المنتج") })
                TextField(value = barcode, onValueChange = { barcode = it }, label = { Text("الباركود") })
                TextField(value = sellingPrice, onValueChange = { sellingPrice = it }, label = { Text("سعر البيع") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddProduct(name, capacity.toIntOrNull() ?: 0, productType, barcode, sellingPrice.toDoubleOrNull() ?: 0.0)
                }
            ) {
                Text("إضافة")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
