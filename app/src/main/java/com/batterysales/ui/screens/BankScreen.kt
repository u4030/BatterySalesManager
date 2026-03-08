package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.paging.compose.collectAsLazyPagingItems
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.batterysales.data.models.BankTransaction
import com.batterysales.data.models.BankTransactionType
import com.batterysales.viewmodel.BankViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton
import com.batterysales.ui.components.CustomKeyboardTextField
import com.batterysales.ui.components.AppDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(
    navController: NavHostController,
    viewModel: BankViewModel = hiltViewModel()
) {
    val pagingItems = viewModel.transactions.collectAsLazyPagingItems()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val warehouses by viewModel.warehouses.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val totalWithdrawals by viewModel.totalWithdrawals.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isLastPage by viewModel.isLastPage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val listState = rememberLazyListState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(com.batterysales.data.models.BankTransactionType.DEPOSIT) }
    var transactionToEdit by remember { mutableStateOf<BankTransaction?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<BankTransaction?>(null) }

    val selectedTab by viewModel.selectedTab.collectAsState()
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    val searchQuery by viewModel.searchQuery.collectAsState()


    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("خطأ") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { viewModel.clearError() }) { Text("موافق") }
            }
        )
    }

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
                                        Text("إجمالي المسحوبات", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                        Text("JD ${String.format("%.3f", totalWithdrawals)}", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("حالة الرصيد", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                        Text(
                                            if (balance >= 0) "إيجابي" else "مدين",
                                            color = if (balance >= 0) Color(0xFF10B948) else Color(0xFFEF4444),
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
                        Tab(selected = selectedTab == 0, onClick = { viewModel.onTabSelected(0) }) {
                            Text("الكل", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = if(selectedTab == 0) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Tab(selected = selectedTab == 1, onClick = { viewModel.onTabSelected(1) }) {
                            Text("المسحوبات", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = if(selectedTab == 1) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search Bar
                    CustomKeyboardTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = "بحث بالوصف أو الرقم المرجعي..."
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

            val loadState = pagingItems.loadState
            if (loadState.refresh is androidx.paging.LoadState.Loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else if (loadState.refresh is androidx.paging.LoadState.Error) {
                val error = (loadState.refresh as androidx.paging.LoadState.Error).error
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("حدث خطأ أثناء تحميل السجل: ${error.localizedMessage}", color = Color.Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else if (pagingItems.itemCount == 0) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "لا توجد عمليات حالياً",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(
                    count = pagingItems.itemCount,
                    key = { index ->
                        val item = pagingItems[index]
                        "${selectedTab}_${item?.id ?: index}"
                    }
                ) { index ->
                    val transaction = pagingItems[index]
                    transaction?.let {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            BankTransactionItemCard(
                                transaction = it,
                                onEdit = { transactionToEdit = it },
                                onDelete = { showDeleteConfirm = it }
                            )
                        }
                    }
                }
            }

            if (pagingItems.loadState.append is androidx.paging.LoadState.Loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accentColor)
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

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("حذف العملية") },
            text = { Text("هل أنت متأكد من حذف هذه العملية البنكية؟ إذا كانت مرتبطة بالخزينة سيتم حذف القيد المقابل أيضاً.") },
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

    if (transactionToEdit != null) {
        EditBankTransactionDialog(
            transaction = transactionToEdit!!,
            onDismiss = { transactionToEdit = null },
            onConfirm = { updated ->
                viewModel.updateTransaction(updated)
                transactionToEdit = null
            }
        )
    }

    if (showAddDialog) {
        AddBankTransactionDialog(
            type = selectedType,
            warehouses = warehouses,
            onDismiss = { showAddDialog = false },
            onAdd = { type, desc, amount, ref, supplier, fromTreasury, warehouseId ->
                viewModel.addManualTransaction(type, amount, desc, ref, supplier, fromTreasury, warehouseId)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddBankTransactionDialog(
    type: com.batterysales.data.models.BankTransactionType,
    warehouses: List<com.batterysales.data.models.Warehouse>,
    onDismiss: () -> Unit,
    onAdd: (com.batterysales.data.models.BankTransactionType, String, Double, String, String, Boolean, String?) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }
    var supplierName by remember { mutableStateOf("") }
    var fromTreasury by remember { mutableStateOf(false) }
    var selectedWarehouseId by remember { mutableStateOf<String?>(null) }

    AppDialog(
        onDismiss = onDismiss,
        title = if (type == com.batterysales.data.models.BankTransactionType.DEPOSIT) "إيداع في البنك" else "سحب من البنك",
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (description.isNotEmpty() && amt > 0) onAdd(type, description, amt, referenceNumber, supplierName, fromTreasury, selectedWarehouseId)
            }, colors = ButtonDefaults.buttonColors(
                containerColor = if (type == com.batterysales.data.models.BankTransactionType.DEPOSIT) Color(0xFF4CAF50) else Color(0xFFF44336)
            )) { Text("موافق") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    ) {
        if (type == com.batterysales.data.models.BankTransactionType.DEPOSIT) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("إيداع من الخزينة؟", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = fromTreasury,
                    onCheckedChange = { fromTreasury = it }
                )
            }
            if (fromTreasury) {
                Text("سيتم خصم المبلغ تلقائياً من رصيد الخزينة", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFB8C00))
                Spacer(modifier = Modifier.height(8.dp))

                if (warehouses.isNotEmpty()) {
                    com.batterysales.ui.stockentry.Dropdown(
                        label = "المستودع (الخزينة) المصدر",
                        selectedValue = warehouses.find { it.id == selectedWarehouseId }?.name ?: "اختر المستودع",
                        options = warehouses.map { it.name },
                        onOptionSelected = { index -> selectedWarehouseId = warehouses[index].id },
                        enabled = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        com.batterysales.ui.components.CustomKeyboardTextField(
            value = description,
            onValueChange = { description = it },
            label = "الوصف"
        )
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = supplierName,
            onValueChange = { supplierName = it },
            label = "اسم المورد (اختياري)"
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
    }
}

@Composable
fun EditBankTransactionDialog(
    transaction: BankTransaction,
    onDismiss: () -> Unit,
    onConfirm: (BankTransaction) -> Unit
) {
    var description by remember { mutableStateOf(transaction.description) }
    var amount by remember { mutableStateOf(transaction.amount.toString()) }
    var referenceNumber by remember { mutableStateOf(transaction.referenceNumber) }
    var supplierName by remember { mutableStateOf(transaction.supplierName) }

    AppDialog(
        onDismiss = onDismiss,
        title = "تعديل عملية بنكية",
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: transaction.amount
                onConfirm(transaction.copy(description = description, amount = amt, referenceNumber = referenceNumber, supplierName = supplierName))
            }) { Text("موافق") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    ) {
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = description,
            onValueChange = { description = it },
            label = "الوصف"
        )
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = supplierName,
            onValueChange = { supplierName = it },
            label = "المورد"
        )
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = referenceNumber,
            onValueChange = { referenceNumber = it },
            label = "رقم الشيك/المرجع"
        )
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "المبلغ"
        )
    }
}

@Composable
fun BankTransactionItemCard(
    transaction: BankTransaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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

                if (transaction.billId == null) {
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

            if (transaction.supplierName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "المورد: ${transaction.supplierName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF3B82F6),
                    fontWeight = FontWeight.Medium
                )
            }

            if (transaction.referenceNumber.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "رقم الشيك: ${transaction.referenceNumber}",
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
