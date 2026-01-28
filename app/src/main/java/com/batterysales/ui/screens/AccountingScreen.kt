package com.batterysales.ui.screens

import androidx.compose.foundation.background
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
                )
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(
                    onClick = {
                        selectedType = TransactionType.INCOME
                        showAddTransactionDialog = true
                    },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إيداع")
                }
                FloatingActionButton(
                    onClick = {
                        selectedType = TransactionType.EXPENSE
                        showAddTransactionDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.error,
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
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "الرصيد الحالي في الخزينة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "SR ${String.format("%.2f", balance)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "آخر العمليات المالية",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "لا توجد عمليات مالية مسجلة",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(transactions) { transaction ->
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
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isIncome) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = amountColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.description, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    dateFormatter.format(transaction.createdAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (isIncome) "+" else "-"} SR ${String.format("%.2f", transaction.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("تعديل") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
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
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
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
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
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
