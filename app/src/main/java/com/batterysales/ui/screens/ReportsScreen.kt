package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.viewmodel.ReportsViewModel

@Composable
fun ReportsScreen(navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val reportItems by viewModel.inventoryReport.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("تقارير المخزون") })
        }
    ) { padding ->
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.fillMaxSize().wrapContentSize())
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
                // Header Row
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("المنتج", modifier = Modifier.weight(2f))
                        warehouses.forEach { warehouse ->
                            Text(warehouse.name, modifier = Modifier.weight(1f))
                        }
                        Text("الإجمالي", modifier = Modifier.weight(1f))
                    }
                    Divider()
                }

                // Data Rows
                items(reportItems) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(
                                    "product_ledger/${item.variant.id}/${item.product.name}/${item.variant.capacity}"
                                )
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Text("${item.product.name} - ${item.variant.capacity} أمبير", modifier = Modifier.weight(2f))
                        warehouses.forEach { warehouse ->
                            Text((item.warehouseQuantities[warehouse.id] ?: 0).toString(), modifier = Modifier.weight(1f))
                        }
                        Text(item.totalQuantity.toString(), modifier = Modifier.weight(1f))
                    }
                    Divider()
                }
            }
        }
    }
}
