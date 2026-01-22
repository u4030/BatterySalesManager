package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.viewmodel.WarehouseViewModel

@Composable
fun WarehouseScreen(navController: NavController, viewModel: WarehouseViewModel = hiltViewModel()) {
    val stockLevels by viewModel.stockLevels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("مخزون المستودعات") })
        }
    ) { padding ->
        if (isLoading && stockLevels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                // Group items by warehouse
                stockLevels.groupBy { it.warehouse.name }.forEach { (warehouseName, items) ->
                    // Warehouse Header
                    item {
                        Text(
                            text = warehouseName,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Divider()
                    }

                    // Stock Items in this warehouse
                    items(items) { stockItem ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${stockItem.product.name} - ${stockItem.variant.capacity} أمبير",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "الكمية المتاحة:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = stockItem.quantity.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
