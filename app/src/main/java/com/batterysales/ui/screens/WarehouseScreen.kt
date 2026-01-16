package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.viewmodel.WarehouseStockItem
import com.batterysales.viewmodel.WarehouseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseScreen(
    navController: NavHostController,
    viewModel: WarehouseViewModel = hiltViewModel()
) {
    val stockLevels by viewModel.stockLevels
    val groupedStock = stockLevels.groupBy { it.warehouse }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مخزون المستودع", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (groupedStock.isEmpty()) {
                    item {
                        Text("لا يوجد مخزون لعرضه.")
                    }
                } else {
                    groupedStock.forEach { (warehouse, stockItems) ->
                        item {
                            Text(
                                text = warehouse.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(stockItems) { item ->
                            WarehouseItemCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WarehouseItemCard(item: WarehouseStockItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${item.product.name} - ${item.variant.capacity} أمبير", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("الباركود: ${item.variant.barcode}", color = Color.Gray, fontSize = 14.sp)
            }
            Text("الكمية: ${item.quantity}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
