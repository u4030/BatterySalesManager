package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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

    val bgColor = Color(0xFF0F0F0F)
    val cardBgColor = Color(0xFF1C1C1C)
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
        containerColor = bgColor,
        bottomBar = {
            NavigationBar(
                containerColor = cardBgColor,
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("dashboard") { popUpTo("dashboard") { inclusive = true } } },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("الرئيسية") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("product_management") },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("المنتجات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("sales") },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("المبيعات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("الإعدادات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Gradient Header
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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Text(
                            text = "إدارة الفواتير",
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
                                onClick = { viewModel.loadInvoices() },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Styled Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(4.dp)
                    ) {
                        TabItem(
                            title = "الكل",
                            isSelected = uiState.selectedTab == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onTabSelected(0) }
                        )
                        TabItem(
                            title = "المعلقة",
                            isSelected = uiState.selectedTab == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onTabSelected(1) }
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("...بحث برقم الفاتورة أو اسم العميل", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                textStyle = com.batterysales.ui.theme.LocalInputTextStyle.current.copy(color = Color.White),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = cardBgColor,
                    focusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.2f)
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
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    TextButton(onClick = { viewModel.onDateRangeSelected(null, null) }) {
                        Text("إلغاء الفلترة", color = accentColor)
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else if (uiState.invoices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("لا توجد فواتير حالياً", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
fun TabItem(title: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                if (isSelected) Color.White else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color(0xFFD84315) else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
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
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
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
                        text = "${String.format("%,.3f", invoice.totalAmount)} ر.س",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFFFB8C00), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = invoice.invoiceNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = invoice.customerName.ifEmpty { "عميل نقدي" },
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.End)
                )

                if (invoice.remainingAmount > 0) {
                    Text(
                        text = "المتبقي: ${String.format("%,.3f", invoice.remainingAmount)} ر.س",
                        fontSize = 14.sp,
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
                            modifier = Modifier.background(Color(0xFF1C1C1C))
                        ) {
                            DropdownMenuItem(
                                text = { Text("تعديل العميل", color = Color.White) },
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
                        fontSize = 14.sp,
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
