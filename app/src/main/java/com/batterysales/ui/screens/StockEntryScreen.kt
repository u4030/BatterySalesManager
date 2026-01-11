package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.data.models.Product
import com.batterysales.viewmodel.StockEntryItem
import com.batterysales.viewmodel.StockEntryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockEntryScreen(
    navController: NavHostController,
    viewModel: StockEntryViewModel = hiltViewModel()
) {
    val costPerAmpere by viewModel.costPerAmpere.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val selectedWarehouse by viewModel.selectedWarehouse.collectAsState()
    val entryItems by viewModel.entryItems.collectAsState()
    val allProducts by viewModel.allProducts.collectAsState()
    var warehouseMenuExpanded by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدخال مخزون جديد", color = Color.White) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddProductDialog = true }) { Icon(Icons.Default.Add, contentDescription = "إضافة صنف") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(expanded = warehouseMenuExpanded, onExpandedChange = { warehouseMenuExpanded = !warehouseMenuExpanded }) {
                OutlinedTextField(value = selectedWarehouse?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("المستودع") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = warehouseMenuExpanded, onDismissRequest = { warehouseMenuExpanded = false }) {
                    warehouses.forEach { warehouse ->
                        DropdownMenuItem(text = { Text(warehouse.name) }, onClick = { viewModel.selectWarehouse(warehouse); warehouseMenuExpanded = false })
                    }
                }
            }

            OutlinedTextField(value = costPerAmpere, onValueChange = { viewModel.setCostPerAmpere(it) }, label = { Text("سعر التكلفة للأمبير") }, modifier = Modifier.fillMaxWidth())

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(entryItems) { item ->
                    StockEntryItemCard(item = item, onUpdate = viewModel::updateItem)
                }
            }

            Button(onClick = { /* TODO */ }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الإدخال") }
        }
    }

    if (showAddProductDialog) {
        AddProductToEntryDialog(
            products = allProducts.filter { p -> entryItems.none { it.product.id == p.id } },
            onDismiss = { showAddProductDialog = false },
            onAddProduct = { product -> viewModel.addEntryItem(product); showAddProductDialog = false }
        )
    }
}

@Composable
fun StockEntryItemCard(item: StockEntryItem, onUpdate: (StockEntryItem, String, String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.product.name)
            OutlinedTextField(value = item.quantity, onValueChange = { onUpdate(item, it, item.costPrice) }, label = { Text("الكمية") })
            OutlinedTextField(value = item.costPrice, onValueChange = { onUpdate(item, item.quantity, it) }, label = { Text("سعر التكلفة") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductToEntryDialog(products: List<Product>, onDismiss: () -> Unit, onAddProduct: (Product) -> Unit) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة صنف") }, text = {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = selectedProduct?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("اختر صنفًا") }, modifier = Modifier.menuAnchor())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                products.forEach { product ->
                    DropdownMenuItem(text = { Text(product.name) }, onClick = { selectedProduct = product; expanded = false })
                }
            }
        }
    }, confirmButton = { Button(onClick = { selectedProduct?.let(onAddProduct) }, enabled = selectedProduct != null) { Text("إضافة") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}
