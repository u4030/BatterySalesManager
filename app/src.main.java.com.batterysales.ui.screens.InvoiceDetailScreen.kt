package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.data.models.Payment
import com.batterysales.viewmodel.InvoiceDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(navController: NavController, viewModel: InvoiceDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var paymentToEdit by remember { mutableStateOf<Payment?>(null) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }

    if (showAddPaymentDialog) {
        PaymentDialog(
            onDismiss = { showAddPaymentDialog = false },
            onConfirm = { amount -> viewModel.addPayment(amount) }
        )
    }

    paymentToEdit?.let { payment ->
        PaymentDialog(
            payment = payment,
            onDismiss = { paymentToEdit = null },
            onConfirm = { amount -> viewModel.updatePayment(payment, amount) }
        )
    }

    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد أنك تريد حذف هذه الدفعة؟") },
            confirmButton = { Button(onClick = { viewModel.deletePayment(paymentToDelete!!.id); paymentToDelete = null }) { Text("حذف") } },
            dismissButton = { Button(onClick = { paymentToDelete = null }) { Text("إلغاء") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل الفاتورة") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.errorMessage != null -> {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.invoice != null -> {
                    val invoice = uiState.invoice!!
                    Column(modifier = Modifier.padding(16.dp)) {
                        InvoiceHeader(invoice = invoice)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (invoice.status == "pending") {
                            Button(onClick = { showAddPaymentDialog = true }) { Text("إضافة دفعة جديدة") }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("سجل الدفعات", style = MaterialTheme.typography.titleLarge)
                        Divider()
                        LazyColumn {
                            items(uiState.payments) { payment ->
                                PaymentItem(
                                    payment = payment,
                                    isPending = invoice.status == "pending",
                                    onEdit = { paymentToEdit = payment },
                                    onDelete = { paymentToDelete = payment }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceHeader(invoice: com.batterysales.data.models.Invoice) {
    val statusColor = if (invoice.status == "paid") Color(0xFF0A842D) else Color(0xFFD32F2F)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(invoice.customerName, style = MaterialTheme.typography.headlineSmall)
            Text("الهاتف: ${invoice.customerPhone}", style = MaterialTheme.typography.bodyMedium)
            Text("التاريخ: ${invoice.createdAt.toFormattedString("yyyy-MM-dd")}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                InfoColumn(label = "الإجمالي", value = String.format("%.2f", invoice.totalAmount))
                InfoColumn(label = "المدفوع", value = String.format("%.2f", invoice.paidAmount))
                InfoColumn(label = "المتبقي", value = String.format("%.2f", invoice.remainingAmount))
            }
             Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                 Text(text = invoice.status.replaceFirstChar { it.uppercase() }, color = statusColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PaymentItem(payment: Payment, isPending: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("المبلغ: ${String.format("%.2f", payment.amount)}", fontWeight = FontWeight.Bold) },
        supportingContent = { Text("التاريخ: ${payment.timestamp.toFormattedString("yyyy-MM-dd HH:mm")}") },
        trailingContent = {
            if (isPending) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "خيارات") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("تعديل") }, onClick = { onEdit(); menuExpanded = false })
                        DropdownMenuItem(text = { Text("حذف") }, onClick = { onDelete(); menuExpanded = false })
                    }
                }
            }
        }
    )
}

@Composable
fun PaymentDialog(payment: Payment? = null, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember { mutableStateOf(payment?.amount?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (payment == null) "إضافة دفعة جديدة" else "تعديل الدفعة") },
        text = { OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("المبلغ") }) },
        confirmButton = { Button(onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0); onDismiss() }) { Text("حفظ") } },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun Date.toFormattedString(format: String): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(this)
}
