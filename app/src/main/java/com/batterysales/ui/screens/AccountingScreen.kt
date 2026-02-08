package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

    val filteredTransactions = remember(transactions, selectedTab, dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) {
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
            matchesTab && matchesDateRange
        }.sortedByDescending { it.createdAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الخزينة والمحاسبة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "تحديد الفترة", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // بطاقة الرصيد الحالي
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "الرصيد الحالي في الخزينة",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "JD ${String.format("%.3f", balance)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("الكل", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("المصروفات", modifier = Modifier.padding(16.dp))
                }
            }

            if (dateRangePickerState.selectedStartDateMillis != null) {
                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الفترة: ${sdf.format(Date(dateRangePickerState.selectedStartDateMillis!!))} - ${sdf.format(Date(dateRangePickerState.selectedEndDateMillis ?: dateRangePickerState.selectedStartDateMillis!!))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        dateRangePickerState.setSelection(null, null)
                    }) {
                        Text("إلغاء الفلترة")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "لا توجد عمليات تطابق البحث",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTransactions, key = { it.id }) { transaction ->
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
            onAdd = { type, desc, amount ->
                viewModel.addManualTransaction(type, amount, desc)
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
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("موافق")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f)
            )
        }
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
    val amountColor = if (isIncome) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isIncome) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${if (isIncome) "+" else "-"} JD ${String.format("%.3f", transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    modifier = Modifier.weight(1f)
                )
                if (transaction.relatedId == null) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                transaction.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                dateFormatter.format(transaction.createdAt),
                style = MaterialTheme.typography.bodyMedium,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onAdd: (TransactionType, String, Double) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == TransactionType.INCOME) "إيداع مبلغ" else "سحب مبلغ / مصروف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (description.isNotEmpty() && amt > 0) onAdd(type, description, amt)
            }, colors = ButtonDefaults.buttonColors(
                containerColor = if (type == TransactionType.INCOME) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )) { Text("تأكيد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
