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
import androidx.hilt.navigation.compose.hiltViewModel
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse
import java.util.UUID

enum class CostInputMode {
    BY_AMPERE,
    BY_ITEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockEntryScreen(
    viewModel: StockEntryViewModel = hiltViewModel()
) {
    val isEditMode = viewModel.isEditMode
    val stockItems = viewModel.stockItems

    // State for UI fields
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedVariant by remember { mutableStateOf<ProductVariant?>(null) }
    var selectedWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var quantity by remember { mutableStateOf("") }
    var costInputMode by remember { mutableStateOf(CostInputMode.BY_AMPERE) }
    var costValue by remember { mutableStateOf("") }
    var supplierName by remember { mutableStateOf("") }

    // State for UI controls
    var expandedProduct by remember { mutableStateOf(false) }
    var expandedVariant by remember { mutableStateOf(false) }
    var expandedWarehouse by remember { mutableStateOf(false) }
    var showAddWarehouseDialog by remember { mutableStateOf(false) }
    var itemToEditInDialog by remember { mutableStateOf<StockEntryItem?>(null) }

    // Derived values for calculation and display
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

    // Grand Totals
    val grandTotalAmperes = stockItems.sumOf { it.totalAmperes }
    val grandTotalCost = stockItems.sumOf { it.totalCost }

    // This effect runs when in edit mode and the item is loaded
    LaunchedEffect(stockItems, viewModel.warehouses.value) {
        if (isEditMode && stockItems.isNotEmpty()) {
            val itemToEdit = stockItems.first()
            quantity = itemToEdit.quantity.toString()
            costValue = itemToEdit.costPrice.toString()
            costInputMode = CostInputMode.BY_ITEM
            selectedVariant = itemToEdit.productVariant

            viewModel.products.value.find { it.id == itemToEdit.productVariant.productId }?.let {
                selectedProduct = it
            }
            viewModel.warehouses.value.find { it.id == viewModel.getLoadedEntryWarehouseId() }?.let {
                 selectedWarehouse = it
            }
            supplierName = viewModel.getLoadedEntrySupplier()
        }
    }

    // Dialogs
    if (showAddWarehouseDialog) {
        AddWarehouseDialog(
            onDismiss = { showAddWarehouseDialog = false },
            onAddWarehouse = { name -> viewModel.addWarehouse(name) }
        )
    }

    itemToEditInDialog?.let { item ->
        EditQuantityDialog(
            item = item,
            onDismiss = { itemToEditInDialog = null },
            onConfirm = { newQuantity ->
                 if (isEditMode) {
                    // In edit mode, update the central item in the view model
                    viewModel.updateItemInList(item.id, newQuantity, item.costPrice, item.costPerAmpere)
                    quantity = newQuantity.toString() // Also update the UI field
                } else {
                    // In add mode, update the item in the list directly
                    viewModel.updateItemInList(item.id, newQuantity, item.costPrice, item.costPerAmpere)
                }
                itemToEditInDialog = null
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // Warehouse Dropdown (disabled in edit mode)
        Row {
            ExposedDropdownMenuBox(
                expanded = expandedWarehouse,
                onExpandedChange = { if (!isEditMode) expandedWarehouse = !expandedWarehouse },
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedWarehouse?.name ?: "",
                    onValueChange = {},
                    label = { Text("المستودع") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWarehouse) },
                    enabled = !isEditMode
                )
                ExposedDropdownMenu(expanded = expandedWarehouse, onDismissRequest = { expandedWarehouse = false }) {
                    viewModel.warehouses.value.forEach { warehouse ->
                        DropdownMenuItem( text = { Text(warehouse.name) }, onClick = { selectedWarehouse = warehouse; expandedWarehouse = false })
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { showAddWarehouseDialog = true }, enabled = !isEditMode) { Text("إضافة مستودع") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Product and Variant Selection (disabled in edit mode)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = expandedProduct,
                onExpandedChange = { if (!isEditMode) expandedProduct = !expandedProduct },
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedProduct?.name ?: "",
                    onValueChange = {},
                    label = { Text("المنتج") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
                    enabled = !isEditMode
                )
                ExposedDropdownMenu(expanded = expandedProduct, onDismissRequest = { expandedProduct = false }) {
                    viewModel.products.value.forEach { product ->
                        DropdownMenuItem( text = { Text(product.name) }, onClick = {
                            selectedProduct = product
                            selectedVariant = null
                            viewModel.fetchVariantsForProduct(product.id)
                            expandedProduct = false
                        })
                    }
                }
            }
            ExposedDropdownMenuBox(
                expanded = expandedVariant,
                onExpandedChange = { if (!isEditMode) expandedVariant = !expandedVariant },
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    modifier = Modifier.menuAnchor(),
                    readOnly = true,
                    value = selectedVariant?.capacity?.toString()?.let { "$it أمبير" } ?: "",
                    onValueChange = {},
                    label = { Text("السعة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVariant) },
                    enabled = !isEditMode && selectedProduct != null
                )
                ExposedDropdownMenu(expanded = expandedVariant, onDismissRequest = { expandedVariant = false }) {
                    viewModel.variants.value.forEach { variant ->
                        DropdownMenuItem(text = { Text("${variant.capacity} أمبير") }, onClick = {
                            selectedVariant = variant
                            expandedVariant = false
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Cost Calculation Section ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                 Text("طريقة حساب التكلفة:", modifier = Modifier.padding(end = 8.dp))
                Row {
                    RadioButton(selected = costInputMode == CostInputMode.BY_AMPERE, onClick = { costInputMode = CostInputMode.BY_AMPERE; costValue = "" })
                    Text("بسعر الأمبير")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = costInputMode == CostInputMode.BY_ITEM, onClick = { costInputMode = CostInputMode.BY_ITEM; costValue = "" })
                    Text("بسعر القطعة")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = if (costInputMode == CostInputMode.BY_AMPERE) costValue else costPerAmpere, onValueChange = { if (costInputMode == CostInputMode.BY_AMPERE) costValue = it }, label = { Text("سعر الأمبير") }, modifier = Modifier.weight(1f), readOnly = costInputMode != CostInputMode.BY_AMPERE, enabled = selectedVariant != null)
                OutlinedTextField(value = if (costInputMode == CostInputMode.BY_ITEM) costValue else costPerItem, onValueChange = { if (costInputMode == CostInputMode.BY_ITEM) costValue = it }, label = { Text("تكلفة القطعة") }, modifier = Modifier.weight(1f), readOnly = costInputMode != CostInputMode.BY_ITEM, enabled = selectedVariant != null)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                 OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("الكمية") }, modifier = Modifier.weight(1f), enabled = selectedVariant != null)
                 OutlinedTextField(value = totalAmperes, onValueChange = {}, label = { Text("إجمالي الأمبيرات") }, modifier = Modifier.weight(1f), readOnly = true)
            }

            OutlinedTextField(value = totalCost, onValueChange = {}, label = { Text("إجمالي التكلفة") }, modifier = Modifier.fillMaxWidth(), readOnly = true)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isEditMode) {
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
                        quantity = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedVariant != null
            ) { Text("إضافة") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isEditMode && stockItems.isNotEmpty()) {
            val item = stockItems.first()
            ListItem(
                headlineContent = { Text("${item.productName} - ${item.productVariant.capacity} أمبير") },
                supportingContent = { Text("الكمية: ${item.quantity}, إجمالي التكلفة: ${String.format("%.2f", item.totalCost)}") }
            )
        } else if (!isEditMode) {
             LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(stockItems, key = { it.id }) { item ->
                    ListItem(
                        modifier = Modifier.clickable { itemToEditInDialog = item },
                        headlineContent = { Text("${item.productName} - ${item.productVariant.capacity} أمبير") },
                        supportingContent = { Text("الكمية: ${item.quantity}, إجمالي التكلفة: ${String.format("%.2f", item.totalCost)}") },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeVariantFromEntry(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "إزالة")
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

         if (!isEditMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = grandTotalAmperes.toString(), onValueChange = {}, label = { Text("المجموع الكلي للأمبيرات") }, modifier = Modifier.weight(1f), readOnly = true)
                OutlinedTextField(value = String.format("%.2f", grandTotalCost), onValueChange = {}, label = { Text("المجموع الكلي للتكلفة") }, modifier = Modifier.weight(1f), readOnly = true)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(value = supplierName, onValueChange = { supplierName = it }, label = { Text("اسم المورد (اختياري)") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isEditMode && stockItems.isNotEmpty()) {
                    val item = stockItems.first()
                    viewModel.updateItemInList(
                        itemId = item.id,
                        newQuantity = currentQuantity,
                        newCostPrice = finalCostPerItem,
                        newCostPerAmpere = costPerAmpere.toDoubleOrNull() ?: 0.0
                    )
                }
                val warehouse = selectedWarehouse
                if (warehouse != null) {
                    viewModel.saveStockEntry(warehouse.id, supplierName)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = (if (isEditMode) stockItems.isNotEmpty() && selectedWarehouse != null else stockItems.isNotEmpty() && selectedWarehouse != null)
        ) {
            Text(if (isEditMode) "تحديث القيد" else "حفظ إدخال المخزون")
        }
    }
}

@Composable
fun AddWarehouseDialog(onDismiss: () -> Unit, onAddWarehouse: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة مستودع جديد") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المستودع") }) }, confirmButton = { Button(onClick = { if (name.isNotBlank()) { onAddWarehouse(name); onDismiss() } }) { Text("إضافة") } }, dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
fun EditQuantityDialog(item: StockEntryItem, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var quantity by remember { mutableStateOf(item.quantity.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعديل الكمية") }, text = { OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("الكمية الجديدة") }) }, confirmButton = { Button(onClick = { val newQuantity = quantity.toIntOrNull(); if (newQuantity != null && newQuantity > 0) { onConfirm(newQuantity) } }) { Text("حفظ") } }, dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } })
}
