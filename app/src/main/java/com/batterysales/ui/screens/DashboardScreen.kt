package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.viewmodel.AppNotification
import com.batterysales.viewmodel.AuthViewModel
import com.batterysales.viewmodel.DashboardViewModel
import com.batterysales.viewmodel.NotificationType
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

data class DashboardItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val color: Color = Color.Gray
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val userRole = currentUser?.role ?: "seller"
    val isAdmin = userRole == "admin"
    var showNotificationDialog by remember { mutableStateOf(false) }

    val dashboardBgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    val items = remember(isAdmin, currentUser) {
        val list = mutableListOf(
            DashboardItem("المبيعات", Icons.Default.ShoppingCart, "sales", Color(0xFFEF4444)),
            DashboardItem("الفواتير", Icons.Default.ReceiptLong, "invoices", Color(0xFFF59E0B)),
            DashboardItem("المستودع", Icons.Default.Warehouse, "warehouse", Color(0xFFFACC15)),
            DashboardItem("إدارة المنتجات", Icons.Default.Inventory2, "product_management", Color(0xFFEF4444)),
            DashboardItem("إدخال مخزون", Icons.Default.SyncAlt, "stock_entry", Color(0xFF10B981)),
            DashboardItem("ترحيل مخزون", Icons.Default.LocalShipping, "stock_transfer", Color(0xFF3B82F6))
        )

        val canViewTreasury = isAdmin || currentUser?.permissions?.contains("view_treasury") == true
        if (canViewTreasury) {
            list.add(DashboardItem("الخزينة", Icons.Default.AccountBalance, "accounting", Color(0xFF8B5CF6)))
        }

        if (isAdmin) {
            list.add(DashboardItem("البنك", Icons.Default.Savings, "bank", Color(0xFF06B6D4)))
            list.add(DashboardItem("الكمبيالات", Icons.Default.Description, "bills", Color(0xFFF97316)))
            list.add(DashboardItem("الموردين", Icons.Default.LocalShipping, "suppliers", Color(0xFF64748B)))
        }

        list.add(DashboardItem("التقارير", Icons.Default.Assessment, "reports", Color(0xFF10B981)))

        if (isAdmin) {
            list.add(DashboardItem("إدارة المستخدمين", Icons.Default.Group, "user_management", Color(0xFFEC4899)))
            list.add(DashboardItem("الموافقات", Icons.Default.FactCheck, "approvals", Color(0xFFF59E0B)))
        }
        list
    }

    Scaffold(
        containerColor = dashboardBgColor
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            item {
                SharedHeader(
                    title = "لوحة التحكم",
                    actions = {
                        Box {
                            HeaderIconButton(
                                icon = if (dashboardState.notifications.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                onClick = { showNotificationDialog = true },
                                contentDescription = "Notifications"
                            )
                            if (dashboardState.notifications.isNotEmpty()) {
                                Surface(
                                    color = Color.Red,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .offset(x = (-4).dp, y = 4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = dashboardState.notifications.size.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        HeaderIconButton(
                            icon = Icons.Default.Settings,
                            onClick = { navController.navigate("settings") },
                            contentDescription = "Settings"
                        )
                        HeaderIconButton(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            onClick = {
                                authViewModel.logout()
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            contentDescription = "Logout"
                        )
                        Icon(
                            if (isSystemInDarkTheme()) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Mode",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )

                // Summary content below header
                Column(modifier = Modifier.padding(16.dp)) {
                    if (dashboardState.warehouseStats.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(dashboardState.warehouseStats) { stats ->
                                Card(
                                    modifier = Modifier.widthIn(min = if (isAdmin) 220.dp else 280.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                ) {
                                    Box(
                                        modifier = Modifier.background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFFFB8C00), // برتقالي أفتح
                                                    Color(0xFFE53935)  // أحمر/برتقالي أغمق
                                                )
                                            )
                                        )
                                    ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            stats.warehouseName,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        androidx.compose.foundation.layout.FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    "إجمالي التحصيل | ",
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Text(
                                                    "JD ${
                                                        String.format(
                                                            "%,.2f",
                                                            stats.todayCollection
                                                        )
                                                    }",
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    "عدد الفواتير",
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Text(
                                                    "${stats.todayCollectionCount}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                        }
                                    }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            "لا توجد مبيعات مسجلة لهذا اليوم حتى الآن",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Grid Items (Chunked into Rows)
            items(items.chunked(2)) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { item ->
                        DashboardCardItem(
                            item = item,
                            modifier = Modifier.weight(1f)
                        ) {
                            navController.navigate(item.route)
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Extra spacer at bottom
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showNotificationDialog) {
        NotificationDialog(
            notifications = dashboardState.notifications,
            onDismiss = { showNotificationDialog = false },
            onNotificationClick = { route ->
                if (route != null) {
                    navController.navigate(route)
                }
                showNotificationDialog = false
            }
        )
    }
}

@Composable
fun NotificationDialog(
    notifications: List<AppNotification>,
    onDismiss: () -> Unit,
    onNotificationClick: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFFB8C00))
                Spacer(modifier = Modifier.width(8.dp))
                Text("التنبيهات", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد تنبيهات حالياً", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications) { notification ->
                        NotificationItem(notification, onNotificationClick)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun NotificationItem(notification: AppNotification, onClick: (String?) -> Unit) {
    val color = when (notification.type) {
        NotificationType.LOW_STOCK -> Color(0xFFEF4444)
        NotificationType.PENDING_APPROVAL -> Color(0xFFF59E0B)
        NotificationType.UPCOMING_BILL -> Color(0xFF3B82F6)
        NotificationType.OVERDUE_BILL -> Color(0xFFEF4444)
    }

    val icon = when (notification.type) {
        NotificationType.LOW_STOCK -> Icons.Default.Warning
        NotificationType.PENDING_APPROVAL -> Icons.Default.FactCheck
        NotificationType.UPCOMING_BILL -> Icons.Default.CalendarMonth
        NotificationType.OVERDUE_BILL -> Icons.Default.Error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(notification.route) },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = color,
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DashboardCardItem(item: DashboardItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top colored border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(item.color)
                    .align(Alignment.TopCenter)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon with glow
                Box(contentAlignment = Alignment.Center) {
                    // Glow effect
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(item.color.copy(alpha = 0.3f), CircleShape)
                            .blur(20.dp)
                    )

                    Surface(
                        color = item.color,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}
