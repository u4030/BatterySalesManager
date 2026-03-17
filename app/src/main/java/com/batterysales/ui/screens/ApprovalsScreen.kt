package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.viewmodel.ApprovalItem
import com.batterysales.viewmodel.ApprovalsViewModel
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApprovalsScreen(
    navController: NavHostController,
    viewModel: ApprovalsViewModel = hiltViewModel()
) {
    val items by viewModel.approvalItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val bgColor = MaterialTheme.colorScheme.background
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    Scaffold(
        containerColor = bgColor
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Gradient Header
            item {
                SharedHeader(
                    title = "الموافقات المعلقة",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Default.Refresh,
                            onClick = { /* Reload logic if available */ },
                            contentDescription = "Refresh"
                        )
                    }
                )
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else if (items.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد طلبات موافقة معلقة", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            } else {
                items(items) { item ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ApprovalCard(
                            item = item,
                            onApprove = {
                                if (item.type == "STOCK_ENTRY") {
                                    viewModel.approveEntry(item.entry!!.id)
                                } else {
                                    viewModel.approveRequest(item.request!!)
                                }
                            },
                            onReject = {
                                if (item.type == "STOCK_ENTRY") {
                                    viewModel.rejectEntry(item.entry!!.id)
                                } else {
                                    viewModel.rejectRequest(item.request!!.id)
                                }
                            },
                            onEdit = {
                                if (item.type == "STOCK_ENTRY") {
                                    navController.navigate("stock_entry?entryId=${item.entry!!.id}")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApprovalCard(item: ApprovalItem, onApprove: () -> Unit, onReject: () -> Unit, onEdit: () -> Unit) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    val isStockEntry = item.type == "STOCK_ENTRY"
    val actionColor = if (isStockEntry) {
        if ((item.entry?.quantity ?: 0) > 0) Color(0xFF10B981) else Color(0xFFEF4444)
    } else {
        if (item.request?.actionType == com.batterysales.data.models.ApprovalRequest.ACTION_EDIT) Color(0xFFFB8C00) else Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isStockEntry) {
                            "${item.variantCapacity} | ${item.warehouseName}"
                        } else {
                            if (item.variantCapacity.isNotEmpty()) "${item.variantCapacity} | طلب تعديل/حذف" else "طلب تعديل/حذف منتج"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Surface(
                    color = actionColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isStockEntry) {
                            if ((item.entry?.quantity ?: 0) > 0) "+${item.entry?.quantity}" else "${item.entry?.quantity}"
                        } else {
                            if (item.request?.actionType == com.batterysales.data.models.ApprovalRequest.ACTION_EDIT) "تعديل" else "حذف"
                        },
                        color = actionColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isStockEntry) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "المورد: ${item.entry?.supplier}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (item.request?.actionType == com.batterysales.data.models.ApprovalRequest.ACTION_EDIT) {
                    // Show proposed changes
                    Surface(
                        color = Color(0xFFFB8C00).copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("البيانات المقترحة الجديدة:", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFB8C00), fontWeight = FontWeight.Bold)

                            if (item.type == "PRODUCT_REQUEST") {
                                Text("الاسم الجديد: ${item.request.productData?.name}", style = MaterialTheme.typography.bodySmall)
                            } else {
                                val v = item.request.variantData
                                Text("السعة: ${v?.capacity}A", style = MaterialTheme.typography.bodySmall)
                                Text("المواصفة: ${v?.specification}", style = MaterialTheme.typography.bodySmall)
                                Text("الباركود: ${v?.barcode}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (item.request?.actionType == com.batterysales.data.models.ApprovalRequest.ACTION_DELETE) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (item.type == "PRODUCT_REQUEST") "تنبيه: سيتم أرشفة (حذف) هذا المنتج بالكامل!" else "تنبيه: سيتم أرشفة (حذف) هذه السعة!",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = dateFormatter.format(if (isStockEntry) item.entry!!.timestamp else item.request!!.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val requesterName = if (isStockEntry) item.entry!!.createdByUserName else item.request!!.requesterName
                if (requesterName.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "بواسطة: $requesterName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.1f))
            Spacer(modifier = Modifier.height(20.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isStockEntry) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تعديل", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("رفض", fontSize = 12.sp)
                }

                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("موافقة", fontSize = 12.sp)
                }
            }
        }
    }
}
