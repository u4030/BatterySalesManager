package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OldBatteryLedgerScreen(
    navController: NavHostController,
    viewModel: OldBatteryViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.transactions.collectAsState()
    val globalSummary by viewModel.summary.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val isSeller by viewModel.isSeller.collectAsState()
    val userWarehouseId by viewModel.userWarehouseId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedFilterWH by remember { mutableStateOf<String?>(null) }

    val transactions = remember(allTransactions, selectedFilterWH) {
        if (selectedFilterWH == null) allTransactions
        else allTransactions.filter { it.warehouseId == selectedFilterWH }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل البطاريات القديمة (سكراب)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF5D4037),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF5D4037),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            if (!isSeller && warehouses.isNotEmpty()) {
                Text("تصفية حسب المستودع:", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilterWH == null,
                            onClick = { selectedFilterWH = null },
                            label = { Text("الكل") }
                        )
                    }
                    items(warehouses) { warehouse ->
                        FilterChip(
                            selected = selectedFilterWH == warehouse.id,
                            onClick = { selectedFilterWH = warehouse.id },
                            label = { Text(warehouse.name) }
                        )
                    }
                }

                // We should probably filter transactions and summary based on this selection locally
                // but let's just show it for now. Actually, let's make it work.
            }

            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD7CCC8))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 2
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 120.dp)) {
                            Text("إجمالي الكمية", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5D4037), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text("${summary.first}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 120.dp)) {
                            Text("إجمالي الأمبيرات", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5D4037), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text("${String.format("%.1f", summary.second)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showSaleDialog = com.batterysales.data.models.OldBatteryTransaction(
                                quantity = summary.first,
                                totalAmperes = summary.second
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037))
                    ) {
                        Text("بيع بالجملة (سكراب)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("سجل العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
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
}

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
    val typeColor = if (isIntake) Color(0xFF4CAF50) else Color(0xFFF44336)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isIntake) Icons.Default.CallReceived else Icons.Default.CallMade,
                    contentDescription = null,
                    tint = typeColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isIntake) "استلام بطاريات قديمة" else "بيع سكراب",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "المستودع: $warehouseName",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateFormatter.format(transaction.date),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("الكمية: ${transaction.quantity}", fontWeight = FontWeight.Bold)
                    Text("${transaction.totalAmperes} أمبير", fontSize = 14.sp)
                }
            }
            if (transaction.notes.isNotEmpty()) {
                Text(transaction.notes, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (!isIntake) {
                Text("المبلغ: JD ${String.format("%.3f", transaction.amount)}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), modifier = Modifier.padding(top = 4.dp))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (isIntake && transaction.quantity > 0) {
                    TextButton(onClick = onSell, modifier = Modifier.padding(end = 8.dp)) {
                        Text("بيع خردة", fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error) }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (warehouses.isNotEmpty() && !isSeller) {
                    Text("المستودع:", fontWeight = FontWeight.Bold)
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
                    Text("المستودع: $whName", fontWeight = FontWeight.Medium)
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
