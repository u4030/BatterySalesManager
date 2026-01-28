package com.batterysales.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.data.models.Warehouse
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.viewmodel.InventoryReportItem
import com.batterysales.viewmodel.ReportsViewModel
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Composable
fun SearchBar(
    barcodeFilter: String?,
    onClear: () -> Unit,
    onScan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = barcodeFilter ?: "",
            onValueChange = { },
            readOnly = true,
            label = { Text("فلترة بالباركود") },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onScan) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود")
        }
        if (!barcodeFilter.isNullOrBlank()) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "مسح الفلتر")
            }
        }
    }
}

@Composable
fun ReportsScreen(navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val reportItems by viewModel.inventoryReport.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val barcodeFilter by viewModel.barcodeFilter.collectAsState()
    var showScanner by remember { mutableStateOf(false) }

    val grandTotalQuantity = remember(reportItems) {
        reportItems.sumOf { it.totalQuantity }
    }

    if (showScanner) {
        BarcodeScanner(onBarcodeScanned = { barcode ->
            viewModel.onBarcodeScanned(barcode)
            showScanner = false
        })
    } else {
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
                Column(modifier = Modifier.padding(padding)) {
                    SearchBar(
                        barcodeFilter = barcodeFilter,
                        onClear = { viewModel.onBarcodeScanned(null) },
                        onScan = { showScanner = true }
                    )

                    // Grand Total Card
                    if (reportItems.isNotEmpty()) {
                        GrandTotalCard(totalQuantity = grandTotalQuantity)
                    }

                    LazyColumn(
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
    }
}
@Composable
fun GrandTotalCard(totalQuantity: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "المجموع الكلي للكمية",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = totalQuantity.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
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
            val isLowStock = item.variant.minQuantity > 0 && item.totalQuantity <= item.variant.minQuantity

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoColumn(
                    label = "إجمالي الكمية",
                    value = item.totalQuantity.toString(),
                    valueColor = if (isLowStock) Color(0xFFD32F2F) else Color.Unspecified
                )
                if (item.variant.minQuantity > 0) {
                    InfoColumn(label = "الحد الأدنى", value = item.variant.minQuantity.toString())
                }
                InfoColumn(
                    label = "متوسط التكلفة",
                    value = String.format(Locale.US, "%.2f", item.averageCost)
                )
                InfoColumn(
                    label = "قيمة المخزون",
                    value = String.format(Locale.US, "%.2f", item.totalCostValue)
                )
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
fun InfoColumn(label: String, value: String, valueColor: Color = Color.Unspecified, modifier: Modifier = Modifier) {
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
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
