package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.viewmodel.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavHostController,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val totalSales by viewModel.totalSales.collectAsState()
    val totalExpenses by viewModel.totalExpenses.collectAsState()
    val totalInvoices by viewModel.totalInvoices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التقارير والإحصائيات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                ReportCardItem(
                    title = "إجمالي المبيعات",
                    value = "SR ${String.format("%.2f", totalSales)}",
                    icon = Icons.Default.TrendingUp,
                    color = Color(0xFF4CAF50)
                )
                
                ReportCardItem(
                    title = "إجمالي المصروفات",
                    value = "SR ${String.format("%.2f", totalExpenses)}",
                    icon = Icons.Default.TrendingDown,
                    color = Color(0xFFF44336)
                )
                
                ReportCardItem(
                    title = "صافي الربح التقديري",
                    value = "SR ${String.format("%.2f", totalSales - totalExpenses)}",
                    icon = Icons.Default.AccountBalance,
                    color = MaterialTheme.colorScheme.primary
                )
                
                ReportCardItem(
                    title = "عدد الفواتير المصدرة",
                    value = "$totalInvoices فاتورة",
                    icon = Icons.Default.Description,
                    color = Color(0xFF2196F3)
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { /* وظيفة تصدير PDF */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تصدير تقرير PDF")
                }
            }
        }
    }
}

@Composable
fun ReportCardItem(title: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 14.sp, color = Color.Gray)
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}
