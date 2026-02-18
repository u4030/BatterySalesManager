package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.batterysales.ui.components.InfoBadge
import com.batterysales.viewmodel.LedgerCategory
import com.batterysales.viewmodel.LedgerItem
import com.batterysales.viewmodel.ProductLedgerViewModel
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton
import com.batterysales.ui.components.CustomKeyboardTextField
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.navigation.NavHostController
import com.batterysales.ui.components.TabItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductLedgerScreen(
    navController: NavHostController,
    viewModel: ProductLedgerViewModel = hiltViewModel()
) {
    val ledgerItems by viewModel.ledgerItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isLastPage by viewModel.isLastPage.collectAsState()
    val listState = rememberLazyListState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val isAdmin = userRole == "admin"
    var showDeleteConfirmation by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load more when reaching the end
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && !isLoadingMore && !isLastPage) {
            viewModel.loadData()
        }
    }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                com.batterysales.ui.components.BarcodeScanner(onBarcodeScanned = { barcode ->
                    scope.launch {
                        val variant = viewModel.findVariantByBarcode(barcode)
                        if (variant != null) {
                            val productName = viewModel.getProductName(variant.productId) ?: "سجل المنتج"
                            val capacityStr = variant.capacity.toString()
                            val spec = variant.specification.ifEmpty { "no_spec" }
                            navController.navigate("product_ledger/${variant.id}/$productName/$capacityStr/$spec") {
                                popUpTo("product_ledger") { inclusive = true }
                            }
                        } else {
                            snackbarHostState.showSnackbar("لم يتم العثور على منتج بهذا الباركود")
                        }
                    }
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

    if (showDeleteConfirmation != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد أنك تريد حذف هذا القيد؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmation?.let { viewModel.deleteStockEntry(it) }
                    showDeleteConfirmation = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) { Text("إلغاء") }
            }
        )
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    Scaffold(
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header Section
            item {
                SharedHeader(
                    title = "سجل حركة المنتج",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Default.PhotoCamera,
                            onClick = { showScanner = true },
                            contentDescription = "Scan"
                        )
                    }
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${viewModel.productName} - ${viewModel.variantCapacity}A" +
                                if (viewModel.variantSpecification.isNotEmpty()) " (${viewModel.variantSpecification})" else "",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search Bar
                    CustomKeyboardTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = "بحث في السجل..."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Styled Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        LedgerCategory.values().forEach { category ->
                            TabItem(
                                title = category.label,
                                isSelected = selectedCategory == category,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                onClick = { viewModel.selectCategory(category) }
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
                items(ledgerItems, key = { it.entry.id }) { item ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LedgerItemCard(
                            item = item,
                            isAdmin = isAdmin,
                            productName = viewModel.productName,
                            variantCapacity = viewModel.variantCapacity,
                            variantSpecification = viewModel.variantSpecification,
                            onEdit = { entryId ->
                                if (item.entry.supplier != "Sale") {
                                    navController.navigate("stock_entry?entryId=$entryId")
                                }
                            },
                            onDelete = { entryId ->
                                if (item.entry.supplier != "Sale") {
                                    showDeleteConfirmation = entryId
                                }
                            }
                        )
                    }
                }
            }

            if (isLoadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accentColor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerItemCard(
    item: LedgerItem,
    isAdmin: Boolean,
    productName: String,
    variantCapacity: String,
    variantSpecification: String,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val entry = item.entry
    var menuExpanded by remember { mutableStateOf(false) }
    val isSale = entry.supplier == "Sale"
    val isTransfer = entry.costPrice == 0.0

    val quantityColor = when {
        entry.quantity > 0 -> Color(0xFF10B981)
        else -> Color(0xFFEF4444)
    }
    val typeText = when {
        isSale -> "عملية بيع"
        isTransfer && entry.quantity < 0 -> "ترحيل مخزون (إخراج)"
        isTransfer && entry.quantity > 0 -> "ترحيل مخزون (إدخال)"
        else -> "عملية شراء"
    }
    val accentColor = Color(0xFFFB8C00)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = typeText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.timestamp.toFormattedString("yyyy-MM-dd HH:mm"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.status == "pending") {
                        Surface(
                            color = Color(0xFF2E1505),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "معلق",
                                color = accentColor,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (!isSale && !isTransfer && isAdmin) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "خيارات", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("تعديل", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        onEdit(entry.id)
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("حذف", color = Color.Red) },
                                    onClick = {
                                        onDelete(entry.id)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBadge(
                    label = "الكمية",
                    value = entry.quantity.toString(),
                    color = quantityColor
                )
                InfoBadge(
                    label = "المستودع",
                    value = item.warehouseName,
                    color = accentColor
                )
                if (entry.supplier.isNotEmpty() && !isSale) {
                     InfoBadge(
                        label = "المورد",
                        value = entry.supplier,
                        color = Color(0xFF3B82F6)
                    )
                }
                if (entry.invoiceNumber.isNotEmpty()) {
                    InfoBadge(
                        label = "رقم الفاتورة/المرجع",
                        value = entry.invoiceNumber,
                        color = Color(0xFF8B5CF6)
                    )
                }
            }
            if (entry.returnedQuantity > 0) {
                val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                val returnDateStr = entry.returnDate?.let { dateFormatter.format(it) } ?: "غير معروف"
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "تم إرجاع ${entry.returnedQuantity} حبة بتاريخ: $returnDateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
            if (entry.createdByUserName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "بواسطة: ${entry.createdByUserName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun InfoColumnLedger(label: String, value: String, valueColor: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

fun formatPrice(price: Double): String {
    return if (price != 0.0) "JD " + String.format(Locale.US, "%.4f", price) else "-"
}

fun Date.toFormattedString(format: String): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(this)
}
