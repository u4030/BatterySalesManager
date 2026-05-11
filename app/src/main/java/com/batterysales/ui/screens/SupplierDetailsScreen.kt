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

@Composable
fun SupplierDetailsScreen(
    supplierId: String,
    navController: NavController,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val supplierReportItems by viewModel.supplierReport.collectAsState()
    val supplierItem = supplierReportItems.find { it.supplier.id == supplierId }
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSupplierLoading by viewModel.isSupplierLoading.collectAsState()

    // Trigger report loading for this specific supplier if not already loaded
    LaunchedEffect(supplierId) {
        if (supplierReportItems.isEmpty()) {
            viewModel.loadSupplierReport()
        }
    }

    Scaffold(
        topBar = {
            SharedHeader(
                title = supplierItem?.supplier?.name ?: "تفاصيل المورد",
                onBackClick = { navController.popBackStack() },
                actions = {
                    if (supplierItem != null) {
                        HeaderIconButton(
                            icon = Icons.Default.Print,
                            onClick = { com.batterysales.utils.PrintUtils.printSupplierReport(context, supplierItem!!) },
                            contentDescription = "طباعة"
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
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    InfoBadge(label = "إجمالي مدين", value = "JD ${String.format("%.3f", item.totalDebit)}", color = Color(0xFFFB8C00))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    InfoBadge(label = "إجمالي دائن", value = "JD ${String.format("%.3f", item.totalCredit)}", color = Color(0xFF10B981))
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = if (item.balance > 0) Color(0xFFEF4444).copy(alpha = 0.1f) else Color(0xFF10B981).copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("المتبقي بذمة المورد:", fontWeight = FontWeight.Bold)
                                    Text("JD ${String.format("%.3f", item.balance)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = if (item.balance > 0) Color(0xFFEF4444) else Color(0xFF10B981))
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
                                title = "طلبيات مسددة",
                                isSelected = selectedSubTab == 1,
                                onClick = { selectedSubTab = 1 },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val currentRegularOrders = if (selectedSubTab == 0) {
                            item.regularOrders.filter { it.remainingBalance > 0.001 }
                        } else {
                            item.regularOrders.filter { it.remainingBalance <= 0.001 }
                        }

                        val currentObligatedOrders = if (selectedSubTab == 0) {
                            item.obligatedOrders.filter { it.remainingBalance > 0.001 }
                        } else {
                            item.obligatedOrders.filter { it.remainingBalance <= 0.001 }
                        }

                        if (currentRegularOrders.isEmpty() && currentObligatedOrders.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("لا توجد طلبيات في هذا القسم", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (currentRegularOrders.isNotEmpty()) {
                            Text("طلبيات شراء:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            currentRegularOrders.forEach { po ->
                                PurchaseOrderCard(po, dateFormatter, navController)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        if (currentObligatedOrders.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("طلبيات مرتبطة بالتزامات (شيكات/كمبيالات):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.height(12.dp))
                            currentObligatedOrders.forEach { po ->
                                PurchaseOrderCard(po, dateFormatter, navController)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
