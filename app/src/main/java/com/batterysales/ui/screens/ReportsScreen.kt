package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.batterysales.ui.components.TabItem
import com.batterysales.ui.components.CustomKeyboardTextField
import com.batterysales.ui.components.KeyboardLanguage

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
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
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
                SharedHeader(
                    title = "التقارير والإحصائيات",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Default.Refresh,
                            onClick = { /* Reload logic */ },
                            contentDescription = "Refresh"
                        )
                    }
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    // Styled Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        TabItem(
                            title = "المخزون",
                            isSelected = selectedTab == 0,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            onClick = { selectedTab = 0 }
                        )
                        TabItem(
                            title = "السكراب",
                            isSelected = selectedTab == 1,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            onClick = { selectedTab = 1 }
                        )
                        if (!isSeller) {
                            TabItem(
                                title = "الموردين",
                                isSelected = selectedTab == 2,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                onClick = { selectedTab = 2 }
                            )
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
                                onValueChange = { viewModel.onBarcodeScanned(it.ifEmpty { null }) },
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


@Composable
fun SearchBarRedesigned(
    barcodeFilter: String?,
    onValueChange: (String) -> Unit,
    onScan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            CustomKeyboardTextField(
                value = barcodeFilter ?: "",
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = "تصفية حسب الباركود..."
            )
        }
        
        IconButton(
            onClick = onScan,
            modifier = Modifier
                .size(56.dp)
                .background(Color(0xFFFB8C00), RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrandTotalCard(totalQuantity: Int, isSeller: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        FlowRow(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
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
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("إدارة وسجل البطاريات القديمة", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "إجمالي مستحقات الموردين", 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    "JD ${String.format("%.3f", totalSuppliersDebit)}", 
                    fontWeight = FontWeight.Bold, 
                    color = Color(0xFFEF4444), 
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
        CustomKeyboardTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSupplierSearchQueryChanged(it) },
            label = "بحث باسم المورد أو رقم المرجع...",
            modifier = Modifier.fillMaxWidth(),
            onSearch = { /* Search is reactive, just hide keyboard */ }
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
        com.batterysales.ui.components.AppDateRangePickerDialog(
            state = dateRangePickerState,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.onDateRangeSelected(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun TargetProgressItem(label: String, target: Double, current: Double) {
    val progress = if (target > 0) current / target else 0.0
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("$label: JD ${String.format("%.3f", target)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (progress >= 1.0) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplierCardRedesigned(item: com.batterysales.viewmodel.SupplierReportItem) {
    var expanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.supplier.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { com.batterysales.utils.PrintUtils.printSupplierReport(context, item) },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "طباعة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { com.batterysales.utils.PrintUtils.shareSupplierReport(context, item) },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBadge(label = "مدين", value = "JD ${String.format("%.3f", item.totalDebit)}", color = Color(0xFFFB8C00))
                InfoBadge(label = "دائن", value = "JD ${String.format("%.3f", item.totalCredit)}", color = Color(0xFF10B981))
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dateFormatter.format(po.entry.timestamp), 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.Bold
                                )
                                if (po.entry.invoiceNumber.isNotEmpty()) {
                                    Surface(
                                        color = Color(0xFFFB8C00).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "فاتورة: ${po.entry.invoiceNumber}", 
                                            style = MaterialTheme.typography.labelSmall, 
                                            color = Color(0xFFFB8C00),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الطلبية:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("JD ${String.format("%.3f", po.entry.totalCost)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المتبقي بذمة المورد:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "JD ${String.format("%.3f", po.remainingBalance)}", 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.Bold,
                                    color = if (po.remainingBalance > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                                )
                            }

                            if (po.referenceNumbers.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.alpha(0.1f))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Default.Payments, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp), 
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ", " + po.referenceNumbers.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (item.supplier.yearlyTarget > 0 || item.supplier.yearlyTarget2 > 0 || item.supplier.yearlyTarget3 > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.05f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("تحقيق الأهداف السنوية:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                
                if (item.supplier.yearlyTarget > 0) {
                    TargetProgressItem("الهدف 1", item.supplier.yearlyTarget, item.totalDebit)
                }
                if (item.supplier.yearlyTarget2 > 0) {
                    TargetProgressItem("الهدف 2", item.supplier.yearlyTarget2, item.totalDebit)
                }
                if (item.supplier.yearlyTarget3 > 0) {
                    TargetProgressItem("الهدف 3", item.supplier.yearlyTarget3, item.totalDebit)
                }
            }
        }
    }
}
