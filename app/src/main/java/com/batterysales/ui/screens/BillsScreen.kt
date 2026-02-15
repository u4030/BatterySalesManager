package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.batterysales.data.models.BillType
import com.batterysales.viewmodel.BillViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillsScreen(
    navController: NavHostController,
    viewModel: BillViewModel = hiltViewModel()
) {
    val bills by viewModel.bills.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val pendingPurchases by viewModel.pendingPurchases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isLastPage by viewModel.isLastPage.collectAsState()
    val listState = rememberLazyListState()

    var showAddBillDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    val filteredBills = remember(bills) { bills }

    // Load more when reaching the end
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && !isLoadingMore && !isLastPage) {
            viewModel.loadBills()
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    var selectedBillForPayment by remember { mutableStateOf<Bill?>(null) }
    var billToDelete by remember { mutableStateOf<Bill?>(null) }
    var billToEdit by remember { mutableStateOf<Bill?>(null) }

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBillDialog = true },
                containerColor = accentColor,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة كمبيالة")
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
            // Gradient Header
            item {
                SharedHeader(
                    title = "الكمبيالات والشيكات",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Default.CalendarMonth,
                            onClick = { showDateRangePicker = true },
                            contentDescription = "Date Range"
                        )
                    }
                )
            }

            item {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else if (bills.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "لا توجد كمبيالات أو شيكات مسجلة",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            if (dateRangePickerState.selectedStartDateMillis != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        com.batterysales.ui.components.DateRangeInfo(
                            startDate = dateRangePickerState.selectedStartDateMillis,
                            endDate = dateRangePickerState.selectedEndDateMillis,
                            onClear = { dateRangePickerState.setSelection(null, null) }
                        )
                    }
                }
            }

            if (!isLoading && filteredBills.isNotEmpty()) {
                items(filteredBills, key = { it.id }) { bill ->
                    val supplier = suppliers.find { it.id == bill.supplierId }
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        BillItemCard(
                            bill = bill,
                            supplierName = supplier?.name ?: "",
                            onPayClick = { selectedBillForPayment = bill },
                            onDeleteClick = { billToDelete = bill },
                            onEditClick = { billToEdit = bill }
                        )
                    }
                }

                if (isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accentColor)
                        }
                    }
                }
            }
        }
    }

    if (billToDelete != null) {
        AlertDialog(
            onDismissRequest = { billToDelete = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف هذه الكمبيالة؟ سيتم أيضاً حذف جميع العمليات المالية المتعلقة بها من الخزينة.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBill(billToDelete!!.id)
                        billToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { billToDelete = null }) { Text("إلغاء") }
            }
        )
    }

    if (billToEdit != null) {
        EditBillDialog(
            bill = billToEdit!!,
            suppliers = suppliers,
            onDismiss = { billToEdit = null },
            onConfirm = { updatedBill ->
                viewModel.updateBill(updatedBill)
                billToEdit = null
            }
        )
    }

    if (selectedBillForPayment != null) {
        PaymentDialog(
            bill = selectedBillForPayment!!,
            onDismiss = { selectedBillForPayment = null },
            onConfirm = { amount ->
                viewModel.recordPayment(selectedBillForPayment!!.id, amount)
                selectedBillForPayment = null
            }
        )
    }

    if (showAddBillDialog) {
        AddBillDialog(
            suppliers = suppliers,
            pendingPurchases = pendingPurchases,
            onDismiss = { showAddBillDialog = false },
            onAdd = { desc, amount, date, type, ref, supplierId, relatedEntryId ->
                viewModel.addBill(desc, amount, date, type, ref, supplierId, relatedEntryId)
                showAddBillDialog = false
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillItemCard(bill: Bill, supplierName: String, onPayClick: () -> Unit, onDeleteClick: () -> Unit, onEditClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val isPaid = bill.status == BillStatus.PAID
    val isPartial = bill.status == BillStatus.PARTIAL

    val statusColor = when {
        isPaid -> Color(0xFF10B981)
        isPartial -> Color(0xFFFACC15)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bill.description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (supplierName.isNotEmpty()) {
                        Text(
                            text = "المورد: $supplierName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when {
                            isPaid -> "مسددة"
                            isPartial -> "جزئية"
                            else -> "مستحقة"
                        },
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(
                        text = "JD ${String.format("%,.3f", bill.amount)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (bill.paidAmount > 0) {
                        Text(
                            text = "المدفوع: JD ${String.format("%,.3f", bill.paidAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "المتبقي: JD ${String.format("%,.3f", bill.amount - bill.paidAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "تاريخ الاستحقاق: ${dateFormatter.format(bill.dueDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (bill.referenceNumber.isNotEmpty()) {
                        Text(
                            text = "رقم السند: ${bill.referenceNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFB8C00)
                        )
                    }
                }

                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (!isPaid) {
                        IconButton(
                            onClick = onPayClick,
                            modifier = Modifier.size(36.dp).background(Color(0xFF10B981).copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = "Pay", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp).background(Color(0xFF3B1F1F), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentDialog(
    bill: Bill,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amount by remember { mutableStateOf((bill.amount - bill.paidAmount).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسديد مبلغ") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("المبلغ المتبقي: JD ${String.format("%.3f", bill.amount - bill.paidAmount)}")
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "مبلغ الدفع"
                )
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0) onConfirm(amt)
            }) { Text("تسديد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillDialog(
    suppliers: List<com.batterysales.data.models.Supplier>,
    pendingPurchases: List<com.batterysales.data.models.StockEntry>,
    onDismiss: () -> Unit,
    onAdd: (String, Double, Date, BillType, String, String, String?) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var selectedSupplier by remember { mutableStateOf<com.batterysales.data.models.Supplier?>(null) }
    var selectedPurchase by remember { mutableStateOf<com.batterysales.data.models.StockEntry?>(null) }
    var amount by remember { mutableStateOf("") }
    var refNum by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(BillType.CHECK) }
    var showDatePicker by remember { mutableStateOf(false) }
    // إنشاء الحالة الخاصة بمنتقي التاريخ هنا
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    // استخدام متغير حالة لتتبع التاريخ المختار وعرضه
    var selectedDate by remember { mutableStateOf(Date(System.currentTimeMillis())) }

    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة كمبيالة/شيك جديد") },
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
                com.batterysales.ui.stockentry.Dropdown(
                    label = "المورد",
                    selectedValue = selectedSupplier?.name ?: "",
                    options = suppliers.map { it.name },
                    onOptionSelected = { index ->
                        selectedSupplier = suppliers[index]
                        selectedPurchase = null // Reset purchase if supplier changes
                    },
                    enabled = true
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "المبلغ"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = refNum,
                    onValueChange = { refNum = it },
                    label = "رقم السند / الشيك"
                )

                val supplierPurchases = pendingPurchases
                    .filter { it.supplierId == selectedSupplier?.id }
                    .sortedByDescending { it.timestamp }
                if (supplierPurchases.isNotEmpty()) {
                    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                    com.batterysales.ui.stockentry.Dropdown(
                        label = "ربط بطلبية شراء (اختياري)",
                        selectedValue = selectedPurchase?.let { "طلبية: ${dateFormatter.format(it.timestamp)} - JD ${it.totalCost}" } ?: "غير مرتبط",
                        options = listOf("غير مرتبط") + supplierPurchases.map { "طلبية: ${dateFormatter.format(it.timestamp)} - JD ${it.totalCost}" },
                        onOptionSelected = { index ->
                            selectedPurchase = if (index == 0) null else supplierPurchases[index - 1]
                            // Auto-fill amount if linked
                            selectedPurchase?.let { 
                                if (amount.isEmpty()) amount = it.totalCost.toString()
                                if (description.isEmpty()) description = "تسديد لطلبية شراء بتاريخ ${dateFormatter.format(it.timestamp)}"
                            }
                        },
                        enabled = true
                    )
                }

                Text("نوع الالتزام:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = {
                                Text(
                                    when (type) {
                                        BillType.CHECK -> "شيك"
                                        BillType.BILL -> "كمبيالة"
                                        BillType.TRANSFER -> "تحويل"
                                        BillType.OTHER -> "أخرى"
                                    }
                                )
                            }
                        )
                    }
                }

                Surface(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تاريخ الاستحقاق: ${dateFormatter.format(selectedDate)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFFFB8C00))
                    }
                }
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (description.isNotEmpty() && amt > 0) onAdd(description, amt, selectedDate, selectedType, refNum, selectedSupplier?.id ?: "", selectedPurchase?.id)
            }) { Text("إضافة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 1. تحديث التاريخ المختار من حالة منتقي التاريخ
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = Date(it)
                        }
                        // 2. إغلاق النافذة
                        showDatePicker = false
                    }
                ) {
                    Text("موافق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBillDialog(
    bill: Bill,
    suppliers: List<com.batterysales.data.models.Supplier>,
    onDismiss: () -> Unit,
    onConfirm: (Bill) -> Unit
) {
    var description by remember { mutableStateOf(bill.description) }
    var selectedSupplier by remember { mutableStateOf(suppliers.find { it.id == bill.supplierId }) }
    var amount by remember { mutableStateOf(bill.amount.toString()) }
    var refNum by remember { mutableStateOf(bill.referenceNumber) }
    var selectedType by remember { mutableStateOf(bill.billType) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = bill.dueDate.time
    )
    val selectedDate = datePickerState.selectedDateMillis?.let { Date(it) } ?: bill.dueDate
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل كمبيالة/شيك") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("تنبيه: تعديل الوصف سيقوم بتعديل وصف العمليات المتعلقة في الخزينة.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "الوصف"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "المبلغ الإجمالي"
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = refNum,
                    onValueChange = { refNum = it },
                    label = "رقم السند / الشيك"
                )

                com.batterysales.ui.stockentry.Dropdown(
                    label = "المورد",
                    selectedValue = selectedSupplier?.name ?: "",
                    options = suppliers.map { it.name },
                    onOptionSelected = { index -> selectedSupplier = suppliers[index] },
                    enabled = true
                )

                Text("نوع الالتزام:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = {
                                Text(
                                    when (type) {
                                        BillType.CHECK -> "شيك"
                                        BillType.BILL -> "كمبيالة"
                                        BillType.TRANSFER -> "تحويل"
                                        BillType.OTHER -> "أخرى"
                                    }
                                )
                            }
                        )
                    }
                }

                Surface(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تاريخ الاستحقاق: ${dateFormatter.format(selectedDate)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFFFB8C00))
                    }
                }
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: bill.amount
                onConfirm(bill.copy(description = description, amount = amt, dueDate = selectedDate, billType = selectedType, referenceNumber = refNum, supplierId = selectedSupplier?.id ?: ""))
            }) { Text("حفظ التعديلات") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("موافق")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
