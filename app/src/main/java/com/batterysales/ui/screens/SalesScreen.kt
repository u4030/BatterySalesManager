package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.viewmodel.SalesViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.batterysales.ui.components.CustomKeyboardTextField
import com.batterysales.ui.components.KeyboardLanguage
import com.batterysales.ui.theme.LocalInputTextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SalesScreen(navController: NavController, viewModel: SalesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paidAmount by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

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
                BarcodeScanner(onBarcodeScanned = { barcode ->
                    viewModel.findProductByBarcode(barcode)
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

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    )

    Scaffold(
        containerColor = bgColor
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Gradient Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = headerGradient,
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Text(
                            text = "فاتورة مبيعات جديدة",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = { showScanner = true },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Scan", tint = Color.White)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            if (uiState.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = viewModel::onDismissError) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SalesDropdown(
                            label = "اختر المنتج",
                            selectedValue = uiState.selectedProduct?.name ?: "",
                            options = uiState.products.map { it.name },
                            onOptionSelected = { index -> viewModel.onProductSelected(uiState.products[index]) },
                            icon = Icons.Default.Inventory2
                        )

                        SalesDropdown(
                            label = "اختر الصنف (السعة)",
                            selectedValue = uiState.selectedVariant?.let { v ->
                                "${v.capacity} أمبير" + if (v.specification.isNotEmpty()) " (${v.specification})" else ""
                            } ?: "",
                            options = uiState.variants.map { v ->
                                "${v.capacity} أمبير" + if (v.specification.isNotEmpty()) " (${v.specification})" else ""
                            },
                            onOptionSelected = { index -> viewModel.onVariantSelected(uiState.variants[index]) },
                            enabled = uiState.selectedProduct != null,
                            icon = Icons.Default.Settings
                        )

                        SalesDropdown(
                            label = "اختر المستودع",
                            selectedValue = uiState.selectedWarehouse?.name ?: "",
                            options = uiState.warehouses.map { it.name },
                            onOptionSelected = { index -> viewModel.onWarehouseSelected(uiState.warehouses[index]) },
                            enabled = !uiState.isWarehouseFixed,
                            icon = Icons.Default.Warehouse
                        )

                        val availableQty = uiState.selectedVariant?.let { uiState.stockLevels[Pair(it.id, uiState.selectedWarehouse?.id ?: "")] ?: 0 } ?: 0
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("الكمية المتاحة: ", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
                            Text("$availableQty", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        SalesTextField(
                            value = uiState.quantity,
                            onValueChange = viewModel::onQuantityChanged,
                            label = "الكمية",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            textAlign = TextAlign.Center
                        )

                        SalesTextField(
                            value = uiState.sellingPrice,
                            onValueChange = viewModel::onSellingPriceChanged,
                            label = "سعر البيع",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            suffix = "JD",
                            textAlign = TextAlign.Center
                        )

                        // Total Box
                        val qtyVal = uiState.quantity.toDoubleOrNull() ?: 0.0
                        val priceVal = uiState.sellingPrice.toDoubleOrNull() ?: 0.0
                        val totalVal = qtyVal * priceVal

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "الإجمالي",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Text(
                                    text = "${String.format("%,.3f", totalVal)} JD",
                                    color = accentColor,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Scrap Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(40.dp).background(accentColor.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("البطاريات القديمة (سكراب)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(
                                    "سيتم إضافة البطاريات المستهلكة إلى المستودع",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            maxItemsInEachRow = 2
                        ) {
                            SalesTextField(
                                value = uiState.oldBatteriesQuantity,
                                onValueChange = viewModel::onOldBatteriesQuantityChanged,
                                label = "الكمية",
                                modifier = Modifier.widthIn(min = 120.dp).weight(1f),
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                textAlign = TextAlign.Center
                            )
                            SalesTextField(
                                value = uiState.oldBatteriesTotalAmps,
                                onValueChange = viewModel::onOldBatteriesTotalAmpsChanged,
                                label = "إجمالي الأمبيرات",
                                modifier = Modifier.widthIn(min = 120.dp).weight(1f),
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                textAlign = TextAlign.Center
                            )
                        }

                        SalesTextField(
                            value = uiState.oldBatteriesValue,
                            onValueChange = viewModel::onOldBatteriesValueChanged,
                            label = "قيمة الخصم",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            suffix = "JD",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Customer Info & Submit
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CustomKeyboardTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = "اسم العميل",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardType = KeyboardLanguage.ARABIC
                        )
                        CustomKeyboardTextField(
                            value = customerPhone,
                            onValueChange = { customerPhone = it },
                            label = "رقم هاتف العميل",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardType = KeyboardLanguage.NUMERIC
                        )
                        SalesTextField(
                            value = paidAmount,
                            onValueChange = { paidAmount = it },
                            label = "المبلغ المدفوع",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            suffix = "JD",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.createSale(
                            customerName = customerName,
                            customerPhone = customerPhone,
                            paidAmount = paidAmount.toDoubleOrNull() ?: 0.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(16.dp),
                    enabled = uiState.selectedVariant != null && uiState.selectedWarehouse != null && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("إنشاء عملية بيع", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val accentColor = Color(0xFFFB8C00)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp))
        Box {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                leadingIcon = { Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accentColor) },
                enabled = enabled,
                textStyle = LocalInputTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = enabled) { expanded = true }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                options.forEachIndexed { index, text ->
                    DropdownMenuItem(
                        text = { Text(text, color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onOptionSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    suffix: String? = null,
    textAlign: TextAlign = TextAlign.Start
) {
    val accentColor = Color(0xFFFB8C00)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            textStyle = LocalInputTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign),
            trailingIcon = if (suffix != null) {
                { Text(suffix, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(end = 12.dp)) }
            } else null
        )
    }
}
