package com.batterysales.ui.screens

import androidx.compose.foundation.clickable
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
import com.batterysales.data.models.Warehouse
import com.batterysales.viewmodel.InventoryReportItem
import com.batterysales.viewmodel.ReportsViewModel
import java.util.Locale

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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(reportItems) { item ->
                    ReportItemCard(
                        item = item,
                        warehouses = warehouses,
                        onClick = {
                            val capacityStr = item.variant.capacity.toString()
                            val productName = item.product.name
                            navController.navigate(
                                "product_ledger/${item.variant.id}/$productName/$capacityStr"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ReportItemCard(
    item: InventoryReportItem,
    warehouses: List<Warehouse>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Header: Product Name
            Text(
                text = "${item.product.name} - ${item.variant.capacity} أمبير",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Main Info: Totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoColumn(label = "إجمالي الكمية", value = item.totalQuantity.toString())
                InfoColumn(label = "سعر التكلفة", value = String.format(Locale.US, "%.2f", item.averageCost))
                InfoColumn(label = "اجمالي الأمبيرات الكلي", value = String.format(Locale.US, "%.2f", item.totalCostValue))
            }

            // Warehouse Breakdown
            val quantitiesInWarehouses = warehouses.mapNotNull { warehouse ->
                val quantity = item.warehouseQuantities[warehouse.id]
                if (quantity != null && quantity != 0) {
                    warehouse.name to quantity
                } else null
            }

            if (quantitiesInWarehouses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("الكمية بالمستودعات:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val totalWarehouseQuantity = quantitiesInWarehouses.sumOf { it.second }
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(modifier = Modifier.padding(end = 32.dp)) // خط فاصل قصير
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text(
                            text = "المجموع: ",
                            fontWeight = FontWeight.Bold, // خط عريض للمجموع
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = totalWarehouseQuantity.toString(),
                            fontWeight = FontWeight.Bold, // خط عريض للمجموع
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // عرض الكمية في كل مستودع
                    quantitiesInWarehouses.forEach { (warehouseName, quantity) ->
                        Row {
                            Text(
                                text = "$warehouseName: ",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = quantity.toString(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
