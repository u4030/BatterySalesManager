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

@Composable
fun ComparisonHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(80.dp)) // Label width
        Text(
            "الحالي",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.width(16.dp)) // Arrow spacer
        Text(
            "المقترح",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFB8C00),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ComparisonDataRow(label: String, oldValue: String, newValue: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )

        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = oldValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
            )
        }

        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFB8C00))

        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFFFB8C00).copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = newValue,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
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
                            val action = if (item.request?.actionType == com.batterysales.data.models.ApprovalRequest.ACTION_EDIT) "تعديل" else "حذف"
                            val target = if (item.request?.targetType == com.batterysales.data.models.ApprovalRequest.TARGET_PRODUCT) "منتج" else "سعة"
                            "طلب $action $target: ${item.variantCapacity}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Surface(
                    color = actionColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isStockEntry) {
                                if ((item.entry?.quantity ?: 0) > 0) Icons.Default.Add else Icons.Default.Remove
                            } else {
                                if (item.request?.actionType == com.batterysales.data.models.ApprovalRequest.ACTION_EDIT) Icons.Default.EditNote else Icons.Default.DeleteForever
                            },
                            contentDescription = null,
                            tint = actionColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isStockEntry) {
                                if ((item.entry?.quantity ?: 0) > 0) "${item.entry?.quantity}" else "${item.entry?.quantity}"
                            } else {
                                if (item.request?.actionType == com.batterysales.data.models.ApprovalRequest.ACTION_EDIT) "تعديل" else "حذف"
                            },
                            color = actionColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        color = Color(0xFFFB8C00).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFB8C00).copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFB8C00), modifier = Modifier.size(16.dp))
                                Text("البيانات المقترحة الجديدة:", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFB8C00), fontWeight = FontWeight.Bold)
                            }

                            HorizontalDivider(color = Color(0xFFFB8C00).copy(alpha = 0.1f))

                            ComparisonHeader()

                            if (item.type == "PRODUCT_REQUEST") {
                                ComparisonDataRow("اسم المنتج", item.request.oldProductData?.name ?: "---", item.request.productData?.name ?: "---")
                                ComparisonDataRow("المواصفة", item.request.oldProductData?.specification ?: "---", item.request.productData?.specification ?: "---")
                            } else {
                                val old = item.request.oldVariantData
                                val new = item.request.variantData
                                ComparisonDataRow("السعة", "${old?.capacity}A", "${new?.capacity}A")
                                ComparisonDataRow("المواصفة", old?.specification?.ifEmpty { "---" } ?: "---", new?.specification?.ifEmpty { "---" } ?: "---")
                                ComparisonDataRow("الباركود", old?.barcode?.ifEmpty { "---" } ?: "---", new?.barcode?.ifEmpty { "---" } ?: "---")
                                ComparisonDataRow("سعر البيع", "JD ${old?.sellingPrice}", "JD ${new?.sellingPrice}")
                            }
                        }
                    }
                } else if (item.request?.actionType == com.batterysales.data.models.ApprovalRequest.ACTION_DELETE) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier.size(40.dp).background(Color(0xFFEF4444).copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                            }
                            Column {
                                val target = if (item.type == "PRODUCT_REQUEST") "هذا المنتج بالكامل" else "هذه السعة"
                                Text(
                                    text = "تنبيه: حذف نهائي",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "سيتم أرشفة $target من النظام.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFEF4444).copy(alpha = 0.8f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color.White.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (item.type == "PRODUCT_REQUEST") item.request?.productName ?: "" else "${item.productName} (${item.request?.variantCapacity}A)",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
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
