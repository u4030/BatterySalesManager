package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.data.models.Invoice
import com.batterysales.viewmodel.InvoiceViewModel
import com.batterysales.ui.components.TabItem
import java.text.SimpleDateFormat
import java.util.*
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    navController: NavHostController,
    viewModel: InvoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditDialog by remember { mutableStateOf<Invoice?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    val listState = rememberLazyListState()

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    if (uiState.invoiceToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteDialog() },
            title = { Text("تأكيد الحذف") },
            text = { Text(uiState.deletionWarningMessage) },
            confirmButton = { Button(onClick = { viewModel.onConfirmDelete() }) { Text("حذف") } },
            dismissButton = { Button(onClick = { viewModel.onDismissDeleteDialog() }) { Text("إلغاء") } }
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

    if (showEditDialog != null) {
        EditCustomerDialog(
            invoice = showEditDialog!!,
            onDismiss = { showEditDialog = null },
            onConfirm = { invoice, newName, newPhone -> viewModel.updateCustomerInfo(invoice, newName, newPhone) }
        )
    }

    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.onDismissError() },
            title = { Text("خطأ") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { viewModel.onDismissError() }) { Text("موافق") }
            }
        )
    }

    // Load more when reaching the end
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !uiState.isLoading && !uiState.isLoadingMore && !uiState.isLastPage) {
            viewModel.loadInvoices()
        }
    }

    // Refresh data when screen is focused to update invoice status after payments
    LaunchedEffect(Unit) {
        viewModel.loadInvoices(reset = true)
    }

    Scaffold(
        containerColor = bgColor
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gradient Header
            item {
                SharedHeader(
                    title = "إدارة الفواتير",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Default.CalendarMonth,
                            onClick = { showDateRangePicker = true },
                            contentDescription = "Date Range"
                        )
                    }
                )

                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.isAdmin && uiState.warehouses.isNotEmpty()) {
                            val selectedIndex = uiState.warehouses.indexOfFirst { it.id == uiState.selectedWarehouseId }.coerceAtLeast(0)

                            ScrollableTabRow(
                                selectedTabIndex = selectedIndex,
                                containerColor = Color.Transparent,
                                contentColor = Color.White,
                                edgePadding = 16.dp,
                                divider = {},
                                indicator = { tabPositions ->
                                    if (tabPositions.isNotEmpty()) {
                                        Box(
                                            Modifier
                                                .tabIndicatorOffset(tabPositions[selectedIndex])
                                                .height(4.dp)
                                                .padding(horizontal = 16.dp)
                                                .background(Color.White, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                uiState.warehouses.forEach { warehouse ->
                                    Tab(
                                        selected = uiState.selectedWarehouseId == warehouse.id,
                                        onClick = { viewModel.onWarehouseSelected(warehouse.id) },
                                        text = {
                                            Text(
                                                warehouse.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (uiState.selectedWarehouseId == warehouse.id) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        selectedContentColor = Color.White,
                                        unselectedContentColor = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                    }

                    // Secondary filters as Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.selectedTab == 0,
                            onClick = { viewModel.onTabSelected(0) },
                            label = { Text("كافة الفواتير") }
                        )
                        FilterChip(
                            selected = uiState.selectedTab == 1,
                            onClick = { viewModel.onTabSelected(1) },
                            label = { Text("الذمم فقط") }
                        )
                    }

                    // Total Debt Card for Admin
                    if (uiState.isAdmin) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("إجمالي الذمم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Red)
                                Text("JD ${String.format("%,.3f", uiState.totalDebt)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.Red)
                            }
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("...بحث برقم الفاتورة أو اسم العميل", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    textStyle = com.batterysales.ui.theme.LocalInputTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cardBgColor,
                        unfocusedContainerColor = cardBgColor,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                )
            }

            if (uiState.startDate != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        com.batterysales.ui.components.DateRangeInfo(
                            startDate = uiState.startDate,
                            endDate = uiState.endDate,
                            onClear = { viewModel.onDateRangeSelected(null, null) }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else if (uiState.invoices.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("لا توجد فواتير حالياً", color = Color.Gray)
                        }
                    }
                }
            } else {
                items(uiState.invoices) { invoice ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        InvoiceItemCard(
                            invoice = invoice,
                            onClick = { navController.navigate("invoice_detail/${invoice.id}") },
                            onDeleteClick = { viewModel.deleteInvoice(invoice) },
                            onEditClick = { showEditDialog = invoice }
                        )
                    }
                }
            }

            if (uiState.isLoadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accentColor)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun EditCustomerDialog(invoice: Invoice, onDismiss: () -> Unit, onConfirm: (Invoice, String, String) -> Unit) {
    var customerName by remember { mutableStateOf(invoice.customerName) }
    var customerPhone by remember { mutableStateOf(invoice.customerPhone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل معلومات العميل") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = "اسم العميل"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = "رقم الهاتف"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(invoice, customerName, customerPhone); onDismiss() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun InvoiceItemCard(invoice: Invoice, onClick: () -> Unit, onDeleteClick: () -> Unit, onEditClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(status = invoice.status)
                    Text(
                    text = "${String.format("%,.3f", invoice.totalAmount)} JD",
                    style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFFFB8C00), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = invoice.invoiceNumber,
                    style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = invoice.customerName.ifEmpty { "عميل نقدي" },
                style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.End)
                )

                if (invoice.remainingAmount > 0) {
                    Text(
                    text = "المتبقي: ${String.format("%,.3f", invoice.remainingAmount)} JD",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF5350),
                        modifier = Modifier.align(Alignment.End)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "خيارات", tint = Color.Gray)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("تعديل العميل", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    onEditClick()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("حذف الفاتورة", color = Color.Red) },
                                onClick = {
                                    onDeleteClick()
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                    
                    Text(
                        text = dateFormatter.format(invoice.invoiceDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (text, color, icon) = when (status) {
        "paid" -> Triple("مدفوعة", Color(0xFF4CAF50), Icons.Default.CheckCircle)
        "pending" -> Triple("معلقة", Color(0xFFFF9800), Icons.Default.AccessTime)
        else -> Triple(status, Color(0xFF9E9E9E), Icons.Default.Info)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
