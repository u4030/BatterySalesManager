package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountingScreen(
    navController: NavHostController,
    viewModel: AccountingViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val totalExpenses by viewModel.totalExpenses.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isLastPage by viewModel.isLastPage.collectAsState()
    val listState = rememberLazyListState()
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(TransactionType.INCOME) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Transaction?>(null) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    var searchQuery by remember { mutableStateOf("") }

    val warehouses by viewModel.warehouses.collectAsState()
    val selectedWarehouseId by viewModel.selectedWarehouseId.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val selectedPaymentMethod by viewModel.selectedPaymentMethod.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    val filteredTransactions = remember(transactions, searchQuery) {
        transactions.filter { transaction ->
            transaction.description.contains(searchQuery, ignoreCase = true) || 
            transaction.referenceNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    // Load more when reaching the end
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && !isLoadingMore && !isLastPage) {
            viewModel.loadData()
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    val canUseTreasury = remember(currentUser) {
        currentUser?.role == "admin" || currentUser?.permissions?.contains("use_treasury") == true
    }

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            if (canUseTreasury) {
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
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Gradient Header with Balance
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
                            ),
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                        .padding(bottom = 24.dp)
                        .statusBarsPadding()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HeaderIconButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    onClick = { navController.popBackStack() },
                                    contentDescription = "Back"
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "الخزينة والمحاسبة",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HeaderIconButton(
                                icon = Icons.Default.CalendarMonth,
                                onClick = { showDateRangePicker = true },
                                contentDescription = "Date Range"
                            )
                        }

                        // Warehouse selection for admins
                        if (currentUser?.role == "admin" && warehouses.isNotEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(warehouses) { warehouse ->
                                    FilterChip(
                                        selected = selectedWarehouseId == warehouse.id,
                                        onClick = { viewModel.onWarehouseSelected(warehouse.id) },
                                        label = { Text(warehouse.name, color = if(selectedWarehouseId == warehouse.id) Color.Black else Color.White) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            containerColor = Color.White.copy(alpha = 0.2f),
                                            labelColor = Color.White
                                        ),
                                        border = null
                                    )
                                }
                            }
                        }

                        // Balance Card inside the gradient
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val currentWhName = warehouses.find { it.id == selectedWarehouseId }?.name ?: "الخزينة العامة"
                                Text(
                                    "إجمالي الرصيد الحالي ($currentWhName)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    "JD ${String.format("%.3f", balance)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("إجمالي المصروفات", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                        Text("JD ${String.format("%.3f", totalExpenses)}", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("طريقة الدفع", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                        Text(
                                            when(selectedPaymentMethod) {
                                                "cash" -> "كاش"
                                                "e-wallet" -> "محفظة"
                                                "visa" -> "فيزا"
                                                else -> "الكل"
                                            },
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Year and Payment Method Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Year Selector
                        var yearExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            FilterChip(
                                selected = selectedYear != null,
                                onClick = { yearExpanded = true },
                                label = { Text(selectedYear?.toString() ?: "كل السنوات") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                                DropdownMenuItem(text = { Text("كل السنوات") }, onClick = { viewModel.onYearSelected(null); yearExpanded = false })
                                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                for (y in currentYear downTo currentYear - 5) {
                                    DropdownMenuItem(text = { Text(y.toString()) }, onClick = { viewModel.onYearSelected(y); yearExpanded = false })
                                }
                            }
                        }

                        // Payment Method Tabs
                        ScrollableTabRow(
                            selectedTabIndex = when(selectedPaymentMethod) {
                                "cash" -> 1
                                "e-wallet" -> 2
                                "visa" -> 3
                                else -> 0
                            },
                            modifier = Modifier.weight(2f),
                            containerColor = Color.Transparent,
                            contentColor = accentColor,
                            edgePadding = 0.dp,
                            divider = {},
                            indicator = { tabPositions ->
                                val index = when(selectedPaymentMethod) {
                                    "cash" -> 1
                                    "e-wallet" -> 2
                                    "visa" -> 3
                                    else -> 0
                                }
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                                    color = accentColor
                                )
                            }
                        ) {
                            listOf(null to "الكل", "cash" to "كاش", "e-wallet" to "محفظة", "visa" to "فيزا").forEach { (id, label) ->
                                Tab(
                                    selected = selectedPaymentMethod == id,
                                    onClick = { viewModel.onPaymentMethodSelected(id) }
                                ) {
                                    Text(label, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                                }
                            }
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
            onAdd = { type, desc, amount, ref, method ->
                viewModel.addManualTransaction(type, amount, desc, ref, method)
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
            onConfirm = {
                viewModel.onDateRangeSelected(
                    dateRangePickerState.selectedStartDateMillis,
                    dateRangePickerState.selectedEndDateMillis
                )
                showDateRangePicker = false
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
                    text = "رقم الشيك/السند/المرجع: ${transaction.referenceNumber}",
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
            }) { Text("موافق") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTransactionDialog(
    type: TransactionType,
    onDismiss: () -> Unit,
    onAdd: (TransactionType, String, Double, String, String) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("cash") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == TransactionType.INCOME) "إيداع مبلغ" else "سحب مبلغ / مصروف") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                Text("طريقة الدفع:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("cash" to "كاش", "e-wallet" to "محفظة", "visa" to "فيزا").forEach { (id, label) ->
                        FilterChip(
                            selected = selectedMethod == id,
                            onClick = { selectedMethod = id },
                            label = { Text(label) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (description.isNotEmpty() && amt > 0) onAdd(type, description, amt, referenceNumber, selectedMethod)
            }, colors = ButtonDefaults.buttonColors(
                containerColor = if (type == TransactionType.INCOME) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )) { Text("موافق") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
