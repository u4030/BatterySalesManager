package com.batterysales.ui.stocktransfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTransferScreen(
    navController: NavController,
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

    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(androidx.compose.ui.graphics.Color(0xFF1E293B), androidx.compose.ui.graphics.Color(0xFF0F172A))
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gradient Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = headerGradient,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
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
                                modifier = Modifier.background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = androidx.compose.ui.graphics.Color.White)
                            }

                            Text(
                                text = "ترحيل مخزون",
                                style = MaterialTheme.typography.headlineSmall,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            // Placeholder for alignment
                            Box(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { showScanner = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مسح الباركود")
                    }

                    if (uiState.errorMessage != null) {
                        Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }

                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    Dropdown(
                        label = "المنتج",
                        selectedValue = uiState.selectedProduct?.name ?: "",
                        options = uiState.products.map { it.name },
                        onOptionSelected = { index -> viewModel.onProductSelected(uiState.products[index]) },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth()
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
                        enabled = uiState.selectedProduct != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Dropdown(
                        label = "من المستودع",
                        selectedValue = uiState.sourceWarehouse?.name ?: "",
                        options = uiState.warehouses.map { it.name },
                        onOptionSelected = { index -> viewModel.onSourceWarehouseSelected(uiState.warehouses[index]) },
                        enabled = !uiState.isSourceWarehouseFixed,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Dropdown(
                        label = "إلى المستودع",
                        selectedValue = uiState.destinationWarehouse?.name ?: "",
                        options = uiState.warehouses.map { it.name },
                        onOptionSelected = { index -> viewModel.onDestinationWarehouseSelected(uiState.warehouses[index]) },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val availableQty = uiState.selectedVariant?.let { uiState.stockLevels[Pair(it.id, uiState.sourceWarehouse?.id ?: "")] ?: 0 } ?: 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("المخزون المتوفر في المصدر:", style = MaterialTheme.typography.bodyLarge)
                            Text("$availableQty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    OutlinedTextField(
                        value = uiState.quantity,
                        onValueChange = viewModel::onQuantityChanged,
                        label = { Text("الكمية المراد ترحيلها") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        textStyle = LocalInputTextStyle.current
                    )

                    Button(
                        onClick = viewModel::onTransferStock,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEF4444)),
                        enabled = uiState.selectedVariant != null && uiState.sourceWarehouse != null && uiState.destinationWarehouse != null && uiState.quantity.isNotBlank()
                    ) {
                        Text("ترحيل المخزون", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

}
