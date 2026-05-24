package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.data.models.Warehouse
import com.batterysales.data.models.ScrapWarehouse
import com.batterysales.data.models.SupplierSummaryItem
import com.batterysales.ui.components.BarcodeScanner
import com.batterysales.ui.components.InfoBadge
import com.batterysales.viewmodel.ReportsViewModel
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton
import java.util.Date
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import com.batterysales.ui.components.TabItem
import com.batterysales.ui.components.CustomKeyboardTextField
import com.batterysales.ui.components.KeyboardLanguage
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val keyboardController = com.batterysales.ui.components.LocalCustomKeyboardController.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val inventoryItems by viewModel.inventoryReportItems.collectAsState(initial = emptyList<com.batterysales.data.models.InventoryReportItem>())
    val grandTotalQuantity by viewModel.grandTotalInventoryQuantity.collectAsState()
    val supplierOverviewList by viewModel.suppliersOverviewList.collectAsState()
    val isSeller by viewModel.isSeller.collectAsState()
    val warehouseList by viewModel.filteredWarehouses.collectAsState(initial = emptyList<Warehouse>())
    val oldBatterySummary by viewModel.oldBatterySummary.collectAsState()
    val scrapWarehouses by viewModel.scrapWarehouses.collectAsState()
    val isInventoryLoading by viewModel.isInventoryLoading.collectAsState()
    val isSupplierLoading by viewModel.isSupplierLoading.collectAsState()
    val isScrapLoading by viewModel.isScrapLoading.collectAsState()
    val barcodeFilter by viewModel.barcodeFilter.collectAsState()
    var showScanner by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val allItemNames by viewModel.allInventoryItemNames.collectAsState()


    val bgColor = MaterialTheme.colorScheme.background
    val accentColor = Color(0xFFFB8C00)

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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    SharedHeader(
                        title = "التقارير والإحصائيات",
                        onBackClick = {
                            keyboardController.hideKeyboard()
                            navController.popBackStack()
                        },
                        actions = {
                            HeaderIconButton(
                                icon = Icons.Default.Refresh,
                                onClick = { viewModel.refreshAll() },
                                contentDescription = "Refresh"
                            )
                        }
                    )

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(4.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            TabItem(title = "المخزون", isSelected = selectedTab == 0, onClick = { viewModel.onTabSelected(0) }, modifier = Modifier.padding(horizontal = 4.dp))
                            TabItem(title = "السكراب", isSelected = selectedTab == 1, onClick = { viewModel.onTabSelected(1) }, modifier = Modifier.padding(horizontal = 4.dp))
                            if (!isSeller) {
                                TabItem(title = "الموردين", isSelected = selectedTab == 2, onClick = { viewModel.onTabSelected(2) }, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }
                }

                val currentTabLoading = when(selectedTab) {
                    0 -> isInventoryLoading
                    1 -> isScrapLoading
                    2 -> isSupplierLoading && supplierOverviewList.isEmpty()
                    else -> false
                }

                if (currentTabLoading && (selectedTab != 0 || inventoryItems.isEmpty())) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> {
                            item { InventoryReportControls(viewModel) }

                            if (inventoryItems.isNotEmpty()) {
                                item { GrandTotalCard(totalQuantity = grandTotalQuantity, isSeller = isSeller) }
                            }

                            if (inventoryItems.isEmpty() && !currentTabLoading) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("لا يوجد بيانات في المخزون حالياً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            items(inventoryItems) { reportItem ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    ReportItemCard(
                                        item = reportItem,
                                        warehouses = warehouseList,
                                        isSeller = isSeller,
                                        onClick = {
                                            val capacityStr = reportItem.variant.capacity.toString()
                                            val productName = reportItem.product.name
                                            val spec = reportItem.variant.specification.ifEmpty { "no_spec" }
                                            navController.navigate("product_ledger/${reportItem.variant.id}/$productName/$capacityStr/$spec")
                                        }
                                    )
                                }
                            }
                        }
                        1 -> {
                            item {
                                OldBatteryReportSectionRedesigned(
                                    scrapWarehouses = scrapWarehouses,
                                    oldBatterySummary = oldBatterySummary,
                                    onNavigate = { navController.navigate("old_battery_ledger") }
                                )
                            }
                        }
                        2 -> {
                            if (!isSeller) {
                                supplierReportSectionRedesigned(this, viewModel, supplierOverviewList, navController)
                            }
                        }
                    }
                }
            }

            if (selectedTab == 0 && inventoryItems.isNotEmpty()) {
                com.batterysales.ui.components.SidebarAlphabetNavigation(
                    onLetterSelected = { letter ->
                        val index = inventoryItems.indexOfFirst {
                            it.product.name.trim().startsWith(letter.toString(), ignoreCase = true)
                        }
                        if (index != -1) {
                            // Offsets: 0: Header, 1: Controls, 2: GrandTotalCard
                            val offset = 3
                            scope.launch { listState.animateScrollToItem(index + offset) }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 150.dp, bottom = 10.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryReportControls(viewModel: ReportsViewModel) {
    val keyboardController = com.batterysales.ui.components.LocalCustomKeyboardController.current
    val barcodeFilter by viewModel.barcodeFilter.collectAsState()
    val startDate by viewModel.inventoryStartDate.collectAsState()
    val endDate by viewModel.inventoryEndDate.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    val dateFormatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())

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
                IconButton(onClick = { showScanner = false }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
        }
    }

    var searchInput by remember { mutableStateOf("") }
    LaunchedEffect(barcodeFilter) { if (barcodeFilter != searchInput) searchInput = barcodeFilter ?: "" }

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                CustomKeyboardTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "بحث بالاسم أو الباركود...",
                    onSearch = { keyboardController.hideKeyboard(); viewModel.onBarcodeScanned(searchInput.ifEmpty { null }) }
                )
            }

            IconButton(
                onClick = { keyboardController.hideKeyboard(); viewModel.onBarcodeScanned(searchInput.ifEmpty { null }) },
                modifier = Modifier.size(56.dp).background(Color(0xFFFB8C00), RoundedCornerShape(12.dp))
            ) { Icon(Icons.Default.Search, contentDescription = "بحث", tint = Color.White) }

            IconButton(
                onClick = { showScanner = true },
                modifier = Modifier.size(56.dp).background(Color(0xFFFB8C00), RoundedCornerShape(12.dp))
            ) { Icon(Icons.Default.PhotoCamera, contentDescription = "مسح الباركود", tint = Color.White) }
        }

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
                    Text("المواد التي طرأ عليها تعديل خلال فترة:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onConfirm = { viewModel.onInventoryDateRangeSelected(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis); showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrandTotalCard(totalQuantity: Int, isSeller: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        FlowRow(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "إجمالي كمية المخزون", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(text = totalQuantity.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportItemCard(item: com.batterysales.data.models.InventoryReportItem, warehouses: List<com.batterysales.data.models.Warehouse>, isSeller: Boolean, onClick: () -> Unit) {
    var anyWarehouseLowStock = false
    item.warehouseQuantities.forEach { entry ->
        val whId = entry.key
        val qty = entry.value
        val threshold = item.variant.minQuantities[whId] ?: item.variant.minQuantity
        if (threshold > 0 && qty <= threshold) anyWarehouseLowStock = true
    }
    val isLowStock = anyWarehouseLowStock || (item.variant.minQuantity > 0 && item.totalQuantity <= item.variant.minQuantity)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "\u200F${item.product.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "\u200E${item.variant.capacity} A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.variant.specification.isNotEmpty()) {
                            Text(text = " | ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Text(text = item.variant.specification, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (isLowStock) {
                    Surface(color = Color(0xFFEF4444).copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                        Text(text = "مخزون منخفض", color = Color(0xFFEF4444), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBadge(label = "إجمالي الكمية", value = item.totalQuantity.toString(), color = if (isLowStock) Color(0xFFEF4444) else Color(0xFF10B981))
                if (!isSeller) {
                    InfoBadge(label = "متوسط التكلفة", value = "JD ${String.format("%.3f", item.averageCost)}", color = Color(0xFFFB8C00))
                    InfoBadge(label = "قيمة المخزون", value = "JD ${String.format("%.3f", item.totalCostValue)}", color = Color(0xFF3B82F6))
                }
            }
        }
    }
}

@Composable
fun OldBatteryReportSectionRedesigned(scrapWarehouses: List<ScrapWarehouse>, oldBatterySummary: Pair<Int, Double>, onNavigate: () -> Unit) {
    var selectedWHIndex by remember { mutableIntStateOf(0) }
    val currentSummary = if (selectedWHIndex == 0) oldBatterySummary else {
        val scrapWh = scrapWarehouses[selectedWHIndex - 1]
        Pair(scrapWh.totalQuantity, scrapWh.totalAmperes)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (scrapWarehouses.size > 1) {
            androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = selectedWHIndex == 0, onClick = { selectedWHIndex = 0 }, label = { Text("الكل") }) }
                items(scrapWarehouses.size) { index -> FilterChip(selected = selectedWHIndex == index + 1, onClick = { selectedWHIndex = index + 1 }, label = { Text(scrapWarehouses[index].name.removePrefix("سكراب - ")) }) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF5D4037).copy(alpha = 0.1f))) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = Color(0xFF5D4037).copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color(0xFFD7CCC8), modifier = Modifier.size(24.dp)) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("إجمالي مخزون السكراب", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("الكمية", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${currentSummary.first}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("الأمبيرات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("\u200E${String.format("%.1f", currentSummary.second)} A", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFB8C00)) }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)), shape = RoundedCornerShape(16.dp)) { Text("إدارة وسجل البطاريات القديمة", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun supplierReportSectionRedesigned(scope: androidx.compose.foundation.lazy.LazyListScope, viewModel: ReportsViewModel, supplierItems: List<SupplierSummaryItem>, navController: NavController) {
    scope.item { SupplierReportControls(viewModel) }
    val totalSuppliersDebt = supplierItems.sumOf { it.currentBalance }
    scope.item {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))) {
            FlowRow(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center) {
                Text("إجمالي مستحقات الموردين", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                Text("JD ${String.format("%.3f", totalSuppliersDebt)}", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
    if (supplierItems.isEmpty()) { scope.item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("لا يوجد موردين حالياً", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    else { scope.items(supplierItems) { item -> Box(modifier = Modifier.padding(horizontal = 16.dp)) { SupplierSummaryCard(item, onClick = { navController.navigate("supplier_details/${item.supplierId}") }) } } }
}

@Composable
fun SupplierSummaryCard(item: SupplierSummaryItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) { Text("مدين (مشتريات)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("JD ${String.format("%.3f", item.totalDebit)}", fontWeight = FontWeight.Bold, color = Color(0xFFFB8C00)) }
                Column(modifier = Modifier.weight(1f)) { Text("دائن (مسدد)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("JD ${String.format("%.3f", item.totalCredit)}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981)) }
                Column(modifier = Modifier.weight(1f)) { Text("المتبقي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("JD ${String.format("%.3f", item.currentBalance)}", fontWeight = FontWeight.ExtraBold, color = if (item.currentBalance > 0) Color(0xFFEF4444) else Color(0xFF10B981)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierReportControls(viewModel: ReportsViewModel) {
    val searchQuery by viewModel.supplierSearchQuery.collectAsState()
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CustomKeyboardTextField(value = searchQuery, onValueChange = { viewModel.onSupplierSearchQueryChanged(it) }, label = "بحث باسم المورد...", modifier = Modifier.fillMaxWidth(), onSearch = { })
    }
}

@Composable
fun PurchaseOrderCard(po: com.batterysales.data.models.PurchaseOrderItem, dateFormatter: java.text.SimpleDateFormat, navController: NavController) {
    var expanded by remember { mutableStateOf(false) }
    val isFullyCovered = po.remainingBalance <= 0.001

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Row: Date & Invoice Tag
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateFormatter.format(po.entry.getEffectiveDate()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (po.entry.invoiceNumber.isNotEmpty()) {
                    Surface(color = Color(0xFFFB8C00).copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = "فاتورة: ${po.entry.invoiceNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFB8C00),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Financial Summary Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("إجمالي الطلبية:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("JD ${String.format("%,.3f", po.entry.totalCost)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("المتبقي:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "JD ${String.format("%,.3f", po.remainingBalance)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isFullyCovered) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }

            // Coverage Status Badge (Image match)
            val coverageAmount = po.entry.totalCost - po.remainingBalance
            if (coverageAmount > 0.001) {
                Surface(
                    color = (if (isFullyCovered) Color(0xFF10B981) else Color(0xFFFB8C00)).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isFullyCovered) Color(0xFF10B981) else Color(0xFFFB8C00),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFullyCovered) "مغطاة بالكامل من شيكات غير مرتبطة"
                                   else "من شيكات مرتبطة جزئياً بمبلغ JD ${String.format("%.3f", coverageAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFullyCovered) Color(0xFF10B981) else Color(0xFFFB8C00),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Settlement Traceability Notes (Image match)
            if (po.referenceNumbers.isNotEmpty() || po.entry.settlementNotes.isNotEmpty()) {
                val allNotes = (po.referenceNumbers + po.entry.settlementNotes).distinct()
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(
                        Icons.Default.Payments,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = allNotes.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )
                }
            }

            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally).size(24.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.alpha(0.1f))
                Text("الأصناف:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                po.items.forEach { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            navController.navigate("product_ledger/${entry.productVariantId}/${entry.productName}/${entry.capacity}/no_spec")
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.productName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("${entry.capacity}A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("الكمية: ${entry.quantity}", style = MaterialTheme.typography.bodySmall)
                                Text("JD ${String.format("%.3f", entry.totalCost)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
