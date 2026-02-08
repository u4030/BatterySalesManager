package com.batterysales.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.data.models.Warehouse
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.viewmodel.InventoryReportItem
import com.batterysales.viewmodel.ReportsViewModel
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    val supplierItems by viewModel.supplierReport.collectAsState()
    val isSeller by viewModel.isSeller.collectAsState()
    val warehouses by viewModel.filteredWarehouses.collectAsState()
    val oldBatterySummary by viewModel.oldBatterySummary.collectAsState()
    val oldBatteryWarehouseSummary by viewModel.oldBatteryWarehouseSummary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val barcodeFilter by viewModel.barcodeFilter.collectAsState()
    var showScanner by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val grandTotalQuantity = remember(reportItems) {
        reportItems.sumOf { it.totalQuantity }
    }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                BarcodeScanner(onBarcodeScanned = { barcode ->
                    viewModel.onBarcodeScanned(barcode)
                    showScanner = false
                })
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("التقارير والإحصائيات") })
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("مخزون المنتجات", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("البطاريات القديمة", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                        Text("حالة الموردين", modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                if (selectedTab == 0) {
                    SearchBar(
                        barcodeFilter = barcodeFilter,
                        onClear = { viewModel.onBarcodeScanned(null) },
                        onScan = { showScanner = true }
                    )

                    // Grand Total Card
                    if (reportItems.isNotEmpty()) {
                        GrandTotalCard(totalQuantity = grandTotalQuantity, isSeller = isSeller)
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(reportItems) { item ->
                            ReportItemCard(
                                item = item,
                                warehouses = warehouses,
                                isSeller = isSeller,
                                onClick = {
                                    val capacityStr = item.variant.capacity.toString()
                                    val productName = item.product.name
                                    val spec = item.variant.specification.ifEmpty { "no_spec" }
                                    navController.navigate(
                                        "product_ledger/${item.variant.id}/$productName/$capacityStr/$spec"
                                    )
                                }
                            )
                        }
                    }
                } else if (selectedTab == 1) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Actually the user wants a tab for each warehouse in old batteries
                        var selectedWHIndex by remember { mutableIntStateOf(0) }
                        val whList = warehouses

                        if (whList.isNotEmpty()) {
                            ScrollableTabRow(selectedTabIndex = selectedWHIndex, edgePadding = 16.dp) {
                                Tab(selected = selectedWHIndex == 0, onClick = { selectedWHIndex = 0 }) {
                                    Text("الكل", modifier = Modifier.padding(16.dp))
                                }
                                whList.forEachIndexed { index, warehouse ->
                                    Tab(selected = selectedWHIndex == index + 1, onClick = { selectedWHIndex = index + 1 }) {
                                        Text(warehouse.name, modifier = Modifier.padding(16.dp))
                                    }
                                }
                            }

                            val currentSummary = if (selectedWHIndex == 0) oldBatterySummary
                                                else oldBatteryWarehouseSummary[whList[selectedWHIndex - 1].id] ?: Pair(0, 0.0)

                            OldBatteryReportSection(currentSummary) {
                                navController.navigate("old_battery_ledger")
                            }
                        } else {
                            OldBatteryReportSection(oldBatterySummary) {
                                navController.navigate("old_battery_ledger")
                            }
                        }
                    }
                } else {
                    SupplierReportSection(viewModel, supplierItems)
                }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrandTotalCard(totalQuantity: Int, isSeller: Boolean) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportItemCard(
    item: InventoryReportItem,
    warehouses: List<Warehouse>,
    isSeller: Boolean,
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
                text = "${item.product.name} - ${item.variant.capacity} أمبير" +
                        if (item.variant.specification.isNotEmpty()) " (${item.variant.specification})" else "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Main Info: Totals
            val anyWarehouseLowStock = item.warehouseQuantities.any { (whId, qty) ->
                val threshold = item.variant.minQuantities[whId] ?: item.variant.minQuantity
                threshold > 0 && qty <= threshold
            }
            val isLowStock = anyWarehouseLowStock || (item.variant.minQuantity > 0 && item.totalQuantity <= item.variant.minQuantity)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoColumn(
                    label = "إجمالي الكمية",
                    value = item.totalQuantity.toString(),
                    valueColor = if (isLowStock) Color(0xFFD32F2F) else Color.Unspecified,
                    modifier = Modifier.weight(1f).widthIn(min = 120.dp)
                )
                if (item.variant.minQuantity > 0) {
                    InfoColumn(
                        label = "الحد الأدنى",
                        value = item.variant.minQuantity.toString(),
                        modifier = Modifier.weight(1f).widthIn(min = 120.dp)
                    )
                }
                if (!isSeller) {
                    InfoColumn(
                        label = "متوسط التكلفة",
                        value = "JD " + String.format(Locale.US, "%.3f", item.averageCost),
                        modifier = Modifier.weight(1f).widthIn(min = 120.dp)
                    )
                    InfoColumn(
                        label = "قيمة المخزون",
                        value = "JD " + String.format(Locale.US, "%.3f", item.totalCostValue),
                        modifier = Modifier.weight(1f).widthIn(min = 120.dp)
                    )
                }
            }

            // Warehouse Breakdown
            val quantitiesInWarehouses = warehouses.mapNotNull { warehouse ->
                val quantity = item.warehouseQuantities[warehouse.id]
                if (quantity != null && quantity != 0) {
                    val threshold = item.variant.minQuantities[warehouse.id] ?: item.variant.minQuantity
                    val isWHLow = threshold > 0 && quantity <= threshold
                    Triple(warehouse.name, quantity, isWHLow)
                } else null
            }

            if (quantitiesInWarehouses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("الكمية بالمستودعات:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quantitiesInWarehouses.forEach { (warehouseName, quantity, isWHLow) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$warehouseName: ",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = quantity.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isWHLow) Color(0xFFD32F2F) else Color.Unspecified
                            )
                            if (isWHLow) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("(منخفض)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OldBatteryReportSection(summary: Pair<Int, Double>, onViewDetails: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD7CCC8))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("إجمالي مخزون السكراب", fontWeight = FontWeight.Bold, color = Color(0xFF5D4037))
                Spacer(modifier = Modifier.height(16.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                        Text("الكمية", fontSize = 14.sp)
                        Text("${summary.first}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                        Text("الأمبيرات", fontSize = 14.sp)
                        Text("${String.format("%.1f", summary.second)}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onViewDetails,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037))
                ) {
                    Text("إدارة وسجل البطاريات القديمة")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupplierReportSection(viewModel: ReportsViewModel, items: List<com.batterysales.viewmodel.SupplierReportItem>) {
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()
    val searchQuery by viewModel.supplierSearchQuery.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    val dateRangePickerState = rememberDateRangePickerState()
    val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSupplierSearchQueryChanged(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("بحث باسم المورد أو رقم الشيك/السند...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSupplierSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { showDatePicker = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("الفترة الزمنية", style = MaterialTheme.typography.labelMedium)
                    val startStr = startDate?.let { dateFormatter.format(java.util.Date(it)) } ?: "البداية"
                    val endStr = endDate?.let { dateFormatter.format(java.util.Date(it)) } ?: "النهاية"
                    Text("$startStr - $endStr", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.PhotoCamera, contentDescription = null) // Just an icon for now, could be DateRange
            }
        }

        val totalSuppliersDebit = items.sumOf { it.balance }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            FlowRow(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "إجمالي المبالغ المستحقة للموردين",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    "JD ${String.format("%.3f", totalSuppliersDebit)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { item ->
                SupplierCard(item)
            }
        }
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            state = dateRangePickerState,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.onDateRangeSelected(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis)
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplierCard(item: com.batterysales.viewmodel.SupplierReportItem) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.supplier.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = 3
            ) {
                InfoColumn(label = "مدين (مشتريات)", value = "JD ${String.format("%.3f", item.totalDebit)}", modifier = Modifier.widthIn(min = 100.dp))
                InfoColumn(label = "دائن (دفعات)", value = "JD ${String.format("%.3f", item.totalCredit)}", modifier = Modifier.widthIn(min = 100.dp))
                InfoColumn(label = "الرصيد", value = "JD ${String.format("%.3f", item.balance)}", valueColor = if (item.balance > 0) Color.Red else Color.Unspecified, modifier = Modifier.widthIn(min = 100.dp))
            }

            if (expanded && item.purchaseOrders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("تفاصيل طلبيات الشراء:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                item.purchaseOrders.forEach { po ->
                    Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center) {
                            Text("${dateFormatter.format(po.entry.timestamp)}: JD ${String.format("%.3f", po.entry.totalCost)}", style = MaterialTheme.typography.bodyMedium)
                            Text("رصيد: JD ${String.format("%.3f", po.remainingBalance)}", style = MaterialTheme.typography.bodyMedium, color = if (po.remainingBalance > 0) Color.Red else Color.Gray)
                        }
                        if (po.linkedPaidAmount > 0) {
                            Text("تم دفع: JD ${String.format("%.3f", po.linkedPaidAmount)}", fontSize = 12.sp, color = Color(0xFF4CAF50))
                        }
                        if (po.referenceNumbers.isNotEmpty()) {
                            Text("أرقام المرجع: ${po.referenceNumbers.joinToString(", ")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            }

            if (item.supplier.yearlyTarget > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("الهدف السنوي: JD ${String.format("%.3f", item.supplier.yearlyTarget)}", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(
                    progress = item.targetProgress.toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (item.targetProgress >= 1.0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
                Text("${(item.targetProgress * 100).toInt()}% محقق", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    state: DateRangePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text("موافق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f).padding(16.dp)
        )
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
