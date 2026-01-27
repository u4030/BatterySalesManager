package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val route: String
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

    val items = mutableListOf(
        DashboardItem("المبيعات", Icons.Default.ShoppingCart, "sales"),
        DashboardItem("الفواتير", Icons.Default.Receipt, "invoices"),
        DashboardItem("المستودع", Icons.Default.Inventory, "warehouse"),
        DashboardItem("إدارة المنتجات", Icons.Default.Settings, "product_management"),
        DashboardItem("إدخال مخزون", Icons.Default.AddBusiness, "stock_entry"),
        DashboardItem("ترحيل مخزون", Icons.Default.MoveDown, "stock_transfer"),
        DashboardItem("العملاء", Icons.Default.People, "clients")
    )

    if (isAdmin) {
        items.add(DashboardItem("الخزينة", Icons.Default.AccountBalance, "accounting"))
        items.add(DashboardItem("الكمبيالات", Icons.Default.Description, "bills"))
    }

    items.add(DashboardItem("التقارير", Icons.Default.Assessment, "reports"))

    if (isAdmin) {
        items.add(DashboardItem("إدارة المستخدمين", Icons.Default.Group, "user_management"))
        items.add(DashboardItem("الموافقات", Icons.Default.FactCheck, "approvals"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("لوحة التحكم", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        navController.navigate("login") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "خروج")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "مرحباً بك، ${currentUser?.displayName ?: ""}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Alerts Section
            if (isAdmin && dashboardState.pendingApprovalsCount > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { navController.navigate("approvals") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("لديك ${dashboardState.pendingApprovalsCount} طلبات موافقة معلقة", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (dashboardState.lowStockVariants.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("تنبيه انخفاض المخزون", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            }
                            dashboardState.lowStockVariants.take(3).forEach { lowStockItem ->
                                Text("${lowStockItem.productName} (${lowStockItem.capacity} أمبير): الكمية ${lowStockItem.currentQuantity}", fontSize = 14.sp)
                            }
                            if (dashboardState.lowStockVariants.size > 3) {
                                Text("المزيد...", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.heightIn(max = 1000.dp) // Set a height for nested grid
                ) {
                    items(items) { item ->
                        DashboardCardItem(item) {
                            navController.navigate(item.route)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCardItem(item: DashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
