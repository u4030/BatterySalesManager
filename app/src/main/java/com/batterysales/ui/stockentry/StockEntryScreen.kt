package com.batterysales.ui.stockentry

import androidx.compose.foundation.clickable
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
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockEntryScreen(
    viewModel: StockEntryViewModel = hiltViewModel()
) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedVariant by remember { mutableStateOf<ProductVariant?>(null) }
    var selectedWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var quantity by remember { mutableStateOf("") }
    var costPerAmpere by remember { mutableStateOf("") }
    var manualCostPrice by remember { mutableStateOf("") }

    var expandedProduct by remember { mutableStateOf(false) }
    var expandedVariant by remember { mutableStateOf(false) }
    var expandedWarehouse by remember { mutableStateOf(false) }

    var showAddWarehouseDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<StockEntryItem?>(null) }
    var supplierName by remember { mutableStateOf("") }

    if (showAddWarehouseDialog) {
        AddWarehouseDialog(
            onDismiss = { showAddWarehouseDialog = false },
            onAddWarehouse = { name -> viewModel.addWarehouse(name) }
        )
    }

    itemToEdit?.let { item ->
        EditQuantityDialog(
            item = item,
            onDismiss = { itemToEdit = null },
            onConfirm = { newQuantity ->
                viewModel.updateItemQuantity(item, newQuantity)
                itemToEdit = null
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // Warehouse Dropdown
        Row {
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

        // Product and Variant Selection
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(expanded = expandedProduct, onExpandedChange = { expandedProduct = !expandedProduct }, modifier = Modifier.weight(1f)) {
                TextField(
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedProduct?.name ?: "",
                    onValueChange = {},
                    label = { Text("المنتج") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
                )
                ExposedDropdownMenu(expanded = expandedProduct, onDismissRequest = { expandedProduct = false }) {
                    viewModel.products.value.forEach { product ->
                        DropdownMenuItem(
                            text = { Text(product.name) },
                            onClick = {
                                selectedProduct = product
                                selectedVariant = null
                                viewModel.fetchVariantsForProduct(product.id)
                                expandedProduct = false
                            }
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = expandedVariant, onExpandedChange = { expandedVariant = !expandedVariant }, modifier = Modifier.weight(1f)) {
                TextField(
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedVariant?.capacity?.toString()?.let { "$it أمبير" } ?: "",
                    onValueChange = {},
                    label = { Text("السعة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVariant) },
                    enabled = selectedProduct != null
                )
                ExposedDropdownMenu(expanded = expandedVariant, onDismissRequest = { expandedVariant = false }) {
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Derived states for automatic calculation ---
        val currentQuantity = quantity.toIntOrNull() ?: 0
        val singleItemCost = manualCostPrice.toDoubleOrNull() ?: 0.0

        val totalAmperes = (currentQuantity * (selectedVariant?.capacity ?: 0)).toString()
        val totalCost = String.format("%.2f", currentQuantity * singleItemCost)


        // --- Cost & Quantity Calculation Fields ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row for Per-Item Cost
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = costPerAmpere,
                    onValueChange = { newCostPerAmpere ->
                        costPerAmpere = newCostPerAmpere
                        if (newCostPerAmpere.isEmpty()) {
                            manualCostPrice = ""
                        } else {
                            val costPerAmpereDouble = newCostPerAmpere.toDoubleOrNull()
                            val variantCapacity = selectedVariant?.capacity
                            if (costPerAmpereDouble != null && variantCapacity != null) {
                                val newManualCost = String.format("%.2f", costPerAmpereDouble * variantCapacity)
                                if (newManualCost != manualCostPrice) {
                                    manualCostPrice = newManualCost
                                }
                            }
                        }
                    },
                    label = { Text("سعر الأمبير") },
                    modifier = Modifier.weight(1f),
                    enabled = selectedVariant != null
                )
                OutlinedTextField(
                    value = manualCostPrice,
                    onValueChange = { newManualCost ->
                        manualCostPrice = newManualCost
                        if (newManualCost.isEmpty()) {
                            costPerAmpere = ""
                        } else {
                            val manualCostDouble = newManualCost.toDoubleOrNull()
                            val variantCapacity = selectedVariant?.capacity
                            if (manualCostDouble != null && variantCapacity != null && variantCapacity > 0) {
                                val newCostPerAmpere = String.format("%.2f", manualCostDouble / variantCapacity)
                                if (newCostPerAmpere != costPerAmpere) {
                                    costPerAmpere = newCostPerAmpere
                                }
                            }
                        }
                    },
                    label = { Text("تكلفة القطعة") },
                    modifier = Modifier.weight(1f),
                    enabled = selectedVariant != null
                )
            }
            // Row for Quantity and Totals
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("الكمية") },
                    modifier = Modifier.weight(1f),
                    enabled = selectedVariant != null
                )
                OutlinedTextField(
                    value = totalAmperes,
                    onValueChange = {},
                    label = { Text("إجمالي الأمبيرات") },
                    modifier = Modifier.weight(1f),
                    readOnly = true
                )
            }
             // Row for Total Cost
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = totalCost,
                    onValueChange = {},
                    label = { Text("إجمالي التكلفة") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add To Entry Button
        Button(
            onClick = {
                val variant = selectedVariant
                val product = selectedProduct
                if (variant != null && product != null) {
                    viewModel.addVariantToEntry(
                        variant = variant,
                        quantity = quantity.toIntOrNull() ?: 0,
                        productName = product.name,
                        costPerAmpere = costPerAmpere.toDoubleOrNull(), // ViewModel can still use this for logging if needed
                        manualCost = manualCostPrice.toDoubleOrNull()
                    )
                    // Reset fields
                    quantity = ""
                    manualCostPrice = ""
                    costPerAmpere = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedVariant != null
        ) {
                Text("إضافة")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stock Items List
        LazyColumn {
            items(viewModel.stockItems) { item ->
                ListItem(
                    modifier = Modifier.clickable { itemToEdit = item },
                    headlineContent = { Text("${item.productName} - ${item.productVariant.capacity} أمبير") },
                    supportingContent = { Text("الكمية: ${item.quantity}, التكلفة: ${String.format("%.2f", item.costPrice)}") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeVariantFromEntry(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "إزالة")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = supplierName,
            onValueChange = { supplierName = it },
            label = { Text("اسم المورد (اختياري)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                val warehouse = selectedWarehouse
                if (warehouse != null) {
                    viewModel.saveStockEntry(warehouse.id, supplierName)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.stockItems.isNotEmpty() && selectedWarehouse != null
        ) {
            Text("حفظ إدخال المخزون")
        }
    }
}

@Composable
fun AddWarehouseDialog(onDismiss: () -> Unit, onAddWarehouse: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مستودع جديد") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المستودع") }) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { onAddWarehouse(name); onDismiss() } }) { Text("إضافة") } },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun EditQuantityDialog(item: StockEntryItem, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var quantity by remember { mutableStateOf(item.quantity.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الكمية") },
        text = { OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("الكمية الجديدة") }) },
        confirmButton = { Button(onClick = { val newQuantity = quantity.toIntOrNull(); if (newQuantity != null && newQuantity > 0) { onConfirm(newQuantity) } }) { Text("حفظ") } },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}
