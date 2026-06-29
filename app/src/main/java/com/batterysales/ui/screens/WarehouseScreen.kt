package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.viewmodel.WarehouseViewModel
import com.batterysales.data.models.Warehouse
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.CustomKeyboardTextField
import com.batterysales.ui.components.HeaderIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseScreen(
    navController: NavController,
    viewModel: WarehouseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = com.batterysales.ui.components.LocalCustomKeyboardController.current

    var showAddDialog by remember { mutableStateOf(false) }
    var warehouseToEdit by remember { mutableStateOf<Warehouse?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Warehouse?>(null) }

    Scaffold(
        topBar = {
            SharedHeader(
                title = "المستودع والمخزون",
                onBackClick = { navController.popBackStack() },
                actions = {
                    if (uiState.isAdmin) {
                        HeaderIconButton(
                            icon = Icons.Default.AddBusiness,
                            onClick = { showAddDialog = true },
                            contentDescription = "إضافة مستودع"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Warehouse Selection (Admin only)
            if (uiState.isAdmin && uiState.warehouses.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                val selectedWh = uiState.warehouses.find { it.id == uiState.selectedWarehouseId }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedWh?.name ?: "اختر مستودع",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("المستودع") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            uiState.warehouses.forEach { wh ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(wh.name)
                                            if (wh.isMain) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFB8C00), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.onWarehouseSelected(wh.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (uiState.isAdmin && selectedWh != null) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { warehouseToEdit = selectedWh }) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showDeleteConfirm = selectedWh }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Search Bar
            CustomKeyboardTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                label = "بحث عن بطارية...",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                onSearch = { keyboardController.hideKeyboard() }
            )

            // List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.inventoryItems.forEach { item ->
                    item {
                        val stock = item.totalQuantity
                        val threshold = item.variant.minQuantities[uiState.selectedWarehouseId] ?: item.variant.minQuantity
                        val isLowStock = threshold > 0 && stock <= threshold

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isLowStock) 4.dp else 2.dp),
                            border = if (isLowStock) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLowStock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isLowStock) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "تحذير",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = item.product.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLowStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${item.variant.capacity}A",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.variant.specification.isNotEmpty()) {
                                            Text(
                                                text = " | ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = (if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.5f)
                                            )
                                            Text(
                                                text = item.variant.specification,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                
                                Surface(
                                    color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                ) {
                                    Text(
                                        text = stock.toString(),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isLowStock) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        if (uiState.isSubmitting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFB8C00))
                        Text("جاري حفظ التغييرات والمزامنة...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }

    if (showAddDialog) {
        WarehouseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, location, isMain ->
                viewModel.addWarehouse(name, location, isMain)
                showAddDialog = false
            }
        )
    }

    warehouseToEdit?.let { warehouse ->
        WarehouseDialog(
            warehouse = warehouse,
            onDismiss = { warehouseToEdit = null },
            onConfirm = { name, location, isMain ->
                viewModel.updateWarehouse(warehouse.copy(name = name, location = location, isMain = isMain))
                warehouseToEdit = null
            }
        )
    }

    showDeleteConfirm?.let { warehouse ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("حذف مستودع") },
            text = { Text("هل أنت متأكد من حذف مستودع '${warehouse.name}'؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWarehouse(warehouse.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun WarehouseDialog(
    warehouse: Warehouse? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(warehouse?.name ?: "") }
    var location by remember { mutableStateOf(warehouse?.location ?: "") }
    var isMain by remember { mutableStateOf(warehouse?.isMain ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (warehouse == null) "إضافة مستودع" else "تعديل مستودع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المستودع") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("الموقع/العنوان") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isMain, onCheckedChange = { isMain = it })
                    Text("تعيين كمستودع رئيسي")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, location, isMain) },
                enabled = name.isNotBlank()
            ) {
                Text(if (warehouse == null) "إضافة" else "حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
 
