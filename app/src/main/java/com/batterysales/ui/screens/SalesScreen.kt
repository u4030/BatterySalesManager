package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    navController: NavHostController,
    viewModel: SalesViewModel = hiltViewModel()
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paidAmount by remember { mutableStateOf("") }

    var expandedProduct by remember { mutableStateOf(false) }
    var expandedWarehouse by remember { mutableStateOf(false) }

    val selectedProduct = viewModel.selectedProduct.value
    val selectedWarehouse = viewModel.selectedWarehouse.value
    val availableQuantity = if (selectedProduct != null && selectedWarehouse != null) {
        viewModel.getAvailableQuantity(selectedProduct.id, selectedWarehouse.id)
    } else {
        0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مبيعة جديدة", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Customer Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بيانات العميل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("اسم العميل") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("رقم جوال العميل") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                }
            }

            // Sale Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تفاصيل المبيعة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    // Product Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedProduct,
                        onExpandedChange = { expandedProduct = !expandedProduct }
                    ) {
                        TextField(
                            modifier = Modifier.menuAnchor(),
                            readOnly = true,
                            value = selectedProduct?.name ?: "",
                            onValueChange = {},
                            label = { Text("المنتج") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProduct,
                            onDismissRequest = { expandedProduct = false },
                        ) {
                            viewModel.products.value.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text(product.name) },
                                    onClick = {
                                        viewModel.onProductSelected(product)
                                        expandedProduct = false
                                    }
                                )
                            }
                        }
                    }

                    // Warehouse Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedWarehouse,
                        onExpandedChange = { expandedWarehouse = !expandedWarehouse }
                    ) {
                        TextField(
                            modifier = Modifier.menuAnchor(),
                            readOnly = true,
                            value = selectedWarehouse?.name ?: "",
                            onValueChange = {},
                            label = { Text("المستودع") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWarehouse) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = expandedWarehouse,
                            onDismissRequest = { expandedWarehouse = false },
                        ) {
                            viewModel.warehouses.value.forEach { warehouse ->
                                DropdownMenuItem(
                                    text = { Text(warehouse.name) },
                                    onClick = {
                                        viewModel.selectedWarehouse.value = warehouse
                                        expandedWarehouse = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = viewModel.quantity.value,
                            onValueChange = { viewModel.quantity.value = it },
                            label = { Text("الكمية") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            supportingText = { Text("المتاح: $availableQuantity") }
                        )
                        OutlinedTextField(
                            value = viewModel.sellingPrice.value,
                            onValueChange = { viewModel.sellingPrice.value = it },
                            label = { Text("السعر") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Payment
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الدفع", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = paidAmount, onValueChange = { paidAmount = it }, label = { Text("المبلغ المدفوع") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
            }

            Button(
                onClick = {
                    viewModel.createSale(
                        customerName = customerName,
                        customerPhone = customerPhone,
                        paidAmount = paidAmount.toDoubleOrNull() ?: 0.0
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ المبيعة وإنشاء فاتورة")
            }
        }
    }
}
