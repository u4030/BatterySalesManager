package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.components.HeaderIconButton
import com.batterysales.ui.components.InfoBadge
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.TabItem
import com.batterysales.viewmodel.ReportsViewModel
import com.batterysales.ui.components.AppDateRangePickerDialog

@Composable
fun SupplierRefundDialog(
    initialAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.3f", initialAmount)) }
    var destination by remember { mutableStateOf("cash") } // "cash" or "bank"
    var description by remember { mutableStateOf("استرداد رصيد دائن فائض من المورد") }

    com.batterysales.ui.components.AppDialog(
        onDismiss = onDismiss,
        title = "استرداد نقدي/بنكي من المورد",
        confirmButton = {
            Button(onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                if (amt > 0) {
                    onConfirm(amt, destination, description)
                }
            }) { Text("تأكيد الاسترداد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = "المبلغ المسترد",
            keyboardType = com.batterysales.ui.components.KeyboardLanguage.NUMERIC
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("وجهة إيداع المبلغ:", style = MaterialTheme.typography.labelMedium)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { destination = "cash" }) {
                RadioButton(selected = destination == "cash", onClick = { destination = "cash" })
                Text("إيداع نقدي في خزينة المستودع الرئيسي")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { destination = "bank" }) {
                RadioButton(selected = destination == "bank", onClick = { destination = "bank" })
                Text("إيداع في الحساب البنكي")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        com.batterysales.ui.components.CustomKeyboardTextField(
            value = description,
            onValueChange = { description = it },
            label = "الوصف / ملاحظة",
            keyboardType = com.batterysales.ui.components.KeyboardLanguage.ARABIC
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupplierDetailsScreen(
    supplierId: String,
    navController: NavController,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val supplierItem by viewModel.selectedSupplierReport.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSupplierLoading by viewModel.isSupplierLoading.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showRefundDialog by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    val dateFormatterShort = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())

    // Trigger targeted loading for this specific supplier
    LaunchedEffect(supplierId) {
        viewModel.onSupplierSelected(supplierId)
    }

    Scaffold(
        topBar = {
            SharedHeader(
                title = supplierItem?.supplier?.name ?: "تحميل التقرير...",
                onBackClick = { navController.popBackStack() },
                actions = {
                    if (supplierItem != null) {
                        HeaderIconButton(
                            icon = Icons.Default.AutoFixHigh,
                            onClick = { viewModel.triggerAutoLink(supplierId) },
                            contentDescription = "إعادة المزامنة التلقائية"
                        )
                        HeaderIconButton(
                            icon = Icons.Default.Sync,
                            onClick = { viewModel.syncSupplier(supplierId) },
                            contentDescription = "تحديث"
                        )
                        HeaderIconButton(
                            icon = Icons.Default.Share,
                            onClick = { com.batterysales.utils.PrintUtils.shareSupplierReport(context, supplierItem!!) },
                            contentDescription = "مشاركة"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isSupplierLoading && supplierItem == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (supplierItem == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("المورد غير موجود أو لا توجد بيانات")
            }
        } else {
            val item = supplierItem!!
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Date Filter Card
                    item {
                        Card(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("الفترة المحددة", style = MaterialTheme.typography.labelSmall)
                                        val filterText = if (startDate != null && endDate != null) {
                                            "${dateFormatterShort.format(java.util.Date(startDate!!))} - ${dateFormatterShort.format(java.util.Date(endDate!!))}"
                                        } else {
                                            "كل الفترات"
                                        }
                                        Text(filterText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (startDate != null || endDate != null) {
                                    IconButton(onClick = { viewModel.onDateRangeSelected(null, null) }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح")
                                    }
                                }
                            }
                        }
                    }

                    // Financial Summary
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("ملخص الحساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f).widthIn(min = 140.dp)) {
                                        InfoBadge(label = "إجمالي مدين", value = "JD ${String.format("%.3f", item.totalDebit)}", color = Color(0xFFFB8C00))
                                    }
                                    Box(modifier = Modifier.weight(1f).widthIn(min = 140.dp)) {
                                        InfoBadge(label = "إجمالي دائن", value = "JD ${String.format("%.3f", item.totalCredit)}", color = Color(0xFF10B981))
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                val settledOverpayment = maxOf(0.0, item.totalCredit - item.totalDebit)
                                val isExcess = settledOverpayment > 0.001
                                val uncoveredDebt = maxOf(0.0, item.totalDebit - item.supplier.totalCredit)

                                val displayColor = if (isExcess) Color(0xFF0284C7) else if (uncoveredDebt > 0.001) Color(0xFFEF4444) else Color(0xFF10B981)
                                val labelText = if (isExcess) "رصيد فائض مسدد للمورد:" else "المتبقي بذمة المورد:"
                                val displayValue = if (isExcess) settledOverpayment else uncoveredDebt

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = displayColor.copy(alpha = 0.1f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = labelText,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "JD ${String.format("%,.3f", displayValue)}",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = displayColor
                                            )
                                        }

                                        if (isExcess) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = { showRefundDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("استرداد نقدي/بنكي من المورد", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tabs for Orders
                    item {
                        var selectedSubTab by remember { mutableStateOf(0) }
                        val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd hh:mm a", java.util.Locale.getDefault())

                        Column {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TabItem(
                                    title = "طلبيات معلقة",
                                    isSelected = selectedSubTab == 0,
                                    onClick = { selectedSubTab = 0 },
                                    modifier = Modifier.weight(1f)
                                )
                                TabItem(
                                    title = "طلبيات مغطاة/مسددة",
                                    isSelected = selectedSubTab == 1,
                                    onClick = { selectedSubTab = 1 },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val allOrders = (item.regularOrders + item.obligatedOrders)
                                .sortedByDescending { it.entry.getEffectiveDate() }

                            val filteredOrders = if (selectedSubTab == 0) {
                                allOrders.filter { it.remainingBalance > 0.001 }
                            } else {
                                allOrders.filter { it.remainingBalance <= 0.001 }
                            }

                            if (filteredOrders.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("لا توجد طلبيات في هذا القسم", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                filteredOrders.forEach { po ->
                                    PurchaseOrderCard(po, dateFormatter, navController)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                if (isSupplierLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color(0xFF0284C7))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("جاري معالجة العملية...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (showDatePicker) {
            AppDateRangePickerDialog(
                state = dateRangePickerState,
                onDismiss = { showDatePicker = false },
                onConfirm = {
                    viewModel.onDateRangeSelected(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis)
                    showDatePicker = false
                }
            )
        }

        if (showRefundDialog && supplierItem != null) {
            val excessVal = maxOf(0.0, supplierItem!!.totalCredit - supplierItem!!.totalDebit)
            SupplierRefundDialog(
                initialAmount = excessVal,
                onDismiss = { showRefundDialog = false },
                onConfirm = { amt, dest, desc ->
                    viewModel.recordSupplierRefund(supplierItem!!.supplier.id, amt, dest, desc)
                    showRefundDialog = false
                }
            )
        }
    }
}
