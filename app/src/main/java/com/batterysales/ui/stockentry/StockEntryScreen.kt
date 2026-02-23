package com.batterysales.ui.stockentry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.theme.LocalInputTextStyle
import com.batterysales.viewmodel.StockEntryViewModel
import com.batterysales.viewmodel.StockEntryUiState
import com.batterysales.viewmodel.CostInputMode
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton
import java.text.SimpleDateFormat
import java.util.Locale

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

    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFB8C00))
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Gradient Header
                item {
                    SharedHeader(
                        title = if (uiState.isEditMode) "تعديل قيد المخزون" else "إدخال مخزون جديد",
                        onBackClick = { navController.popBackStack() },
                        actions = {
                            HeaderIconButton(
                                icon = Icons.Default.PhotoCamera,
                                onClick = { showScanner = true },
                                contentDescription = "Scan"
                            )
                        }
                    )
                }

                item {
                    StockEntryContent(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warehouse Dropdown
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("المستودع والمورد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dropdown(
                        label = "المستودع",
                        selectedValue = uiState.selectedWarehouse?.name ?: "",
                        options = uiState.warehouses.map { it.name },
                        onOptionSelected = { index -> viewModel.onWarehouseSelected(uiState.warehouses[index]) },
                        enabled = !uiState.isEditMode && uiState.isAdmin,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.isAdmin && !uiState.isEditMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showAddWarehouseDialog = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = Color(0xFFFB8C00))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        IconButton(onClick = { showAddSupplierDialog = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = Color(0xFFFB8C00))
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.invoiceNumber,
                    onValueChange = viewModel::onInvoiceNumberChanged,
                    label = { Text("رقم الفاتورة / المرجع") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalInputTextStyle.current,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFB8C00),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        // Product and Variant Selection
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("تفاصيل المنتج", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2
                ) {
                    Dropdown(
                        label = "المنتج",
                        selectedValue = uiState.selectedProduct?.let { p ->
                            p.name + if (p.specification.isNotEmpty()) " (${p.specification})" else ""
                        } ?: "",
                        options = uiState.products.map { p ->
                            p.name + if (p.specification.isNotEmpty()) " (${p.specification})" else ""
                        },
                        onOptionSelected = { index -> viewModel.onProductSelected(uiState.products[index]) },
                        enabled = !uiState.isEditMode,
                        modifier = Modifier.weight(1f).widthIn(min = 150.dp)
                    )
                    Dropdown(
                        label = "السعة",
                        selectedValue = uiState.selectedVariant?.let { v ->
                            "${v.capacity}A" + if (v.specification.isNotEmpty()) " (${v.specification})" else ""
                        } ?: "",
                        options = uiState.variants.map { v ->
                            "${v.capacity}A" + if (v.specification.isNotEmpty()) " (${v.specification})" else ""
                        },
                        onOptionSelected = { index -> viewModel.onVariantSelected(uiState.variants[index]) },
                        enabled = !uiState.isEditMode && uiState.selectedProduct != null,
                        modifier = Modifier.weight(1f).widthIn(min = 150.dp)
                    )
                }
            }
        }


        // --- Cost Calculation Section (Admin Only) ---
        if (uiState.isAdmin) {
            CostCalculationSection(uiState = uiState, viewModel = viewModel)

            if (uiState.isEditMode) {
                OutlinedTextField(
                    value = uiState.returnedQuantity,
                    onValueChange = viewModel::onReturnedQuantityChanged,
                    label = { Text("الكمية المرتجعة") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    textStyle = LocalInputTextStyle.current,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Red,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.5f)
                    )
                )
            }
        } else {
            // Seller only sees Quantity field
            OutlinedTextField(
                value = uiState.quantity,
                onValueChange = viewModel::onQuantityChanged,
                label = { Text("الكمية") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                textStyle = LocalInputTextStyle.current,
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (!uiState.isEditMode) {
            Button(
                onClick = viewModel::onAddItemClicked,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = uiState.selectedVariant != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB8C00))
            ) { 
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إضافة إلى القائمة") 
            }
        }

        // --- Items List ---
        uiState.stockItems.forEach { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${item.productName} - ${item.productVariant.capacity}A" + 
                            if(item.productVariant.specification.isNotEmpty()) " (${item.productVariant.specification})" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.isAdmin) {
                            Text("الكمية: ${item.quantity} | التكلفة: JD ${String.format("%.3f", item.totalCost)}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("الكمية: ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!uiState.isEditMode) {
                        IconButton(onClick = { viewModel.onRemoveItemClicked(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "إزالة", tint = Color.Red)
                        }
                    }
                }
            }
        }

        if (uiState.isAdmin) {
            GrandTotalsSection(uiState = uiState)
        }

        Button(
            onClick = viewModel::onSaveClicked,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            enabled = uiState.stockItems.isNotEmpty() && uiState.selectedWarehouse != null && (uiState.isAdmin || !uiState.isEditMode)
        ) { 
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (uiState.isEditMode) "تحديث القيد" else "حفظ إدخال المخزون", fontWeight = FontWeight.Bold) 
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CostCalculationSection(uiState: StockEntryUiState, viewModel: StockEntryViewModel) {
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
    val returnedQty = uiState.returnedQuantity.toIntOrNull() ?: 0
    val netQuantity = quantity - returnedQty

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
    val totalAmperes = (netQuantity * variantCapacity).toString()
    val totalCost = String.format("%.4f", netQuantity * (costPerItem.toDoubleOrNull() ?: 0.0))

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("حساب التكلفة والكمية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.onCostInputModeChanged(CostInputMode.BY_AMPERE) }) {
                    RadioButton(selected = uiState.costInputMode == CostInputMode.BY_AMPERE, onClick = { viewModel.onCostInputModeChanged(CostInputMode.BY_AMPERE) })
                    Text("سعر الأمبير", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.onCostInputModeChanged(CostInputMode.BY_ITEM) }) {
                    RadioButton(selected = uiState.costInputMode == CostInputMode.BY_ITEM, onClick = { viewModel.onCostInputModeChanged(CostInputMode.BY_ITEM) })
                    Text("سعر القطعة", style = MaterialTheme.typography.bodySmall)
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2
            ) {
                OutlinedTextField(
                    value = if (uiState.costInputMode == CostInputMode.BY_AMPERE) uiState.costValue else costPerAmpere,
                    onValueChange = viewModel::onCostValueChanged,
                    label = { Text("سعر الأمبير") },
                    modifier = Modifier.weight(1f).widthIn(min = 140.dp),
                    readOnly = uiState.costInputMode != CostInputMode.BY_AMPERE,
                    enabled = uiState.selectedVariant != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    textStyle = LocalInputTextStyle.current,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = if (uiState.costInputMode == CostInputMode.BY_ITEM) uiState.costValue else costPerItem,
                    onValueChange = viewModel::onCostValueChanged,
                    label = { Text("تكلفة القطعة") },
                    modifier = Modifier.weight(1f).widthIn(min = 140.dp),
                    readOnly = uiState.costInputMode != CostInputMode.BY_ITEM,
                    enabled = uiState.selectedVariant != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    textStyle = LocalInputTextStyle.current,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = uiState.quantity,
                    onValueChange = viewModel::onQuantityChanged,
                    label = { Text("الكمية") },
                    modifier = Modifier.weight(1f).widthIn(min = 140.dp),
                    enabled = uiState.selectedVariant != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    textStyle = LocalInputTextStyle.current,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = uiState.minQuantity,
                    onValueChange = viewModel::onMinQuantityChanged,
                    label = { Text("الحد الأدنى") },
                    modifier = Modifier.weight(1f).widthIn(min = 140.dp),
                    enabled = uiState.selectedVariant != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    textStyle = LocalInputTextStyle.current,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("إجمالي الأمبيرات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(totalAmperes, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("إجمالي التكلفة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("JD $totalCost", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
            }
        }
    }
}

@Composable
fun GrandTotalsSection(uiState: StockEntryUiState) {
    val grandTotalAmperes = uiState.stockItems.sumOf { it.totalAmperes }
    val grandTotalCost = uiState.stockItems.sumOf { it.totalCost }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("المجموع الكلي للقيد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("إجمالي الأمبيرات", style = MaterialTheme.typography.labelSmall)
                    Text("$grandTotalAmperes A", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("إجمالي التكلفة", style = MaterialTheme.typography.labelSmall)
                    Text("JD ${String.format("%.3f", grandTotalCost)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
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
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            value = selectedValue,
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            textStyle = LocalInputTextStyle.current,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFB8C00),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
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
    com.batterysales.ui.components.AppDialog(
        onDismiss = onDismiss,
        title = "إضافة مستودع جديد",
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { onAddWarehouse(name); onDismiss() } }) { Text("إضافة") } }, 
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = name,
            onValueChange = { name = it },
            label = "اسم المستودع"
        )
    }
}

@Composable
fun AddSupplierDialog(onDismiss: () -> Unit, onAddSupplier: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    com.batterysales.ui.components.AppDialog(
        onDismiss = onDismiss,
        title = "إضافة مورد جديد",
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAddSupplier(name, target.toDoubleOrNull() ?: 0.0)
                    onDismiss()
                }
            }) { Text("إضافة") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = name,
            onValueChange = { name = it },
            label = "اسم المورد"
        )
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = target,
            onValueChange = { target = it },
            label = "الهدف السنوي (Target)"
        )
    }
}
