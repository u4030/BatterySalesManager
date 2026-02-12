package com.batterysales.ui.stocktransfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batterysales.ui.theme.LocalInputTextStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.stockentry.Dropdown
import com.batterysales.viewmodel.StockTransferViewModel
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferScreen(
    navController: NavHostController,
    viewModel: StockTransferViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

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
                    selected = false,
                    onClick = { navController.navigate("sales") },
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
    )
    { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            // Header Section
            SharedHeader(
                title = "ترحيل مخزون",
                onBackClick = { navController.popBackStack() },
                actions = {
                    HeaderIconButton(
                        icon = Icons.Default.PhotoCamera,
                        onClick = { showScanner = true },
                        contentDescription = "Scan"
                    )
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1F1F)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = uiState.errorMessage!!, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تفاصيل المنتج", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            
                            Dropdown(
                                label = "المنتج",
                                selectedValue = uiState.selectedProduct?.name ?: "",
                                options = uiState.products.map { it.name },
                                onOptionSelected = { index -> viewModel.onProductSelected(uiState.products[index]) },
                                enabled = true
                            )

                            Dropdown(
                                label = "الصنف (السعة)",
                                selectedValue = uiState.selectedVariant?.let { v ->
                                    "${v.capacity} أمبير" + if (v.specification.isNotEmpty()) " (${v.specification})" else ""
                                } ?: "",
                                options = uiState.variants.map { v ->
                                    "${v.capacity} أمبير" + if (v.specification.isNotEmpty()) " (${v.specification})" else ""
                                },
                                onOptionSelected = { index -> viewModel.onVariantSelected(uiState.variants[index]) },
                                enabled = uiState.selectedProduct != null
                            )
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CompareArrows, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("المستودعات", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Dropdown(
                                label = "من المستودع",
                                selectedValue = uiState.sourceWarehouse?.name ?: "",
                                options = uiState.warehouses.map { it.name },
                                onOptionSelected = { index -> viewModel.onSourceWarehouseSelected(uiState.warehouses[index]) },
                                enabled = !uiState.isSourceWarehouseFixed
                            )

                            Dropdown(
                                label = "إلى المستودع",
                                selectedValue = uiState.destinationWarehouse?.name ?: "",
                                options = uiState.warehouses.map { it.name },
                                onOptionSelected = { index -> viewModel.onDestinationWarehouseSelected(uiState.warehouses[index]) },
                                enabled = true
                            )
                        }
                    }
                }

                item {
                    val availableQty = uiState.selectedVariant?.let { uiState.stockLevels[Pair(it.id, uiState.sourceWarehouse?.id ?: "")] ?: 0 } ?: 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1505)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5D4037))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("المخزون المتاح", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                                Text("في مستودع المصدر", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                            }
                            Text("$availableQty", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accentColor)
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                             com.batterysales.ui.screens.SalesTextField(
                                value = uiState.quantity,
                                onValueChange = viewModel::onQuantityChanged,
                                label = "الكمية المراد ترحيلها",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = viewModel::onTransferStock,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(16.dp),
                        enabled = uiState.selectedVariant != null && uiState.sourceWarehouse != null && uiState.destinationWarehouse != null && uiState.quantity.isNotBlank()
                    ) {
                        Text("بدء عملية الترحيل", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

}
