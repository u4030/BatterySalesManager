package com.batterysales.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.batterysales.data.models.Invoice
import com.batterysales.viewmodel.InvoiceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(navController: NavController, viewModel: InvoiceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Invoice?>(null) }
    var showEditDialog by remember { mutableStateOf<Invoice?>(null) }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد أنك تريد حذف هذه الفاتورة وجميع دفعاتها؟") },
            confirmButton = { Button(onClick = { viewModel.deleteInvoice(showDeleteDialog!!.id); showDeleteDialog = null }) { Text("حذف") } },
            dismissButton = { Button(onClick = { showDeleteDialog = null }) { Text("إلغاء") } }
        )
    }

    if (showEditDialog != null) {
        EditCustomerDialog(
            invoice = showEditDialog!!,
            onDismiss = { showEditDialog = null },
            onConfirm = { invoice, newName, newPhone -> viewModel.updateCustomerInfo(invoice, newName, newPhone) }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("الفواتير") }) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
                items(uiState.invoices) { invoice ->
                    InvoiceItemCard(
                        invoice = invoice,
                        onCardClick = {
                            if (invoice.status == "pending") {
                                navController.navigate("invoice_detail/${invoice.id}")
                            }
                        },
                        onEditClick = { showEditDialog = invoice },
                        onDeleteClick = { showDeleteDialog = invoice }
                    )
                }
            }
        }
    }
}

@Composable
fun InvoiceItemCard(
    invoice: Invoice,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val statusColor = if (invoice.status == "paid") Color(0xFF0A842D) else Color(0xFFD32F2F)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(onClick = onCardClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(invoice.customerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "خيارات") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("تعديل العميل") }, onClick = onEditClick)
                        DropdownMenuItem(text = { Text("حذف الفاتورة") }, onClick = onDeleteClick)
                    }
                }
            }
            Text("الهاتف: ${invoice.customerPhone}", style = MaterialTheme.typography.bodyMedium)
            Text("التاريخ: ${invoice.createdAt.toFormattedString("yyyy-MM-dd")}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
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
fun EditCustomerDialog(invoice: Invoice, onDismiss: () -> Unit, onConfirm: (Invoice, String, String) -> Unit) {
    var customerName by remember { mutableStateOf(invoice.customerName) }
    var customerPhone by remember { mutableStateOf(invoice.customerPhone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل معلومات العميل") },
        text = {
            Column {
                OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("اسم العميل") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("رقم الهاتف") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(invoice, customerName, customerPhone); onDismiss() }) { Text("حفظ") } },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun Date.toFormattedString(format: String): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(this)
}
