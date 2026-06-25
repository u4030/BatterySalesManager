package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.viewmodel.PurchaseOrderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderScreen(
    navController: NavHostController,
    viewModel: PurchaseOrderViewModel = hiltViewModel()
) {
//    val totalCost by viewModel.totalCost.collectAsState()
//    val orderItems by viewModel.orderItems.collectAsState()
//    val allProducts by viewModel.allProducts.collectAsState()
//    val isLoading by viewModel.isLoading.collectAsState()
//    val errorMessage by viewModel.errorMessage.collectAsState()
//    val successMessage by viewModel.successMessage.collectAsState()
    var showAddProductDialog by remember { mutableStateOf(false) }

//    LaunchedEffect(successMessage) {
//        if (successMessage != null) {
//            // TODO: Show a success snackbar or toast
//            navController.popBackStack()
//        }
//    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إنشاء طلبية شراء جديدة", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddProductDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منتج", tint = Color.White)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
//                errorMessage?.let {
//                    Text(it, color = MaterialTheme.colorScheme.error)
//                    Spacer(modifier = Modifier.height(8.dp))
//                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
//                    items(orderItems) { item ->
//                        Text("${item.product.name} (الكمية: ${item.quantity})")
//                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
//                OutlinedTextField(
//                    value = totalCost,
//                    onValueChange = { viewModel.setTotalCost(it) },
//                    label = { Text("إجمالي تكلفة الطلبية (دينار أردني)") },
//                    modifier = Modifier.fillMaxWidth()
//                )
//                Spacer(modifier = Modifier.height(16.dp))
//                Button(
//                    onClick = { viewModel.savePurchaseOrder() },
//                    modifier = Modifier.fillMaxWidth(),
//                    enabled = !isLoading
//                ) {
//                    Text("حفظ الطلبية")
//                }
            }
//            if (isLoading) {
//                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
//            }
        }
    }

//    if (showAddProductDialog) {
//        AddProductToOrderDialog(
//            products = allProducts,
//            onDismiss = { showAddProductDialog = false },
//            onAddProduct = { product, quantity ->
//                viewModel.addOrderItem(product, quantity)
//                showAddProductDialog = false
//            }
//        )
//    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductToOrderDialog(
    products: List<com.batterysales.data.models.Product>,
    onDismiss: () -> Unit,
    onAddProduct: (com.batterysales.data.models.Product, Int) -> Unit
) {
    var selectedProduct by remember { mutableStateOf<com.batterysales.data.models.Product?>(null) }
    var quantity by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة منتج إلى الطلبية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedProduct?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("اختر منتجًا") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        products.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name) },
                                onClick = {
                                    selectedProduct = product
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("الكمية") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedProduct?.let {
                        onAddProduct(it, quantity.toIntOrNull() ?: 0)
                    }
                },
                enabled = selectedProduct != null && quantity.toIntOrNull() ?: 0 > 0
            ) {
                Text("إضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
