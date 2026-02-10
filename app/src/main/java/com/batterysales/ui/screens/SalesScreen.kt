package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.viewmodel.SalesViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.batterysales.ui.components.CustomKeyboardTextField
import com.batterysales.ui.components.KeyboardLanguage
import com.batterysales.ui.theme.LocalInputTextStyle

@OptIn(ExperimentalMaterial3Api::class)
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

    val bgColor = Color(0xFF0F0F0F)
    val cardBgColor = Color(0xFF1C1C1C)
    val accentColor = Color(0xFFFB8C00)
    val fieldBorderColor = Color(0xFF3E2723)

    Scaffold(
        containerColor = bgColor,
        bottomBar = {
            NavigationBar(
                containerColor = cardBgColor,
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("dashboard") { popUpTo("dashboard") { inclusive = true } } },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("الرئيسية") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("product_management") },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("المنتجات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("المبيعات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("الإعدادات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showScanner = true },
                        modifier = Modifier.background(accentColor.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Scan", tint = accentColor)
                    }
                    Text("فاتورة جديدة", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1F1F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = uiState.errorMessage!!, color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = viewModel::onDismissError) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                            Text("الكمية المتاحة: ", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            Text("$availableQty", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                            suffix = "ر.س",
                            textAlign = TextAlign.Center
                        )

                        // Total Box
                        val qty = uiState.quantity.toDoubleOrNull() ?: 0.0
                        val price = uiState.sellingPrice.toDoubleOrNull() ?: 0.0
                        val total = qty * price

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(Color(0xFF2E1505), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF5D4037), RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("الإجمالي", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                                Text("${String.format("%,.2f", total)} ر.س", color = accentColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
                                Text("البطاريات القديمة (سكراب)", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    "سيتم إضافة البطاريات المستهلكة إلى المستودع",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SalesTextField(
                                value = uiState.oldBatteriesQuantity,
                                onValueChange = viewModel::onOldBatteriesQuantityChanged,
                                label = "الكمية",
                                modifier = Modifier.weight(1f),
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                textAlign = TextAlign.Center
                            )
                            SalesTextField(
                                value = uiState.oldBatteriesTotalAmps,
                                onValueChange = viewModel::onOldBatteriesTotalAmpsChanged,
                                label = "إجمالي الأمبيرات",
                                modifier = Modifier.weight(1f),
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                textAlign = TextAlign.Center
                            )
                        }

                        SalesTextField(
                            value = uiState.oldBatteriesValue,
                            onValueChange = viewModel::onOldBatteriesValueChanged,
                            label = "قيمة الخصم",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            suffix = "ر.س",
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
                            suffix = "ر.س",
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
    val fieldBorderColor = Color(0xFF3E2723)
    val accentColor = Color(0xFFFB8C00)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Box {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color.Transparent,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = fieldBorderColor,
                    disabledBorderColor = fieldBorderColor.copy(alpha = 0.5f)
                ),
                leadingIcon = { Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accentColor) },
                enabled = enabled,
                textStyle = LocalInputTextStyle.current.copy(color = Color.White)
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
                    .background(Color(0xFF1C1C1C))
            ) {
                options.forEachIndexed { index, text ->
                    DropdownMenuItem(
                        text = { Text(text, color = Color.White) },
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
    val fieldBorderColor = Color(0xFF3E2723)
    val accentColor = Color(0xFFFB8C00)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = Color.Transparent,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = fieldBorderColor
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            textStyle = LocalInputTextStyle.current.copy(color = Color.White, textAlign = textAlign),
            trailingIcon = if (suffix != null) {
                { Text(suffix, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(end = 12.dp)) }
            } else null
        )
    }
}
