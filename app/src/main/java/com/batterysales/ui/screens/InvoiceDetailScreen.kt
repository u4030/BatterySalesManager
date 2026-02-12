package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape

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

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    Scaffold(
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.invoice == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لم يتم العثور على الفاتورة", color = MaterialTheme.colorScheme.onBackground)
            }
        } else {
            val invoice = uiState.invoice!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Modern Header with Gradient
                SharedHeader(
                    title = "تفاصيل الفاتورة",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Default.Print,
                            onClick = { /* Print logic */ },
                            contentDescription = "Print"
                        )
                    }
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    // Quick Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "رقم الفاتورة",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "#${invoice.invoiceNumber}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }

                        StatusBadge(status = invoice.status)
                    }
                }

                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Customer Info Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRowItem("اسم العميل", invoice.customerName, Icons.Default.Person)
                            InfoRowItem("تاريخ الفاتورة", dateFormatter.format(invoice.invoiceDate), Icons.Default.CalendarToday)
                            if (invoice.customerPhone.isNotEmpty()) InfoRowItem("الجوال", invoice.customerPhone, Icons.Default.Phone)
                        }
                    }

                    Text("الأصناف المباعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    
                    invoice.items.forEach { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = accentColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${item.quantity}",
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("JD ${String.format("%.3f", item.price)} للقطعة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                Text(
                                    "JD ${String.format("%.3f", item.total)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (invoice.oldBatteriesQuantity > 0) {
                        Text("البطاريات القديمة (سكراب)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الكمية والأمبيرات", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    Text("${invoice.oldBatteriesQuantity} حبة | ${invoice.oldBatteriesTotalAmperes}A", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("القيمة المخصومة", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                    Text("JD ${String.format("%.3f", invoice.oldBatteriesValue)}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Financial Summary
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryRowItem("المجموع الفرعي", invoice.subtotal)
                            SummaryRowItem("الضريبة", invoice.tax)
                            SummaryRowItem("الخصم", -invoice.discount, color = Color(0xFFEF4444))
                            
                            if (invoice.oldBatteriesValue > 0) {
                                SummaryRowItem("خصم السكراب", -invoice.oldBatteriesValue, color = Color(0xFF10B981))
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الإجمالي النهائي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("JD ${String.format("%.3f", invoice.totalAmount)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            
                            SummaryRowItem("المبلغ المدفوع", invoice.paidAmount, color = Color(0xFF10B981))
                            SummaryRowItem(
                                "المبلغ المتبقي", 
                                invoice.remainingAmount, 
                                color = if (invoice.remainingAmount > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                            )
                        }
                    }

                    // Action Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (invoice.remainingAmount > 0) {
                            Button(
                                onClick = { showPaymentDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تسجيل دفعة")
                            }
                        }
                        Button(
                            onClick = { showPaymentHistoryDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("سجل الدفعات", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
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
                }) { Text("موافق") }
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
private fun InfoRowItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            shape = CircleShape,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SummaryRowItem(label: String, value: Double, color: Color? = null) {
    val textColor = color ?: MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(text = "JD ${String.format("%.3f", value)}", style = MaterialTheme.typography.bodyMedium, color = textColor, fontWeight = FontWeight.Bold)
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
