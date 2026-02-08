package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.batterysales.data.models.Invoice
import com.batterysales.viewmodel.InvoiceViewModel
import java.text.SimpleDateFormat
import java.util.*

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
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDateRangeSelected(
                        dateRangePickerState.selectedStartDateMillis,
                        dateRangePickerState.selectedEndDateMillis
                    )
                    showDateRangePicker = false
                }) {
                    Text("موافق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f).padding(16.dp)
            )
        }
    }

    if (showEditDialog != null) {
        EditCustomerDialog(
            invoice = showEditDialog!!,
            onDismiss = { showEditDialog = null },
            onConfirm = { invoice, newName, newPhone -> viewModel.updateCustomerInfo(invoice, newName, newPhone) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة الفواتير", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "تحديد الفترة", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { viewModel.loadInvoices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("sales") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة فاتورة")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(selected = uiState.selectedTab == 0, onClick = { viewModel.onTabSelected(0) }) {
                    Text("الكل", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = uiState.selectedTab == 1, onClick = { viewModel.onTabSelected(1) }) {
                    Text("المعلقة", modifier = Modifier.padding(16.dp))
                }
            }

            // شريط البحث
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("بحث برقم الفاتورة أو اسم العميل...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = com.batterysales.ui.theme.LocalInputTextStyle.current,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (uiState.startDate != null) {
                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الفترة: ${sdf.format(Date(uiState.startDate!!))} - ${sdf.format(Date(uiState.endDate ?: uiState.startDate!!))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { viewModel.onDateRangeSelected(null, null) }) {
                        Text("إلغاء الفلترة")
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.invoices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("لا توجد فواتير حالياً", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    items(uiState.invoices) { invoice ->
                        InvoiceItemCard(
                            invoice = invoice,
                            onClick = { navController.navigate("invoice_detail/${invoice.id}") },
                            onDeleteClick = { viewModel.deleteInvoice(invoice) },
                            onEditClick = { showEditDialog = invoice }
                        )
                    }
                }
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
            Column {
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = "اسم العميل"
                )
                Spacer(modifier = Modifier.height(8.dp))
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = "رقم الهاتف"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(invoice, customerName, customerPhone); onDismiss() }) { Text("حفظ") } },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun InvoiceItemCard(invoice: Invoice, onClick: () -> Unit, onDeleteClick: () -> Unit, onEditClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${invoice.invoiceNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(status = invoice.status)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = invoice.customerName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = dateFormatter.format(invoice.invoiceDate),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "JD ${String.format("%.3f", invoice.totalAmount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (invoice.remainingAmount > 0) {
                    Text(
                        text = "المتبقي: JD ${String.format("%.3f", invoice.remainingAmount)}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "خيارات", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("تعديل العميل") },
                        onClick = {
                            onEditClick()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف الفاتورة") },
                        onClick = {
                            onDeleteClick()
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (text, color) = when (status) {
        "paid" -> "مدفوعة" to Color(0xFF4CAF50)
        "pending" -> "معلقة" to Color(0xFFFF9800)
        else -> status to Color(0xFF9E9E9E)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold
        )
    }
}
