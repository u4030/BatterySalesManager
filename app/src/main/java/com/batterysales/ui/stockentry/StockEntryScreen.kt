package com.batterysales.ui.stockentry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.theme.LocalInputTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockEntryScreen(
    navController: NavController,
    viewModel: StockEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showScanner by remember { mutableStateOf(false) }

    // Navigate back when the operation is finished
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            navController.popBackStack()
        }
    }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                com.batterysales.ui.components.BarcodeScanner(onBarcodeScanned = { barcode ->
                    viewModel.onBarcodeScanned(barcode)
                    showScanner = false
                })
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
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
            Column(modifier = Modifier.padding(padding).imePadding()) {
                OutlinedButton(
                    onClick = { showScanner = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مسح الباركود")
                }
                StockEntryContent(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
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
    var showAddSupplierDialog by remember { mutableStateOf(false) }

    if (showAddWarehouseDialog) {
        AddWarehouseDialog(
            onDismiss = { showAddWarehouseDialog = false },
            onAddWarehouse = { name -> viewModel.onAddWarehouse(name) }
        )
    }

    if (showAddSupplierDialog) {
        AddSupplierDialog(
            onDismiss = { showAddSupplierDialog = false },
            onAddSupplier = { name, target -> viewModel.onAddSupplier(name, target) }
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
                    enabled = !uiState.isEditMode && uiState.isAdmin,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { showAddWarehouseDialog = true }, enabled = !uiState.isEditMode) {
                        Text("إضافة مستودع")
                    }
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
                    selectedValue = uiState.selectedVariant?.let { v ->
                        "${v.capacity} أمبير" + if (v.specification.isNotEmpty()) " (${v.specification})" else ""
                    } ?: "",
                    options = uiState.variants.map { v ->
                        "${v.capacity} أمبير" + if (v.specification.isNotEmpty()) " (${v.specification})" else ""
                    },
                    onOptionSelected = { index -> viewModel.onVariantSelected(uiState.variants[index]) },
                    enabled = !uiState.isEditMode && uiState.selectedProduct != null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Cost Calculation Section (Admin Only) ---
        if (uiState.isAdmin) {
            item {
                CostCalculationSection(uiState = uiState, viewModel = viewModel)
            }
        } else {
            // Seller only sees Quantity field
            item {
                OutlinedTextField(
                    value = uiState.quantity,
                    onValueChange = viewModel::onQuantityChanged,
                    label = { Text("الكمية") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    textStyle = LocalInputTextStyle.current
                )
            }
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
                headlineContent = {
                    Text(
                        "${item.productName} - ${item.productVariant.capacity} أمبير" +
                                if (item.productVariant.specification.isNotEmpty()) " (${item.productVariant.specification})" else ""
                    )
                },
                supportingContent = {
                    if (uiState.isAdmin) {
                        Text("الكمية: ${item.quantity}, إجمالي التكلفة: JD ${String.format("%.4f", item.totalCost)}")
                    } else {
                        Text("الكمية: ${item.quantity}")
                    }
                },
                trailingContent = {
                    if (!uiState.isEditMode) {
                        IconButton(onClick = { viewModel.onRemoveItemClicked(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "إزالة")
                        }
                    }
                }
            )
        }

        if (uiState.isAdmin) {
            item {
                GrandTotalsSection(uiState = uiState)
            }
        }
        item {
            Row {
                Dropdown(
                    label = "المورد",
                    selectedValue = uiState.selectedSupplier?.name ?: uiState.supplierName,
                    options = uiState.suppliers.map { it.name },
                    onOptionSelected = { index -> viewModel.onSupplierSelected(uiState.suppliers[index]) },
                    enabled = true,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { showAddSupplierDialog = true }) {
                        Text("إضافة مورد")
                    }
                }
            }
        }

        item {
            Button(
                onClick = viewModel::onSaveClicked,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.stockItems.isNotEmpty() && uiState.selectedWarehouse != null && (uiState.isAdmin || !uiState.isEditMode)
            ) { Text(if (uiState.isEditMode) "تحديث القيد" else "حفظ إدخال المخزون") }
        }
    }
}

@Composable
fun CostCalculationSection(uiState: StockEntryUiState, viewModel: StockEntryViewModel) {
    // Derived values for display
    val variantCapacity = uiState.selectedVariant?.capacity ?: 0
    val cost = if (uiState.costValue.count { it == '.' } > 1) {
        val parts = uiState.costValue.split(".")
        val jd = parts.getOrNull(0) ?: "0"
        val qirsh = (parts.getOrNull(1) ?: "00").padStart(2, '0')
        val fils = (parts.getOrNull(2) ?: "00").padStart(2, '0')
        "$jd.${qirsh}${fils}".toDoubleOrNull() ?: 0.0
    } else {
        uiState.costValue.toDoubleOrNull() ?: 0.0
    }
    val quantity = uiState.quantity.toIntOrNull() ?: 0

    val (costPerAmpere, costPerItem) = when (uiState.costInputMode) {
        CostInputMode.BY_AMPERE -> {
            val itemCost = if (variantCapacity > 0) cost * variantCapacity else 0.0
            Pair(uiState.costValue, String.format("%.4f", itemCost))
        }
        CostInputMode.BY_ITEM -> {
            val ampereCost = if (variantCapacity > 0) cost / variantCapacity else 0.0
            Pair(String.format("%.4f", ampereCost), uiState.costValue)
        }
    }
    val totalAmperes = (quantity * variantCapacity).toString()
    val totalCost = String.format("%.4f", quantity * (costPerItem.toDoubleOrNull() ?: 0.0))

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
            OutlinedTextField(
                value = if (uiState.costInputMode == CostInputMode.BY_AMPERE) uiState.costValue else costPerAmpere,
                onValueChange = viewModel::onCostValueChanged,
                label = { Text("سعر الأمبير") },
                modifier = Modifier.weight(1f),
                readOnly = uiState.costInputMode != CostInputMode.BY_AMPERE,
                enabled = uiState.selectedVariant != null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                textStyle = LocalInputTextStyle.current
            )
            OutlinedTextField(
                value = if (uiState.costInputMode == CostInputMode.BY_ITEM) uiState.costValue else costPerItem,
                onValueChange = viewModel::onCostValueChanged,
                label = { Text("تكلفة القطعة") },
                modifier = Modifier.weight(1f),
                readOnly = uiState.costInputMode != CostInputMode.BY_ITEM,
                enabled = uiState.selectedVariant != null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                textStyle = LocalInputTextStyle.current
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.quantity,
                onValueChange = viewModel::onQuantityChanged,
                label = { Text("الكمية") },
                modifier = Modifier.weight(1f),
                enabled = uiState.selectedVariant != null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                textStyle = LocalInputTextStyle.current
            )
            OutlinedTextField(
                value = uiState.minQuantity,
                onValueChange = viewModel::onMinQuantityChanged,
                label = { Text("الحد الأدنى") },
                modifier = Modifier.weight(1f),
                enabled = uiState.selectedVariant != null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                textStyle = LocalInputTextStyle.current
            )
        }
        OutlinedTextField(value = totalAmperes, onValueChange = {}, label = { Text("إجمالي الأمبيرات") }, modifier = Modifier.fillMaxWidth(), readOnly = true, textStyle = LocalInputTextStyle.current)
        OutlinedTextField(value = totalCost, onValueChange = {}, label = { Text("إجمالي التكلفة") }, modifier = Modifier.fillMaxWidth(), readOnly = true, textStyle = LocalInputTextStyle.current)
    }
}

@Composable
fun GrandTotalsSection(uiState: StockEntryUiState) {
    val grandTotalAmperes = uiState.stockItems.sumOf { it.totalAmperes }
    val grandTotalCost = uiState.stockItems.sumOf { it.totalCost }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = grandTotalAmperes.toString(), onValueChange = {}, label = { Text("المجموع الكلي للأمبيرات") }, modifier = Modifier.weight(1f), readOnly = true, textStyle = LocalInputTextStyle.current)
        OutlinedTextField(value = String.format("%.4f", grandTotalCost), onValueChange = {}, label = { Text("المجموع الكلي للتكلفة (JD)") }, modifier = Modifier.weight(1f), readOnly = true, textStyle = LocalInputTextStyle.current)
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
            enabled = enabled,
            textStyle = LocalInputTextStyle.current
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة مستودع جديد") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المستودع") }, textStyle = LocalInputTextStyle.current) }, confirmButton = { Button(onClick = { if (name.isNotBlank()) { onAddWarehouse(name); onDismiss() } }) { Text("إضافة") } }, dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
fun AddSupplierDialog(onDismiss: () -> Unit, onAddSupplier: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مورد جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المورد") }, textStyle = LocalInputTextStyle.current)
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("الهدف السنوي (Target)") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), textStyle = LocalInputTextStyle.current)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAddSupplier(name, target.toDoubleOrNull() ?: 0.0)
                    onDismiss()
                }
            }) { Text("إضافة") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}
