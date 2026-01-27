package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

data class DashboardItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
    val items = listOf(
        DashboardItem("المبيعات", Icons.Default.ShoppingCart, "sales"),
        DashboardItem("الفواتير", Icons.Default.Receipt, "invoices"),
        DashboardItem("المستودع", Icons.Default.Inventory, "warehouse"),
        DashboardItem("إدارة المنتجات", Icons.Default.Settings, "product_management"),
        DashboardItem("إدخال مخزون", Icons.Default.AddBusiness, "stock_entry"),
        DashboardItem("ترحيل مخزون", Icons.Default.MoveDown, "stock_transfer"),
        DashboardItem("العملاء", Icons.Default.People, "clients"),
        DashboardItem("الخزينة", Icons.Default.AccountBalance, "accounting"),
        DashboardItem("الكمبيالات", Icons.Default.Description, "bills"),
        DashboardItem("التقارير", Icons.Default.Assessment, "reports")
    )

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Text(
                "مرحباً بك في نظام إدارة البطاريات",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
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
