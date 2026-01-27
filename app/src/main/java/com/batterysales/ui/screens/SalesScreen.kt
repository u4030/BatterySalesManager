package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.ui.stockentry.Dropdown
import com.batterysales.viewmodel.SalesViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.Alignment

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
        BarcodeScanner(onBarcodeScanned = { barcode ->
            viewModel.findProductByBarcode(barcode)
            showScanner = false
        })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("فاتورة جديدة") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    OutlinedButton(
                        onClick = { showScanner = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مسح الباركود")
                    }
                }

                item {
                    if (uiState.errorMessage != null) {
                        Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }

                item {
                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                item {
                    Dropdown(
                        label = "اختر المنتج",
                        selectedValue = uiState.selectedProduct?.name ?: "",
                        options = uiState.products.map { it.name },
                        onOptionSelected = { index -> viewModel.onProductSelected(uiState.products[index]) },
                        enabled = true
                    )
                }

                item {
                    Dropdown(
                        label = "اختر الصنف (السعة)",
                        selectedValue = uiState.selectedVariant?.let { "${it.capacity} أمبير" } ?: "",
                        options = uiState.variants.map { "${it.capacity} أمبير" },
                        onOptionSelected = { index -> viewModel.onVariantSelected(uiState.variants[index]) },
                        enabled = uiState.selectedProduct != null
                    )
                }

                item {
                    Dropdown(
                        label = "اختر المستودع",
                        selectedValue = uiState.selectedWarehouse?.name ?: "",
                        options = uiState.warehouses.map { it.name },
                        onOptionSelected = { index -> viewModel.onWarehouseSelected(uiState.warehouses[index]) },
                        enabled = true
                    )
                }

                item {
                    val availableQty = uiState.selectedVariant?.let { uiState.stockLevels[Pair(it.id, uiState.selectedWarehouse?.id ?: "")] ?: 0 } ?: 0
                    Text("الكمية المتاحة: $availableQty")
                }

                item {
                    OutlinedTextField(value = uiState.quantity, onValueChange = viewModel::onQuantityChanged, label = { Text("الكمية") }, modifier = Modifier.fillMaxWidth())
                }

                item {
                    OutlinedTextField(value = uiState.sellingPrice, onValueChange = viewModel::onSellingPriceChanged, label = { Text("سعر البيع") }, modifier = Modifier.fillMaxWidth())
                }

                item {
                    OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("اسم العميل") }, modifier = Modifier.fillMaxWidth())
                }

                item {
                    OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("رقم هاتف العميل") }, modifier = Modifier.fillMaxWidth())
                }

                item {
                    OutlinedTextField(value = paidAmount, onValueChange = { paidAmount = it }, label = { Text("المبلغ المدفوع") }, modifier = Modifier.fillMaxWidth())
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.selectedVariant != null && uiState.selectedWarehouse != null
                    ) {
                        Text("إنشاء عملية بيع")
                    }
                }
            }
        }
    }
}
