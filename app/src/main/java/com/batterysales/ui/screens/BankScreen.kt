package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.navigation.NavHostController
import com.batterysales.data.models.BankTransaction
import com.batterysales.data.models.BankTransactionType
import com.batterysales.viewmodel.BankViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(
    navController: NavHostController,
    viewModel: BankViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(com.batterysales.data.models.BankTransactionType.DEPOSIT) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    val filteredTransactions = remember(transactions, selectedTab, dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) {
        transactions.filter { transaction ->
            val matchesTab = when (selectedTab) {
                0 -> true // All
                1 -> transaction.type == com.batterysales.data.models.BankTransactionType.WITHDRAWAL // Withdrawals
                else -> true
            }
            val matchesDateRange = if (dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null) {
                transaction.date.time >= dateRangePickerState.selectedStartDateMillis!! &&
                        transaction.date.time <= dateRangePickerState.selectedEndDateMillis!! + 86400000 // End of day
            } else true
            matchesTab && matchesDateRange
        }.sortedByDescending { it.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("البنك والشيكات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "تحديد الفترة", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        selectedType = com.batterysales.data.models.BankTransactionType.DEPOSIT
                        showAddDialog = true
                    },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("إيداع شيك") }
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        selectedType = com.batterysales.data.models.BankTransactionType.WITHDRAWAL
                        showAddDialog = true
                    },
                    containerColor = Color(0xFFF44336),
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Remove, contentDescription = null) },
                    text = { Text("سحب شيك") }
                )
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
            // Balance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFBBDEFB))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "إجمالي رصيد الشيكات في البنك",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1976D2),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "JD ${String.format("%.3f", balance)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
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
                    Text("المسحوبات", modifier = Modifier.padding(16.dp))
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
                        BankTransactionItemCard(transaction)
                    }
                }
            }
        }
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

    if (showAddDialog) {
        AddBankTransactionDialog(
            type = selectedType,
            onDismiss = { showAddDialog = false },
            onAdd = { type, desc, amount ->
                viewModel.addManualTransaction(type, amount, desc)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddBankTransactionDialog(
    type: com.batterysales.data.models.BankTransactionType,
    onDismiss: () -> Unit,
    onAdd: (com.batterysales.data.models.BankTransactionType, String, Double) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == com.batterysales.data.models.BankTransactionType.DEPOSIT) "إيداع في البنك" else "سحب من البنك") },
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
                containerColor = if (type == com.batterysales.data.models.BankTransactionType.DEPOSIT) Color(0xFF4CAF50) else Color(0xFFF44336)
            )) { Text("تأكيد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun BankTransactionItemCard(transaction: BankTransaction) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    val isDeposit = transaction.type == BankTransactionType.DEPOSIT
    val amountColor = if (isDeposit) Color(0xFF4CAF50) else Color(0xFFF44336)

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
                    imageVector = if (isDeposit) Icons.Default.AccountBalanceWallet else Icons.Default.Payments,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${if (isDeposit) "+" else "-"} JD ${String.format("%.3f", transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                transaction.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                dateFormatter.format(transaction.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
