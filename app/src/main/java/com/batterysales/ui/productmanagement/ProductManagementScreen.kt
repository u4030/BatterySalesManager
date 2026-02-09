package com.batterysales.ui.productmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.viewmodel.ProductManagementViewModel

@Composable
fun ProductManagementScreen(viewModel: ProductManagementViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddProductDialog by remember { mutableStateOf(false) }
    var showAddVariantDialog by remember { mutableStateOf(false) }

    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var variantToEdit by remember { mutableStateOf<ProductVariant?>(null) }

    var productToArchive by remember { mutableStateOf<Product?>(null) }
    var variantToArchive by remember { mutableStateOf<ProductVariant?>(null) }

    // Dialogs
    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onAddProduct = { name, specification -> viewModel.addProduct(name, specification) }
        )
    }

    productToEdit?.let { product ->
        EditProductDialog(
            product = product,
            onDismiss = { productToEdit = null },
            onUpdateProduct = { updatedProduct -> viewModel.updateProduct(updatedProduct) }
        )
    }

    if (showAddVariantDialog) {
        AddVariantDialog(
            warehouses = uiState.warehouses,
            onDismiss = { showAddVariantDialog = false },
            onAddVariant = { capacity, sellingPrice, barcode, minQuantity, minQuantities, specification ->
                viewModel.addVariant(capacity, sellingPrice, barcode, minQuantity, minQuantities, specification)
            }
        )
    }

    variantToEdit?.let { variant ->
        EditVariantDialog(
            variant = variant,
            warehouses = uiState.warehouses,
            onDismiss = { variantToEdit = null },
            onUpdateVariant = { updatedVariant -> viewModel.updateVariant(updatedVariant) }
        )
    }

    productToArchive?.let { product ->
        DeleteConfirmationDialog(
            itemName = product.name,
            onDismiss = { productToArchive = null },
            onConfirm = { viewModel.archiveProduct(product) }
        )
    }

    variantToArchive?.let { variant ->
        DeleteConfirmationDialog(
            itemName = "${variant.capacity} Amp",
            onDismiss = { variantToArchive = null },
            onConfirm = { viewModel.archiveVariant(variant) }
        )
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        Text("إدارة المنتجات", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        var searchQuery by remember { mutableStateOf("") }
        var showSearchScanner by remember { mutableStateOf(false) }

        if (showSearchScanner) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showSearchScanner = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    BarcodeScanner(onBarcodeScanned = {
                        searchQuery = it
                        viewModel.onBarcodeFilterChanged(it)
                        showSearchScanner = false
                    })
                    IconButton(
                        onClick = { showSearchScanner = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                viewModel.onBarcodeFilterChanged(it)
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            placeholder = { Text("بحث بالاسم أو الباركود...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    IconButton(onClick = { showSearchScanner = true }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود")
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.onBarcodeFilterChanged("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Button(
            onClick = { showAddProductDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("إضافة منتج جديد")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
            items(uiState.products) { product ->
                ProductCard(
                    product = product,
                    isSelected = uiState.selectedProduct?.id == product.id,
                    variants = if (uiState.selectedProduct?.id == product.id) uiState.variants else emptyList(),
                    onProductClick = { viewModel.selectProduct(product) },
                    onProductEdit = { productToEdit = product },
                    onProductDelete = { productToArchive = product },
                    onAddVariant = { showAddVariantDialog = true },
                    onVariantEdit = { variantToEdit = it },
                    onVariantDelete = { variantToArchive = it }
                )
            }
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    isSelected: Boolean,
    variants: List<ProductVariant>,
    onProductClick: () -> Unit,
    onProductEdit: () -> Unit,
    onProductDelete: () -> Unit,
    onAddVariant: () -> Unit,
    onVariantEdit: (ProductVariant) -> Unit,
    onVariantDelete: (ProductVariant) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onProductClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(onClick = onProductEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onProductDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error) }
                }
            }

            if (product.specification.isNotEmpty()) {
                Text(product.specification, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("الأصناف المتوفرة (السعات):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onAddVariant) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة سعة")
                    }
                }

                if (variants.isEmpty()) {
                    Text("لا توجد سعات مضافة لهذا المنتج.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    variants.forEach { variant ->
                        VariantItemRow(
                            variant = variant,
                            onEdit = { onVariantEdit(variant) },
                            onDelete = { onVariantDelete(variant) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VariantItemRow(variant: ProductVariant, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${variant.capacity} أمبير", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("المواصفة: ${variant.specification}", style = MaterialTheme.typography.bodyMedium)
                Text("الباركود: ${variant.barcode}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}


// Dialog Composables (Add, Edit, Delete Confirmation)

@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onAddProduct: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var specification by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة منتج جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "اسم المنتج"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = specification,
                    onValueChange = { specification = it },
                    label = "المواصفة"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = { Button(onClick = { if(name.isNotBlank()) onAddProduct(name, specification); onDismiss() }) { Text("إضافة") } },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun EditProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onUpdateProduct: (Product) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var specification by remember { mutableStateOf(product.specification) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل المنتج") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "اسم المنتج"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = specification,
                    onValueChange = { specification = it },
                    label = "المواصفة"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                if(name.isNotBlank()) onUpdateProduct(product.copy(name = name, specification = specification))
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddVariantDialog(
    warehouses: List<Warehouse>,
    onDismiss: () -> Unit,
    onAddVariant: (Int, Double, String, Int, Map<String, Int>, String) -> Unit
) {
    var capacity by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var minQuantity by remember { mutableStateOf("") }
    var minQuantities by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var specification by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                BarcodeScanner(onBarcodeScanned = {
                    barcode = it
                    showScanner = false
                })
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة سعة جديدة") },
            text = {
                Column {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        com.batterysales.ui.components.CustomKeyboardTextField(
                            value = capacity,
                            onValueChange = { capacity = it },
                            label = "السعة (أمبير)",
                            modifier = Modifier.widthIn(min = 120.dp)
                        )
                        com.batterysales.ui.components.CustomKeyboardTextField(
                            value = sellingPrice,
                            onValueChange = { sellingPrice = it },
                            label = "سعر البيع",
                            modifier = Modifier.widthIn(min = 120.dp)
                        )
                        Box(modifier = Modifier.widthIn(min = 120.dp)) {
                            com.batterysales.ui.components.CustomKeyboardTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = "الباركود",
                                modifier = Modifier.fillMaxWidth()
                            )
                            IconButton(
                                onClick = { showScanner = true },
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود")
                            }
                        }
                        com.batterysales.ui.components.CustomKeyboardTextField(
                            value = minQuantity,
                            onValueChange = { minQuantity = it },
                            label = "الحد الأدنى العام",
                            modifier = Modifier.widthIn(min = 120.dp)
                        )

                        warehouses.forEach { warehouse ->
                            com.batterysales.ui.components.CustomKeyboardTextField(
                                value = minQuantities[warehouse.id] ?: "",
                                onValueChange = { newValue ->
                                    val newMap = minQuantities.toMutableMap()
                                    newMap[warehouse.id] = newValue
                                    minQuantities = newMap
                                },
                                label = "الحد الأدنى (${warehouse.name})",
                                modifier = Modifier.widthIn(min = 120.dp)
                            )
                        }

                        com.batterysales.ui.components.CustomKeyboardTextField(
                            value = specification,
                            onValueChange = { specification = it },
                            label = "المواصفة",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddVariant(
                        capacity.toIntOrNull() ?: 0,
                        sellingPrice.toDoubleOrNull() ?: 0.0,
                        barcode,
                        minQuantity.toIntOrNull() ?: 0,
                        minQuantities.mapValues { it.value.toIntOrNull() ?: 0 },
                        specification
                    )
                    onDismiss()
                }) { Text("إضافة") }
            },
            dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
        )
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditVariantDialog(
    variant: ProductVariant,
    warehouses: List<Warehouse>,
    onDismiss: () -> Unit,
    onUpdateVariant: (ProductVariant) -> Unit
) {
    var capacity by remember { mutableStateOf(variant.capacity.toString()) }
    var sellingPrice by remember { mutableStateOf(variant.sellingPrice.toString()) }
    var barcode by remember { mutableStateOf(variant.barcode) }
    var minQuantity by remember { mutableStateOf(variant.minQuantity.toString()) }
    var minQuantities by remember { mutableStateOf(variant.minQuantities.mapValues { it.value.toString() }.toMutableMap()) }
    var specification by remember { mutableStateOf(variant.specification) }
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                BarcodeScanner(onBarcodeScanned = {
                    barcode = it
                    showScanner = false
                })
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل السعة") },
            text = {
                Column {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        com.batterysales.ui.components.CustomKeyboardTextField(
                            value = capacity,
                            onValueChange = { capacity = it },
                            label = "السعة (أمبير)",
                            modifier = Modifier.widthIn(min = 120.dp)
                        )
                        com.batterysales.ui.components.CustomKeyboardTextField(
                            value = sellingPrice,
                            onValueChange = { sellingPrice = it },
                            label = "سعر البيع",
                            modifier = Modifier.widthIn(min = 120.dp)
                        )
                        Box(modifier = Modifier.widthIn(min = 120.dp)) {
                            com.batterysales.ui.components.CustomKeyboardTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = "الباركود",
                                modifier = Modifier.fillMaxWidth()
                            )
                            IconButton(
                                onClick = { showScanner = true },
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود")
                            }
                        }
                        com.batterysales.ui.components.CustomKeyboardTextField(
                            value = minQuantity,
                            onValueChange = { minQuantity = it },
                            label = "الحد الأدنى العام",
                            modifier = Modifier.widthIn(min = 120.dp)
                        )

                        warehouses.forEach { warehouse ->
                            com.batterysales.ui.components.CustomKeyboardTextField(
                                value = minQuantities[warehouse.id] ?: "",
                                onValueChange = { newValue ->
                                    val newMap = minQuantities.toMutableMap()
                                    newMap[warehouse.id] = newValue
                                    minQuantities = newMap
                                },
                                label = "الحد الأدنى (${warehouse.name})",
                                modifier = Modifier.widthIn(min = 120.dp)
                            )
                        }

                        com.batterysales.ui.components.CustomKeyboardTextField(
                            value = specification,
                            onValueChange = { specification = it },
                            label = "المواصفة",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateVariant(variant.copy(
                        capacity = capacity.toIntOrNull() ?: 0,
                        sellingPrice = sellingPrice.toDoubleOrNull() ?: 0.0,
                        barcode = barcode,
                        minQuantity = minQuantity.toIntOrNull() ?: 0,
                        minQuantities = minQuantities.mapValues { it.value.toIntOrNull() ?: 0 },
                        specification = specification
                    ))
                    onDismiss()
                }) { Text("حفظ") }
            },
            dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
        )
}

@Composable
fun DeleteConfirmationDialog(itemName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأكيد الحذف") },
        text = { Text("هل أنت متأكد أنك تريد حذف '$itemName'؟ سيتم أرشفة هذا العنصر.") },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}
