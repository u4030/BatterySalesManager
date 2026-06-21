package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.ui.components.InfoBadge
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton
import com.batterysales.ui.components.TabItem
import com.batterysales.viewmodel.ReportsViewModel
import com.batterysales.ui.components.AppDateRangePickerDialog

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
                            contentDescription = "تسوية ذكية"
                        )
                        HeaderIconButton(
                            icon = Icons.Default.Sync,
                            onClick = { viewModel.syncSupplier(supplierId) },
                            contentDescription = "مزامنة كاملة"
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (supplierItem == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("المورد غير موجود أو لا توجد بيانات")
            }
        } else {
            val item = supplierItem!!
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date Filter Card
                item {
                    Card(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("الفترة الزمنية للتقرير", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val startStr = startDate?.let { dateFormatterShort.format(java.util.Date(it)) } ?: "البداية"
                                val endStr = endDate?.let { dateFormatterShort.format(java.util.Date(it)) } ?: "النهاية"
                                Text("$startStr - $endStr", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = if (item.balance > 0) Color(0xFFEF4444).copy(alpha = 0.1f) else Color(0xFF10B981).copy(alpha = 0.1f))
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "المتبقي بذمة المورد:",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Text(
                                        text = "JD ${String.format("%.3f", item.balance)}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (item.balance > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                                    )
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
        }
    }

    if (showDatePicker) {
        com.batterysales.ui.components.AppDateRangePickerDialog(
            state = dateRangePickerState,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.onDateRangeSelected(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis)
                showDatePicker = false
            }
        )
    }
}
