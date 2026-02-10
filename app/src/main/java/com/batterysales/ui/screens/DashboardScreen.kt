package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.viewmodel.AuthViewModel
import com.batterysales.viewmodel.DashboardViewModel

data class DashboardItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val color: Color = Color.Gray
)

@OptIn(ExperimentalMaterial3Api::class)
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

    val dashboardBgColor = Color(0xFF0F0F0F)
    val cardBgColor = Color(0xFF1C1C1C)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    val items = remember(isAdmin) {
        val list = mutableListOf(
            DashboardItem("المبيعات", Icons.Default.ShoppingCart, "sales", Color(0xFFEF4444)),
            DashboardItem("الفواتير", Icons.Default.ReceiptLong, "invoices", Color(0xFFF59E0B)),
            DashboardItem("المستودع", Icons.Default.Warehouse, "warehouse", Color(0xFFFACC15)),
            DashboardItem("إدارة المنتجات", Icons.Default.Inventory2, "product_management", Color(0xFFEF4444)),
            DashboardItem("إدخال مخزون", Icons.Default.SyncAlt, "stock_entry", Color(0xFF10B981)),
            DashboardItem("ترحيل مخزون", Icons.Default.LocalShipping, "stock_transfer", Color(0xFF3B82F6))
        )

        if (isAdmin) {
            list.add(DashboardItem("الخزينة", Icons.Default.AccountBalance, "accounting", Color(0xFF8B5CF6)))
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
        containerColor = dashboardBgColor,
        bottomBar = {
            NavigationBar(
                containerColor = cardBgColor,
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("الرئيسية") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("product_management") },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("المنتجات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("sales") },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text("المبيعات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("الإعدادات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFACC15),
                        selectedTextColor = Color(0xFFFACC15),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Header Section
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = -16.dp, vertical = -16.dp) // Offset content padding
                        .background(
                            brush = headerGradient,
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                onClick = { navController.navigate("settings") },
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            Text(
                                text = "لوحة التحكم",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Icon(
                                Icons.Default.LightMode,
                                contentDescription = "Mode",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Summary Cards Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SummaryCard(
                                title = "المبيعات اليوم",
                                value = "${String.format("%,.0f", dashboardState.todaySales)} ر.س",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCard(
                                title = "الفواتير",
                                value = "${dashboardState.todayInvoicesCount} فاتورة",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Alerts Section
            if (isAdmin && dashboardState.pendingApprovalsCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { navController.navigate("approvals") },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1F1F)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("لديك ${dashboardState.pendingApprovalsCount} طلبات موافقة معلقة", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (dashboardState.lowStockVariants.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { navController.navigate("reports") },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1F1F)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("تنبيه انخفاض المخزون", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            dashboardState.lowStockVariants.take(3).forEach { lowStockItem ->
                                Text(
                                    "${lowStockItem.productName} (${lowStockItem.capacity} أمبير) في ${lowStockItem.warehouseName}: الكمية ${lowStockItem.currentQuantity}",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Grid Items
            items(items) { item ->
                DashboardCardItem(item) {
                    navController.navigate(item.route)
                }
            }

            // Extra spacer at bottom
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
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
fun DashboardCardItem(item: DashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
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
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}
