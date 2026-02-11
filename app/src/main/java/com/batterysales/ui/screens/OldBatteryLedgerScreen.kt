package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.data.models.OldBatteryTransaction
import com.batterysales.data.models.OldBatteryTransactionType
import com.batterysales.viewmodel.OldBatteryViewModel
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OldBatteryLedgerScreen(
    navController: NavHostController,
    viewModel: OldBatteryViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.transactions.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val isSeller by viewModel.isSeller.collectAsState()
    val userWarehouseId by viewModel.userWarehouseId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var selectedFilterWH by remember { mutableStateOf<String?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    
    val transactions = remember(allTransactions, selectedFilterWH, dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) {
        allTransactions.filter { transaction ->
            val matchesWarehouse = if (selectedFilterWH == null) true else transaction.warehouseId == selectedFilterWH
            val matchesDateRange = if (dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null) {
                transaction.date.time >= dateRangePickerState.selectedStartDateMillis!! &&
                        transaction.date.time <= dateRangePickerState.selectedEndDateMillis!! + 86400000 // End of day
            } else true
            matchesWarehouse && matchesDateRange
        }
    }
    
    val summary = remember(transactions) {
        var totalQty = 0
        var totalAmperes = 0.0
        transactions.forEach {
            when (it.type) {
                OldBatteryTransactionType.INTAKE -> {
                    totalQty += it.quantity
                    totalAmperes += it.totalAmperes
                }
                OldBatteryTransactionType.SALE -> {
                    totalQty -= it.quantity
                    totalAmperes -= it.totalAmperes
                }
                OldBatteryTransactionType.ADJUSTMENT -> {
                    totalQty += it.quantity
                    totalAmperes += it.totalAmperes
                }
            }
        }
        Pair(totalQty, totalAmperes)
    }

    var showSaleDialog by remember { mutableStateOf<OldBatteryTransaction?>(null) }
    var showEditDialog by remember { mutableStateOf<OldBatteryTransaction?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<OldBatteryTransaction?>(null) }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    )

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = accentColor,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header Section
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
                                text = "سجل السكراب",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { showDateRangePicker = true },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Date Range", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Summary Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.End) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("إجمالي الكمية", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                        Text("${summary.first}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("إجمالي الأمبيرات", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                        Text("${String.format("%.1f", summary.second)} A", color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                                
                                if (summary.first > 0) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            showSaleDialog = com.batterysales.data.models.OldBatteryTransaction(
                                                quantity = summary.first,
                                                totalAmperes = summary.second
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("بيع سكراب بالجملة", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (dateRangePickerState.selectedStartDateMillis != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        com.batterysales.ui.components.DateRangeInfo(
                            startDate = dateRangePickerState.selectedStartDateMillis,
                            endDate = dateRangePickerState.selectedEndDateMillis,
                            onClear = { dateRangePickerState.setSelection(null, null) }
                        )
                    }
                }
            }

            if (!isSeller && warehouses.isNotEmpty()) {
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilterWH == null,
                                onClick = { selectedFilterWH = null },
                                label = { Text("الكل") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor, selectedLabelColor = Color.White)
                            )
                        }
                        items(warehouses) { warehouse ->
                            FilterChip(
                                selected = selectedFilterWH == warehouse.id,
                                onClick = { selectedFilterWH = warehouse.id },
                                label = { Text(warehouse.name) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor, selectedLabelColor = Color.White)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else if (transactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد عمليات مسجلة", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(transactions, key = { it.id }) { transaction ->
                    val warehouseName = warehouses.find { it.id == transaction.warehouseId }?.name ?: "غير معروف"
                    OldBatteryTransactionCard(
                        transaction = transaction,
                        warehouseName = warehouseName,
                        onEdit = { showEditDialog = transaction },
                        onDelete = { showDeleteConfirm = transaction },
                        onSell = { showSaleDialog = transaction }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditOldBatteryDialog(
            warehouses = warehouses,
            isSeller = isSeller,
            userWarehouseId = userWarehouseId,
            onDismiss = { showAddDialog = false },
            onConfirm = { qty, amps, notes, whId ->
                viewModel.addManualIntake(qty, amps, notes, whId)
                showAddDialog = false
            }
        )
    }

    if (showEditDialog != null) {
        AddEditOldBatteryDialog(
            transaction = showEditDialog,
            warehouses = warehouses,
            isSeller = isSeller,
            userWarehouseId = userWarehouseId,
            onDismiss = { showEditDialog = null },
            onConfirm = { qty, amps, notes, whId ->
                viewModel.updateTransaction(showEditDialog!!.copy(quantity = qty, totalAmperes = amps, notes = notes, warehouseId = whId))
                showEditDialog = null
            }
        )
    }

    if (showSaleDialog != null) {
        SellOldBatteryDialog(
            transaction = showSaleDialog!!,
            onDismiss = { showSaleDialog = null },
            onConfirm = { qty, amps, price ->
                viewModel.sellBatteries(qty, amps, price, showSaleDialog!!.warehouseId)
                showSaleDialog = null
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("حذف السجل") },
            text = { Text("هل أنت متأكد من حذف هذا السجل؟ سيتم تحديث المخزون تلقائياً.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteTransaction(showDeleteConfirm!!.id)
                    showDeleteConfirm = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("إلغاء") }
            }
        )
    }

    if (showDateRangePicker) {
        com.batterysales.ui.components.AppDateRangePickerDialog(
            state = dateRangePickerState,
            onDismiss = { showDateRangePicker = false },
            onConfirm = { showDateRangePicker = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OldBatteryTransactionCard(
    transaction: OldBatteryTransaction,
    warehouseName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSell: () -> Unit
) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val isIntake = transaction.type == OldBatteryTransactionType.INTAKE
    val typeColor = if (isIntake) Color(0xFF10B981) else Color(0xFFEF4444)
    val accentColor = Color(0xFFFB8C00)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isIntake) "استلام سكراب" else "عملية بيع سكراب",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "مستودع: $warehouseName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Surface(
                    color = typeColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isIntake) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isIntake) "استلام" else "بيع",
                            color = typeColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(
                        text = "${transaction.quantity} حبة | ${transaction.totalAmperes}A",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!isIntake) {
                        Text(
                            text = "JD ${String.format("%,.3f", transaction.amount)}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = dateFormatter.format(transaction.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isIntake && transaction.quantity > 0) {
                        IconButton(
                            onClick = onSell,
                            modifier = Modifier.size(36.dp).background(accentColor.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Sell, contentDescription = "Sell", tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).background(Color(0xFF3B1F1F), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            if (transaction.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = transaction.notes,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AddEditOldBatteryDialog(
    transaction: OldBatteryTransaction? = null,
    warehouses: List<com.batterysales.data.models.Warehouse>,
    isSeller: Boolean,
    userWarehouseId: String?,
    onDismiss: () -> Unit,
    onConfirm: (Int, Double, String, String) -> Unit
) {
    var qty by remember { mutableStateOf(transaction?.quantity?.toString() ?: "") }
    var amps by remember { mutableStateOf(transaction?.totalAmperes?.toString() ?: "") }
    var notes by remember { mutableStateOf(transaction?.notes ?: "") }
    
    // Default to user warehouse if seller, or existing transaction warehouse, or first warehouse
    val initialWH = if (isSeller) userWarehouseId ?: "" 
                   else transaction?.warehouseId ?: (if (warehouses.isNotEmpty()) warehouses[0].id else "")
                   
    var selectedWarehouseId by remember { mutableStateOf(initialWH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction == null) "إضافة بطاريات قديمة" else "تعديل السجل") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (warehouses.isNotEmpty() && !isSeller) {
                    Text("المستودع:", style = MaterialTheme.typography.titleSmall)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(warehouses) { warehouse ->
                            FilterChip(
                                selected = selectedWarehouseId == warehouse.id,
                                onClick = { selectedWarehouseId = warehouse.id },
                                label = { Text(warehouse.name) }
                            )
                        }
                    }
                } else if (isSeller) {
                    val whName = warehouses.find { it.id == userWarehouseId }?.name ?: "المستودع الخاص بك"
                    Text("المستودع: $whName", style = MaterialTheme.typography.bodyMedium)
                }

                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = "الكمية"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = amps,
                    onValueChange = { amps = it },
                    label = "إجمالي الأمبيرات"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "ملاحظات"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.toIntOrNull() ?: 0
                val a = amps.toDoubleOrNull() ?: 0.0
                if (q > 0 && selectedWarehouseId.isNotEmpty()) onConfirm(q, a, notes, selectedWarehouseId)
            }) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun SellOldBatteryDialog(
    transaction: OldBatteryTransaction,
    onDismiss: () -> Unit,
    onConfirm: (Int, Double, Double) -> Unit
) {
    var qty by remember { mutableStateOf(transaction.quantity.toString()) }
    var amps by remember { mutableStateOf(transaction.totalAmperes.toString()) }
    var price by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بيع بطاريات قديمة (سكراب)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = "الكمية المباعة"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = amps,
                    onValueChange = { amps = it },
                    label = "إجمالي الأمبيرات"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = "سعر البيع الإجمالي"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.toIntOrNull() ?: 0
                val a = amps.toDoubleOrNull() ?: 0.0
                val p = price.toDoubleOrNull() ?: 0.0
                if (q > 0 && p > 0) onConfirm(q, a, p)
            }) { Text("تأكيد البيع") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
