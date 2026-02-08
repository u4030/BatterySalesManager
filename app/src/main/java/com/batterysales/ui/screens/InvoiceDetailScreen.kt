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
import com.batterysales.data.models.Invoice
import com.batterysales.data.models.InvoiceItem
import com.batterysales.data.models.Payment
import com.batterysales.viewmodel.InvoiceDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    navController: NavHostController,
    viewModel: InvoiceDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showPaymentHistoryDialog by remember { mutableStateOf(false) }
    var paymentAmount by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    uiState.errorMessage?.let {
        LaunchedEffect(it) {
            snackbarHostState.showSnackbar(it)
            viewModel.onDismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل الفاتورة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.invoice == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لم يتم العثور على الفاتورة")
            }
        } else {
            val invoice = uiState.invoice!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { InvoiceHeaderCard(invoice) }
                    item { Text("الأصناف", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    items(invoice.items) { item -> InvoiceItemRow(item) }
                    item { InvoiceSummaryCard(invoice) }
                }

                Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (invoice.remainingAmount > 0) {
                            Button(
                                onClick = { showPaymentDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تسجيل دفعة")
                            }
                        }
                        OutlinedButton(
                            onClick = { showPaymentHistoryDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("سجل الدفعات")
                        }
                    }
                }
            }
        }
    }

    if (showPaymentDialog && uiState.invoice != null) {
        val invoice = uiState.invoice!!
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("تسجيل دفعة جديدة") },
            text = {
                Column {
                    Text("المبلغ المتبقي: JD ${String.format("%.3f", invoice.remainingAmount)}")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = { Text("المبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = paymentAmount.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        viewModel.addPayment(amount)
                        showPaymentDialog = false
                        paymentAmount = ""
                    }
                }) { Text("تأكيد") }
            },
            dismissButton = { TextButton(onClick = { showPaymentDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showPaymentHistoryDialog) {
        PaymentHistoryDialog(
            payments = uiState.payments,
            onDismiss = { showPaymentHistoryDialog = false },
            onEdit = { payment, newAmount -> viewModel.updatePayment(payment, newAmount) },
            onDelete = { paymentId -> viewModel.deletePayment(paymentId) }
        )
    }
}

@Composable
fun InvoiceHeaderCard(invoice: Invoice) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "#${invoice.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                StatusBadge(status = invoice.status)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            InfoRow(label = "العميل:", value = invoice.customerName)
            InfoRow(label = "التاريخ:", value = dateFormatter.format(invoice.invoiceDate))
            if (invoice.customerPhone.isNotEmpty()) InfoRow(label = "الجوال:", value = invoice.customerPhone)
        }
    }
}

@Composable
fun InvoiceItemRow(item: InvoiceItem) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.productName, fontWeight = FontWeight.Bold)
                Text(text = "${item.quantity} x JD ${String.format("%.3f", item.price)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = "JD ${String.format("%.3f", item.total)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun InvoiceSummaryCard(invoice: Invoice) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryRow(label = "المجموع الفرعي", value = invoice.subtotal)
            SummaryRow(label = "الضريبة", value = invoice.tax)
            SummaryRow(label = "الخصم", value = -invoice.discount, color = Color.Red)
            if (invoice.oldBatteriesQuantity > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "البطاريات القديمة (${invoice.oldBatteriesQuantity} حبة، ${invoice.oldBatteriesTotalAmperes} أمبير)", fontSize = 14.sp, color = Color(0xFF5D4037))
                    Text(text = "- JD ${String.format("%.3f", invoice.oldBatteriesValue)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF5D4037))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "الإجمالي النهائي", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = "JD ${String.format("%.3f", invoice.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(label = "المبلغ المدفوع", value = invoice.paidAmount, color = Color(0xFF4CAF50))
            SummaryRow(label = "المبلغ المتبقي", value = invoice.remainingAmount, color = Color.Red)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SummaryRow(label: String, value: Double, color: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 14.sp)
        Text(text = "JD ${String.format("%.3f", value)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
fun PaymentHistoryDialog(
    payments: List<Payment>,
    onDismiss: () -> Unit,
    onEdit: (Payment, Double) -> Unit,
    onDelete: (String) -> Unit
) {
    var paymentToEdit by remember { mutableStateOf<Payment?>(null) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل الدفعات") },
        text = {
            LazyColumn {
                items(payments) { payment ->
                    ListItem(
                        headlineContent = { Text("المبلغ: JD ${String.format("%.3f", payment.amount)}", fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Text(
                                text = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(payment.timestamp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { paymentToEdit = payment }) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل")
                                }
                                IconButton(onClick = { paymentToDelete = payment }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف")
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )

    paymentToEdit?.let { payment ->
        var newAmount by remember { mutableStateOf(payment.amount.toString()) }
        AlertDialog(
            onDismissRequest = { paymentToEdit = null },
            title = { Text("تعديل الدفعة") },
            text = {
                Column {
                    Text("سيتم أيضاً تعديل هذه العملية في الخزينة.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { newAmount = it },
                        label = { Text("المبلغ الجديد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onEdit(payment, newAmount.toDoubleOrNull() ?: 0.0)
                    paymentToEdit = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { paymentToEdit = null }) { Text("إلغاء") }
            }
        )
    }

    paymentToDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد أنك تريد حذف هذه الدفعة؟ سيتم أيضاً حذف هذه العملية من الخزينة.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(payment.id)
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) { Text("إلغاء") }
            }
        )
    }
}
