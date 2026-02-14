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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(
    navController: NavHostController,
    viewModel: BankViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isLastPage by viewModel.isLastPage.collectAsState()
    val listState = rememberLazyListState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(com.batterysales.data.models.BankTransactionType.DEPOSIT) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    var searchQuery by remember { mutableStateOf("") }

    val filteredTransactions = remember(transactions, selectedTab, searchQuery) {
        transactions.filter { transaction ->
            val matchesTab = when (selectedTab) {
                0 -> true // All
                1 -> transaction.type == com.batterysales.data.models.BankTransactionType.WITHDRAWAL // Withdrawals
                else -> true
            }
            val matchesSearch = transaction.description.contains(searchQuery, ignoreCase = true) || 
                               transaction.referenceNumber.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
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

    Scaffold(
        containerColor = bgColor,
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
//                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
//                    text = { Text("إيداع شيك") }
                ){
                    Icon(Icons.Default.Add, contentDescription = "إيداع")}
                ExtendedFloatingActionButton(
                    onClick = {
                        selectedType = com.batterysales.data.models.BankTransactionType.WITHDRAWAL
                        showAddDialog = true
                    },
                    containerColor = Color(0xFFF44336),
                    contentColor = Color.White,
//                    icon = { Icon(Icons.Default.Remove, contentDescription = null) },
//                    text = { Text("سحب شيك") }
                ){
                    Icon(Icons.Default.Remove, contentDescription = "سحب")
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
                                    text = "البنك والشيكات",
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
                                Text(
                                    "إجمالي رصيد الشيكات في البنك",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
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
                            Text("المسحوبات", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = if(selectedTab == 1) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
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
                        BankTransactionItemCard(transaction)
                    }
                }
            }
        }
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

    if (showAddDialog) {
        AddBankTransactionDialog(
            type = selectedType,
            onDismiss = { showAddDialog = false },
            onAdd = { type, desc, amount, ref ->
                viewModel.addManualTransaction(type, amount, desc, ref)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddBankTransactionDialog(
    type: com.batterysales.data.models.BankTransactionType,
    onDismiss: () -> Unit,
    onAdd: (com.batterysales.data.models.BankTransactionType, String, Double, String) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }

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
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = "رقم الشيك/السند (اختياري)"
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
                containerColor = if (type == com.batterysales.data.models.BankTransactionType.DEPOSIT) Color(0xFF4CAF50) else Color(0xFFF44336)
            )) { Text("موافق") }
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
    val amountColor = if (isDeposit) Color(0xFF10B981) else Color(0xFFEF4444)

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
                            imageVector = if (isDeposit) Icons.Default.AccountBalanceWallet else Icons.Default.Payments,
                            contentDescription = null,
                            tint = amountColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isDeposit) "إيداع" else "سحب",
                            color = amountColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${if (isDeposit) "+" else "-"} JD ${String.format("%,.3f", transaction.amount)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDeposit) Color(0xFF10B981) else Color(0xFFEF4444)
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
                dateFormatter.format(transaction.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
