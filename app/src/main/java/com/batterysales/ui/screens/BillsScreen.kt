package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(bills) { bill ->
                        BillItemCard(
                            bill = bill,
                            onPayClick = { selectedBillForPayment = bill },
                            onDeleteClick = { viewModel.deleteBill(bill.id) }
                        )
                    }
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
            onDismiss = { showAddBillDialog = false },
            onAdd = { desc, amount, date, type ->
                viewModel.addBill(desc, amount, date, type)
                showAddBillDialog = false
            }
        )
    }
}

@Composable
fun BillItemCard(bill: Bill, onPayClick: () -> Unit, onDeleteClick: () -> Unit) {
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
                    Text("المبلغ الإجمالي: SR ${String.format("%.2f", bill.amount)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    if (bill.paidAmount > 0) {
                        Text("المدفوع: SR ${String.format("%.2f", bill.paidAmount)}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                        Text("المتبقي: SR ${String.format("%.2f", bill.amount - bill.paidAmount)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    } else {
                        Text("المبلغ: SR ${String.format("%.2f", bill.amount)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                    Text("تاريخ الاستحقاق: ${dateFormatter.format(bill.dueDate)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row {
                    if (!isPaid) {
                        IconButton(onClick = onPayClick) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "تسديد", tint = Color(0xFF4CAF50))
                        }
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
fun PaymentDialog(bill: Bill, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember { mutableStateOf((bill.amount - bill.paidAmount).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسديد مبلغ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المبلغ المتبقي: SR ${String.format("%.2f", bill.amount - bill.paidAmount)}")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("مبلغ الدفع") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
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
fun AddBillDialog(onDismiss: () -> Unit, onAdd: (String, Double, Date, BillType) -> Unit) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
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

                Text("نوع الالتزام:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillType.values().forEach { type ->
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
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (description.isNotEmpty() && amt > 0) onAdd(description, amt, selectedDate, selectedType)
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
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
