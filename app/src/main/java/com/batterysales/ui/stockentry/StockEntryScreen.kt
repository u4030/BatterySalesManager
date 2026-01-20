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
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse

enum class CostInputMode {
    BY_AMPERE,
    BY_ITEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockEntryScreen(
    viewModel: StockEntryViewModel = hiltViewModel()
) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedVariant by remember { mutableStateOf<ProductVariant?>(null) }
    var selectedWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var quantity by remember { mutableStateOf("") }

    var costInputMode by remember { mutableStateOf(CostInputMode.BY_AMPERE) }
    var costValue by remember { mutableStateOf("") }

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

        // --- Derived values for calculation and display ---
        val currentQuantity = quantity.toIntOrNull() ?: 0
        val cost = costValue.toDoubleOrNull() ?: 0.0
        val variantCapacity = selectedVariant?.capacity ?: 0

        val (costPerAmpere, costPerItem) = when (costInputMode) {
            CostInputMode.BY_AMPERE -> {
                val calculatedCostPerItem = if (variantCapacity > 0) cost * variantCapacity else 0.0
                Pair(costValue, String.format("%.2f", calculatedCostPerItem))
            }
            CostInputMode.BY_ITEM -> {
                val calculatedCostPerAmpere = if (variantCapacity > 0) cost / variantCapacity else 0.0
                Pair(String.format("%.2f", calculatedCostPerAmpere), costValue)
            }
        }
        val finalCostPerItem = costPerItem.toDoubleOrNull() ?: 0.0
        val totalAmperes = (currentQuantity * variantCapacity).toString()
        val totalCost = String.format("%.2f", currentQuantity * finalCostPerItem)

        // --- Grand Totals ---
        val grandTotalAmperes = viewModel.stockItems.sumOf { it.totalAmperes }
        val grandTotalCost = viewModel.stockItems.sumOf { it.totalCost }


        // --- Cost Calculation Section ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Mode Selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("طريقة حساب التكلفة:", modifier = Modifier.padding(end = 8.dp))
                Row {
                    RadioButton(
                        selected = costInputMode == CostInputMode.BY_AMPERE,
                        onClick = { costInputMode = CostInputMode.BY_AMPERE; costValue = "" }
                    )
                    Text("بسعر الأمبير")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(
                        selected = costInputMode == CostInputMode.BY_ITEM,
                        onClick = { costInputMode = CostInputMode.BY_ITEM; costValue = "" }
                    )
                    Text("بسعر القطعة")
                }
            }

            // Input Fields
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (costInputMode == CostInputMode.BY_AMPERE) costValue else costPerAmpere,
                    onValueChange = { if (costInputMode == CostInputMode.BY_AMPERE) costValue = it },
                    label = { Text("سعر الأمبير") },
                    modifier = Modifier.weight(1f),
                    readOnly = costInputMode != CostInputMode.BY_AMPERE,
                    enabled = selectedVariant != null
                )
                OutlinedTextField(
                    value = if (costInputMode == CostInputMode.BY_ITEM) costValue else costPerItem,
                    onValueChange = { if (costInputMode == CostInputMode.BY_ITEM) costValue = it },
                    label = { Text("تكلفة القطعة") },
                    modifier = Modifier.weight(1f),
                    readOnly = costInputMode != CostInputMode.BY_ITEM,
                    enabled = selectedVariant != null
                )
            }

            // Quantity and Totals
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

            OutlinedTextField(
                value = totalCost,
                onValueChange = {},
                label = { Text("إجمالي التكلفة") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
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
                        quantity = currentQuantity,
                        productName = product.name,
                        costPrice = finalCostPerItem,
                        costPerAmpere = costPerAmpere.toDoubleOrNull() ?: 0.0,
                        totalAmperes = totalAmperes.toIntOrNull() ?: 0,
                        totalCost = totalCost.toDoubleOrNull() ?: 0.0
                    )
                    // Reset only the quantity field
                    quantity = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedVariant != null
        ) {
            Text("إضافة")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stock Items List
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) { // Limit height to avoid pushing totals off-screen
            items(viewModel.stockItems) { item ->
                ListItem(
                    modifier = Modifier.clickable { itemToEdit = item },
                    headlineContent = { Text("${item.productName} - ${item.productVariant.capacity} أمبير") },
                    supportingContent = {
                        Text("الكمية: ${item.quantity}, " +
                                "إجمالي التكلفة: ${String.format("%.2f", item.totalCost)}")
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeVariantFromEntry(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "إزالة")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grand Totals Display
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = grandTotalAmperes.toString(),
                onValueChange = {},
                label = { Text("المجموع الكلي للأمبيرات") },
                modifier = Modifier.weight(1f),
                readOnly = true
            )
            OutlinedTextField(
                value = String.format("%.2f", grandTotalCost),
                onValueChange = {},
                label = { Text("المجموع الكلي للتكلفة") },
                modifier = Modifier.weight(1f),
                readOnly = true
            )
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
                    // Reset all fields after saving
                    selectedProduct = null
                    selectedVariant = null
                    selectedWarehouse = null
                    quantity = ""
                    costValue = ""
                    supplierName = ""
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
