package com.batterysales.ui.productmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.viewmodel.ProductManagementViewModel

@Composable
fun ProductManagementScreen(navController: NavHostController, viewModel: ProductManagementViewModel = hiltViewModel()) {
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

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
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
                    selected = true,
                    onClick = { },
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Gradient Header
            item {
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
                                text = "إدارة المنتجات",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { /* Refresh handled by flow */ },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

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
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("...بحث بالاسم أو الباركود", color = Color.White.copy(alpha = 0.6f)) },
                            leadingIcon = {
                                IconButton(onClick = { showSearchScanner = true }) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "Scan", tint = Color.White.copy(alpha = 0.7f))
                                }
                            },
                            trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.2f),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Add Product Button
                    Button(
                        onClick = { showAddProductDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إضافة منتج جديد", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            }

            items(uiState.products) { product ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val topBorderColor = when (product.name.length % 3) {
        0 -> Color(0xFFEF4444)
        1 -> Color(0xFFFB8C00)
        else -> Color(0xFFFACC15)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProductClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top colored border
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(topBorderColor))

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onProductDelete,
                            modifier = Modifier.size(36.dp).background(Color(0xFFEF4444).copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = onProductEdit,
                            modifier = Modifier.size(36.dp).background(accentColor.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                    }

                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = product.specification.ifEmpty { "بدون مواصفات" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onProductClick,
                        modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isSelected) "Collapse" else "Expand",
                            tint = accentColor
                        )
                    }
                }

                if (isSelected) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onAddVariant() }) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إضافة صنف", color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "الأصناف المتوفرة (${variants.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (variants.isEmpty()) {
                        Text("لا توجد سعات مضافة لهذا المنتج.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
fun VariantItemRow(variant: ProductVariant, onEdit: () -> Unit, onDelete: () -> Unit) {
    val accentColor = Color(0xFFFB8C00)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).background(Color(0xFFEF4444).copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp).background(accentColor.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentColor)
                }
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "${variant.capacity} Amp",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "المواصفة: ${variant.specification.ifEmpty { "عادية" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "الباركود: ${variant.barcode.ifEmpty { "---" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
