package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.batterysales.data.models.BillType
import com.batterysales.viewmodel.BillViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    navController: NavHostController,
    viewModel: BillViewModel = hiltViewModel()
) {
    val bills by viewModel.bills.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val pendingPurchases by viewModel.pendingPurchases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddBillDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الكمبيالات والشيكات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
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
            FloatingActionButton(
                onClick = { showAddBillDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة كمبيالة")
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (bills.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "لا توجد كمبيالات أو شيكات مسجلة",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                var selectedBillForPayment by remember { mutableStateOf<Bill?>(null) }
                var billToDelete by remember { mutableStateOf<Bill?>(null) }
                var billToEdit by remember { mutableStateOf<Bill?>(null) }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(bills) { bill ->
                        val supplier = suppliers.find { it.id == bill.supplierId }
                        BillItemCard(
                            bill = bill,
                            supplierName = supplier?.name ?: "",
                            onPayClick = { selectedBillForPayment = bill },
                            onDeleteClick = { billToDelete = bill },
                            onEditClick = { billToEdit = bill }
                        )
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
            }
        }
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
}

@Composable
fun BillItemCard(bill: Bill, supplierName: String, onPayClick: () -> Unit, onDeleteClick: () -> Unit, onEditClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val isPaid = bill.status == BillStatus.PAID
    val isPartial = bill.status == BillStatus.PARTIAL

    val statusColor = when {
        isPaid -> Color(0xFF4CAF50)
        isPartial -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.tertiary
    }
    val statusContainerColor = statusColor.copy(alpha = 0.1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(bill.description, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Surface(
                    color = statusContainerColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = when {
                            isPaid -> "مسددة"
                            isPartial -> "مسددة جزئياً"
                            else -> "غير مسددة"
                        },
                        color = statusColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (supplierName.isNotEmpty()) {
                        Text("المورد: $supplierName", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                    }
                    if (bill.referenceNumber.isNotEmpty()) {
                        Text("رقم السند: ${bill.referenceNumber}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                    Text("المبلغ الإجمالي: JD ${String.format("%.3f", bill.amount)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    if (bill.paidAmount > 0) {
                        Text("المدفوع: JD ${String.format("%.3f", bill.paidAmount)}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                        Text("المتبقي: JD ${String.format("%.3f", bill.amount - bill.paidAmount)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    } else {
                        Text("المبلغ: JD ${String.format("%.3f", bill.amount)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                    Text("تاريخ الاستحقاق: ${dateFormatter.format(bill.dueDate)}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row {
                    if (!isPaid) {
                        IconButton(onClick = onPayClick) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "تسديد", tint = Color(0xFF4CAF50))
                        }
                    }
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    val selectedDate = datePickerState.selectedDateMillis?.let { Date(it) } ?: Date()
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة كمبيالة/شيك جديد") },
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
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = refNum,
                    onValueChange = { refNum = it },
                    label = "رقم السند / الشيك"
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

                val supplierPurchases = pendingPurchases.filter { it.supplierId == selectedSupplier?.id }
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

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تاريخ الاستحقاق: ${dateFormatter.format(selectedDate)}")
                        Icon(Icons.Default.DateRange, contentDescription = null)
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
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                DatePicker(state = datePickerState, modifier = Modifier.fillMaxSize())
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تنبيه: تعديل الوصف سيقوم بتعديل وصف العمليات المتعلقة في الخزينة.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تاريخ الاستحقاق: ${dateFormatter.format(selectedDate)}")
                        Icon(Icons.Default.DateRange, contentDescription = null)
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
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                DatePicker(state = datePickerState, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
