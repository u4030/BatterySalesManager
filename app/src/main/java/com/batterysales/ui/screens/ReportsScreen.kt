package com.batterysales.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.data.models.Warehouse
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.ui.components.InfoBadge
import com.batterysales.viewmodel.InventoryReportItem
import com.batterysales.viewmodel.ReportsViewModel
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.batterysales.ui.components.TabItem

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

    val bgColor = MaterialTheme.colorScheme.background
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    )

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
        containerColor = bgColor
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Gradient Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }

                            Text(
                                text = "التقارير والإحصائيات",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { /* Reload logic */ },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Styled Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(4.dp)
                        ) {
                            TabItem(
                                title = "المخزون",
                                isSelected = selectedTab == 0,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTab = 0 }
                            )
                            TabItem(
                                title = "السكراب",
                                isSelected = selectedTab == 1,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTab = 1 }
                            )
                            if (!isSeller) {
                                TabItem(
                                    title = "الموردين",
                                    isSelected = selectedTab == 2,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedTab = 2 }
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        item {
                            SearchBarRedesigned(
                                barcodeFilter = barcodeFilter,
                                onClear = { viewModel.onBarcodeScanned(null) },
                                onScan = { showScanner = true }
                            )
                        }

                        if (reportItems.isNotEmpty()) {
                            item {
                                GrandTotalCard(totalQuantity = grandTotalQuantity, isSeller = isSeller)
                            }
                        }

                        items(reportItems) { item ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                    }
                    1 -> {
                        item {
                            OldBatteryReportSectionRedesigned(
                                warehouses = warehouses,
                                oldBatterySummary = oldBatterySummary,
                                oldBatteryWarehouseSummary = oldBatteryWarehouseSummary,
                                onNavigate = { navController.navigate("old_battery_ledger") }
                            )
                        }
                    }
                    2 -> {
                        if (!isSeller) {
                            supplierReportSectionRedesigned(this, viewModel, supplierItems)
                        }
                    }
                }
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
fun SearchBarRedesigned(
    barcodeFilter: String?,
    onClear: () -> Unit,
    onScan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = barcodeFilter ?: "تصفية حسب الباركود...",
                    color = if (barcodeFilter == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (!barcodeFilter.isNullOrBlank()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح الفلتر", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        IconButton(
            onClick = onScan,
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFFFB8C00), RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود", tint = Color.White)
        }
    }
}

@Composable
fun GrandTotalCard(totalQuantity: Int, isSeller: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "إجمالي كمية المخزون",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = totalQuantity.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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
    val anyWarehouseLowStock = item.warehouseQuantities.any { (whId, qty) ->
        val threshold = item.variant.minQuantities[whId] ?: item.variant.minQuantity
        threshold > 0 && qty <= threshold
    }
    val isLowStock = anyWarehouseLowStock || (item.variant.minQuantity > 0 && item.totalQuantity <= item.variant.minQuantity)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.variant.capacity} أمبير" + if (item.variant.specification.isNotEmpty()) " | ${item.variant.specification}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isLowStock) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "مخزون منخفض",
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBadge(label = "إجمالي الكمية", value = item.totalQuantity.toString(), color = if (isLowStock) Color(0xFFEF4444) else Color(0xFF10B981))
                if (!isSeller) {
                    InfoBadge(label = "متوسط التكلفة", value = "JD ${String.format("%.3f", item.averageCost)}", color = Color(0xFFFB8C00))
                    InfoBadge(label = "قيمة المخزون", value = "JD ${String.format("%.3f", item.totalCostValue)}", color = Color(0xFF3B82F6))
                }
            }

            val quantitiesInWarehouses = warehouses.mapNotNull { warehouse ->
                val quantity = item.warehouseQuantities[warehouse.id]
                if (quantity != null && quantity != 0) {
                    val threshold = item.variant.minQuantities[warehouse.id] ?: item.variant.minQuantity
                    val isWHLow = threshold > 0 && quantity <= threshold
                    Triple(warehouse.name, quantity, isWHLow)
                } else null
            }

            if (quantitiesInWarehouses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.05f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("توزيع المستودعات:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quantitiesInWarehouses.forEach { (name, qty, isLow) ->
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "$name: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = qty.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (isLow) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OldBatteryReportSectionRedesigned(
    warehouses: List<Warehouse>,
    oldBatterySummary: Pair<Int, Double>,
    oldBatteryWarehouseSummary: Map<String, Pair<Int, Double>>,
    onNavigate: () -> Unit
) {
    var selectedWHIndex by remember { mutableIntStateOf(0) }
    val currentSummary = if (selectedWHIndex == 0) oldBatterySummary
                        else oldBatteryWarehouseSummary[warehouses[selectedWHIndex - 1].id] ?: Pair(0, 0.0)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (warehouses.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedWHIndex == 0,
                        onClick = { selectedWHIndex = 0 },
                        label = { Text("الكل") }
                    )
                }
                items(warehouses.size) { index ->
                    FilterChip(
                        selected = selectedWHIndex == index + 1,
                        onClick = { selectedWHIndex = index + 1 },
                        label = { Text(warehouses[index].name) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF5D4037).copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color(0xFF5D4037).copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color(0xFFD7CCC8), modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("إجمالي مخزون السكراب", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("الكمية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${currentSummary.first}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("الأمبيرات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.1f", currentSummary.second)} A", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFB8C00))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onNavigate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("إدارة وسجل البطاريات القديمة", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun supplierReportSectionRedesigned(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    viewModel: ReportsViewModel,
    items: List<com.batterysales.viewmodel.SupplierReportItem>
) {
    scope.item {
        SupplierReportControls(viewModel)
    }

    val totalSuppliersDebit = items.sumOf { it.balance }
    scope.item {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("إجمالي مستحقات الموردين", fontWeight = FontWeight.Bold)
                Text("JD ${String.format("%.3f", totalSuppliersDebit)}", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    scope.items(items) { item ->
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            SupplierCardRedesigned(item)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierReportControls(viewModel: ReportsViewModel) {
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()
    val searchQuery by viewModel.supplierSearchQuery.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSupplierSearchQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("بحث باسم المورد أو رقم المرجع...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        )

        Card(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("الفترة الزمنية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val startStr = startDate?.let { dateFormatter.format(java.util.Date(it)) } ?: "البداية"
                    val endStr = endDate?.let { dateFormatter.format(java.util.Date(it)) } ?: "النهاية"
                    Text("$startStr - $endStr", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
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
fun SupplierCardRedesigned(item: com.batterysales.viewmodel.SupplierReportItem) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.supplier.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBadge(label = "مشتريات", value = "JD ${String.format("%.3f", item.totalDebit)}", color = Color(0xFFFB8C00))
                InfoBadge(label = "دفعات", value = "JD ${String.format("%.3f", item.totalCredit)}", color = Color(0xFF10B981))
                InfoBadge(label = "الرصيد", value = "JD ${String.format("%.3f", item.balance)}", color = if (item.balance > 0) Color(0xFFEF4444) else Color(0xFF10B981))
            }

            if (expanded && item.purchaseOrders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.05f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("تفاصيل طلبيات الشراء:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                item.purchaseOrders.forEach { po ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateFormatter.format(po.entry.timestamp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("JD ${String.format("%.3f", po.entry.totalCost)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المتبقي:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("JD ${String.format("%.3f", po.remainingBalance)}", style = MaterialTheme.typography.labelSmall, color = if (po.remainingBalance > 0) Color(0xFFEF4444) else Color.Gray)
                        }
                        if (po.referenceNumbers.isNotEmpty()) {
                            Text("مراجع: ${po.referenceNumbers.joinToString(", ")}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            if (item.supplier.yearlyTarget > 0) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("الهدف السنوي: JD ${String.format("%.3f", item.supplier.yearlyTarget)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(item.targetProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = item.targetProgress.toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (item.targetProgress >= 1.0) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}
