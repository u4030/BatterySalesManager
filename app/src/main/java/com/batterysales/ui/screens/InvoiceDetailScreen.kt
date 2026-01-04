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
import com.batterysales.viewmodel.InvoiceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    navController: NavHostController,
    invoiceId: String,
    viewModel: InvoiceViewModel = hiltViewModel()
) {
    val selectedInvoice by viewModel.selectedInvoice.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmount by remember { mutableStateOf("") }

    LaunchedEffect(invoiceId) {
        viewModel.getInvoiceById(invoiceId)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(successMessage, errorMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
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
        if (isLoading && selectedInvoice == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (selectedInvoice == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لم يتم العثور على الفاتورة")
            }
        } else {
            val invoice = selectedInvoice!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF5F5F5))
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

                Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = Color.White) {
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
                            onClick = { /* طباعة */ },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("طباعة")
                        }
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("تسجيل دفعة جديدة") },
            text = {
                Column {
                    Text("المبلغ المتبقي: SR ${selectedInvoice?.remainingAmount}")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = { Text("المبلغ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = paymentAmount.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        viewModel.recordPayment(invoiceId, amount)
                        showPaymentDialog = false
                        paymentAmount = ""
                    }
                }) { Text("تأكيد") }
            },
            dismissButton = { TextButton(onClick = { showPaymentDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun InvoiceHeaderCard(invoice: Invoice) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "#${invoice.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                StatusBadge(status = invoice.status)
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            InfoRow(label = "العميل:", value = invoice.customerName)
            InfoRow(label = "التاريخ:", value = dateFormatter.format(invoice.invoiceDate))
            if (invoice.customerPhone.isNotEmpty()) InfoRow(label = "الجوال:", value = invoice.customerPhone)
        }
    }
}

@Composable
fun InvoiceItemRow(item: InvoiceItem) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.productName, fontWeight = FontWeight.Bold)
                Text(text = "${item.quantity} x SR ${item.price}", fontSize = 12.sp, color = Color.Gray)
            }
            Text(text = "SR ${String.format("%.2f", item.total)}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InvoiceSummaryCard(invoice: Invoice) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryRow(label = "المجموع الفرعي", value = invoice.subtotal)
            SummaryRow(label = "الضريبة", value = invoice.tax)
            SummaryRow(label = "الخصم", value = -invoice.discount, color = Color.Red)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "الإجمالي النهائي", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = "SR ${String.format("%.2f", invoice.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
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
        Text(text = label, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun SummaryRow(label: String, value: Double, color: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 14.sp)
        Text(text = "SR ${String.format("%.2f", value)}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
