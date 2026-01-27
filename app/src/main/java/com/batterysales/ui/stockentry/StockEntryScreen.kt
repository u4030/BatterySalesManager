package com.batterysales.ui.stockentry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockEntryScreen(
    navController: NavController,
    viewModel: StockEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate back when the operation is finished
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "تعديل قيد المخزون" else "إدخال مخزون جديد") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            StockEntryContent(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun StockEntryContent(
    modifier: Modifier = Modifier,
    uiState: StockEntryUiState,
    viewModel: StockEntryViewModel
) {
    var showAddWarehouseDialog by remember { mutableStateOf(false) }

    if (showAddWarehouseDialog) {
        AddWarehouseDialog(
            onDismiss = { showAddWarehouseDialog = false },
            onAddWarehouse = { name -> viewModel.onAddWarehouse(name) }
        )
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warehouse Dropdown
        item {
            Row {
                Dropdown(
                    label = "المستودع",
                    selectedValue = uiState.selectedWarehouse?.name ?: "",
                    options = uiState.warehouses.map { it.name },
                    onOptionSelected = { index -> viewModel.onWarehouseSelected(uiState.warehouses[index]) },
                    enabled = !uiState.isEditMode,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showAddWarehouseDialog = true }, enabled = !uiState.isEditMode) {
                    Text("إضافة مستودع")
                }
            }
        }

        // Product and Variant Selection
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown(
                    label = "المنتج",
                    selectedValue = uiState.selectedProduct?.name ?: "",
                    options = uiState.products.map { it.name },
                    onOptionSelected = { index -> viewModel.onProductSelected(uiState.products[index]) },
                    enabled = !uiState.isEditMode,
                    modifier = Modifier.weight(1f)
                )
                Dropdown(
                    label = "السعة",
                    selectedValue = uiState.selectedVariant?.capacity?.toString()?.let { "$it أمبير" } ?: "",
                    options = uiState.variants.map { "${it.capacity} أمبير" },
                    onOptionSelected = { index -> viewModel.onVariantSelected(uiState.variants[index]) },
                    enabled = !uiState.isEditMode && uiState.selectedProduct != null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Cost Calculation Section ---
        item {
            CostCalculationSection(uiState = uiState, viewModel = viewModel)
        }

        if (!uiState.isEditMode) {
            item {
                Button(
                    onClick = viewModel::onAddItemClicked,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.selectedVariant != null
                ) { Text("إضافة") }
            }
        }

        // --- Items List ---
        items(uiState.stockItems, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text("${item.productName} - ${item.productVariant.capacity} أمبير") },
                supportingContent = { Text("الكمية: ${item.quantity}, إجمالي التكلفة: ${String.format("%.2f", item.totalCost)}") },
                trailingContent = {
                    if (!uiState.isEditMode) {
                        IconButton(onClick = { viewModel.onRemoveItemClicked(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "إزالة")
                        }
                    }
                }
            )
        }

        if (!uiState.isEditMode) {
            item {
                GrandTotalsSection(uiState = uiState)
            }
        }
        item {
            OutlinedTextField(
                value = uiState.supplierName,
                onValueChange = viewModel::onSupplierNameChanged,
                label = { Text("اسم المورد (اختياري)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = viewModel::onSaveClicked,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.stockItems.isNotEmpty() && uiState.selectedWarehouse != null
            ) { Text(if (uiState.isEditMode) "تحديث القيد" else "حفظ إدخال المخزون") }
        }
    }
}

@Composable
fun CostCalculationSection(uiState: StockEntryUiState, viewModel: StockEntryViewModel) {
    // Derived values for display
    val variantCapacity = uiState.selectedVariant?.capacity ?: 0
    val cost = uiState.costValue.toDoubleOrNull() ?: 0.0
    val quantity = uiState.quantity.toIntOrNull() ?: 0

    val (costPerAmpere, costPerItem) = when (uiState.costInputMode) {
        CostInputMode.BY_AMPERE -> {
            val itemCost = if (variantCapacity > 0) cost * variantCapacity else 0.0
            Pair(uiState.costValue, String.format("%.2f", itemCost))
        }
        CostInputMode.BY_ITEM -> {
            val ampereCost = if (variantCapacity > 0) cost / variantCapacity else 0.0
            Pair(String.format("%.2f", ampereCost), uiState.costValue)
        }
    }
    val totalAmperes = (quantity * variantCapacity).toString()
    val totalCost = String.format("%.2f", quantity * (costPerItem.toDoubleOrNull() ?: 0.0))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("طريقة حساب التكلفة:")
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onCostInputModeChanged(CostInputMode.BY_AMPERE) }
                    .padding(vertical = 2.dp)
            ) {
                RadioButton(
                    selected = uiState.costInputMode == CostInputMode.BY_AMPERE,
                    onClick = { viewModel.onCostInputModeChanged(CostInputMode.BY_AMPERE) }
                )
                Spacer(Modifier.width(8.dp))
                Text("بسعر الأمبير")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onCostInputModeChanged(CostInputMode.BY_ITEM) }
                    .padding(vertical = 2.dp)
            ) {
                RadioButton(
                    selected = uiState.costInputMode == CostInputMode.BY_ITEM,
                    onClick = { viewModel.onCostInputModeChanged(CostInputMode.BY_ITEM) }
                )
                Spacer(Modifier.width(8.dp))
                Text("بسعر القطعة")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = if (uiState.costInputMode == CostInputMode.BY_AMPERE) uiState.costValue else costPerAmpere, onValueChange = viewModel::onCostValueChanged, label = { Text("سعر الأمبير") }, modifier = Modifier.weight(1f), readOnly = uiState.costInputMode != CostInputMode.BY_AMPERE, enabled = uiState.selectedVariant != null)
            OutlinedTextField(value = if (uiState.costInputMode == CostInputMode.BY_ITEM) uiState.costValue else costPerItem, onValueChange = viewModel::onCostValueChanged, label = { Text("تكلفة القطعة") }, modifier = Modifier.weight(1f), readOnly = uiState.costInputMode != CostInputMode.BY_ITEM, enabled = uiState.selectedVariant != null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = uiState.quantity, onValueChange = viewModel::onQuantityChanged, label = { Text("الكمية") }, modifier = Modifier.weight(1f), enabled = uiState.selectedVariant != null)
            OutlinedTextField(value = totalAmperes, onValueChange = {}, label = { Text("إجمالي الأمبيرات") }, modifier = Modifier.weight(1f), readOnly = true)
        }
        OutlinedTextField(value = totalCost, onValueChange = {}, label = { Text("إجمالي التكلفة") }, modifier = Modifier.fillMaxWidth(), readOnly = true)
    }
}

@Composable
fun GrandTotalsSection(uiState: StockEntryUiState) {
    val grandTotalAmperes = uiState.stockItems.sumOf { it.totalAmperes }
    val grandTotalCost = uiState.stockItems.sumOf { it.totalCost }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = grandTotalAmperes.toString(), onValueChange = {}, label = { Text("المجموع الكلي للأمبيرات") }, modifier = Modifier.weight(1f), readOnly = true)
        OutlinedTextField(value = String.format("%.2f", grandTotalCost), onValueChange = {}, label = { Text("المجموع الكلي للتكلفة") }, modifier = Modifier.weight(1f), readOnly = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        TextField(
            modifier = Modifier.menuAnchor(),
            readOnly = true,
            value = selectedValue,
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, text ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    onOptionSelected(index)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun AddWarehouseDialog(onDismiss: () -> Unit, onAddWarehouse: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة مستودع جديد") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المستودع") }) }, confirmButton = { Button(onClick = { if (name.isNotBlank()) { onAddWarehouse(name); onDismiss() } }) { Text("إضافة") } }, dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } })
}
