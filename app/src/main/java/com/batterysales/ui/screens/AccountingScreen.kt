package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.batterysales.data.models.Transaction
import com.batterysales.data.models.TransactionType
import com.batterysales.viewmodel.AccountingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingScreen(
    navController: NavHostController,
    viewModel: AccountingViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(TransactionType.INCOME) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Transaction?>(null) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    var searchQuery by remember { mutableStateOf("") }

    val filteredTransactions = remember(transactions, selectedTab, dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis, searchQuery) {
        transactions.filter { transaction ->
            val matchesTab = when (selectedTab) {
                0 -> true // All
                1 -> transaction.type == TransactionType.EXPENSE || transaction.type == TransactionType.REFUND // Expenses
                else -> true
            }
            val matchesDateRange = if (dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null) {
                transaction.createdAt.time >= dateRangePickerState.selectedStartDateMillis!! &&
                        transaction.createdAt.time <= dateRangePickerState.selectedEndDateMillis!! + 86400000 // End of day
            } else true
            val matchesSearch = transaction.description.contains(searchQuery, ignoreCase = true) || 
                               transaction.referenceNumber.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesDateRange && matchesSearch
        }.sortedByDescending { it.createdAt }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    )

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(
                    onClick = {
                        selectedType = TransactionType.INCOME
                        showAddTransactionDialog = true
                    },
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.7f),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إيداع")
                }
                FloatingActionButton(
                    onClick = {
                        selectedType = TransactionType.EXPENSE
                        showAddTransactionDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "سحب")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
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
                                text = "الخزينة والمحاسبة",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Row {
                                IconButton(
                                    onClick = { showDateRangePicker = true },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Date", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.loadData() },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // بطاقة الرصيد الحالي داخل الهيدر
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "إجمالي الرصيد الحالي",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "JD ${String.format("%.3f", balance)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = accentColor,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = accentColor
                            )
                        },
                        divider = {}
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                            Text("الكل", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = if(selectedTab == 0) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                            Text("المصروفات", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = if(selectedTab == 1) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("بحث بالوصف أو الرقم المرجعي...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = accentColor) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        textStyle = com.batterysales.ui.theme.LocalInputTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )

                    if (dateRangePickerState.selectedStartDateMillis != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        com.batterysales.ui.components.DateRangeInfo(
                            startDate = dateRangePickerState.selectedStartDateMillis,
                            endDate = dateRangePickerState.selectedEndDateMillis,
                            onClear = { dateRangePickerState.setSelection(null, null) }
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else if (filteredTransactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "لا توجد عمليات تطابق البحث",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { transaction ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TransactionItemCard(
                            transaction = transaction,
                            onEdit = { transactionToEdit = transaction },
                            onDelete = { showDeleteConfirm = transaction }
                        )
                    }
                }
            }
        }
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            type = selectedType,
            onDismiss = { showAddTransactionDialog = false },
            onAdd = { type, desc, amount, ref ->
                viewModel.addManualTransaction(type, amount, desc, ref)
                showAddTransactionDialog = false
            }
        )
    }

    if (transactionToEdit != null) {
        EditTransactionDialog(
            transaction = transactionToEdit!!,
            onDismiss = { transactionToEdit = null },
            onConfirm = { updated ->
                viewModel.updateTransaction(updated)
                transactionToEdit = null
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

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("حذف العملية") },
            text = { Text("هل أنت متأكد من حذف هذه العملية المالية؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(showDeleteConfirm!!.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun TransactionItemCard(
    transaction: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    val isIncome = transaction.type == TransactionType.INCOME || transaction.type == TransactionType.PAYMENT
    val amountColor = if (isIncome) Color(0xFF10B981) else Color(0xFFEF4444)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = amountColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isIncome) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = amountColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isIncome) "إيداع" else "سحب",
                            color = amountColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (transaction.relatedId == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onEdit, 
                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onDelete, 
                            modifier = Modifier.size(32.dp).background(Color(0xFF3B1F1F), CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${if (isIncome) "+" else "-"} JD ${String.format("%,.3f", transaction.amount)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) Color(0xFF10B981) else Color(0xFFEF4444)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                transaction.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (transaction.referenceNumber.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "رقم المرجع: ${transaction.referenceNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFB8C00),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                dateFormatter.format(transaction.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onConfirm: (Transaction) -> Unit
) {
    var description by remember { mutableStateOf(transaction.description) }
    var amount by remember { mutableStateOf(transaction.amount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل العملية") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "الوصف"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "المبلغ"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: transaction.amount
                onConfirm(transaction.copy(description = description, amount = amt))
            }) { Text("تأكيد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun AddTransactionDialog(
    type: TransactionType,
    onDismiss: () -> Unit,
    onAdd: (TransactionType, String, Double, String) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == TransactionType.INCOME) "إيداع مبلغ" else "سحب مبلغ / مصروف") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "الوصف"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = "رقم المرجع (اختياري)"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "المبلغ"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (description.isNotEmpty() && amt > 0) onAdd(type, description, amt, referenceNumber)
            }, colors = ButtonDefaults.buttonColors(
                containerColor = if (type == TransactionType.INCOME) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )) { Text("تأكيد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
