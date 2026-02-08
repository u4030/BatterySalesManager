package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.viewmodel.LedgerCategory
import com.batterysales.viewmodel.LedgerItem
import com.batterysales.viewmodel.ProductLedgerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductLedgerScreen(
    navController: NavController,
    viewModel: ProductLedgerViewModel = hiltViewModel()
) {
    val ledgerItems by viewModel.ledgerItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val isAdmin = userRole == "admin"
    var showDeleteConfirmation by remember { mutableStateOf<String?>(null) }


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
                Button(onClick = { showDeleteConfirmation = null }) { Text("إلغاء") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(
                        "${viewModel.productName} - ${viewModel.variantCapacity} أمبير" +
                                if (viewModel.variantSpecification.isNotEmpty()) " (${viewModel.variantSpecification})" else "",
                        color = Color.White
                    ) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedCategory.ordinal,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedCategory.ordinal]),
                            color = Color.White
                        )
                    }
                ) {
                    LedgerCategory.values().forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            text = { Text(category.label, color = Color.White) }
                        )
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
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(ledgerItems) { item ->
                    LedgerItemCard(
                        item = item,
                        isAdmin = isAdmin,
                        productName = viewModel.productName,
                        variantCapacity = viewModel.variantCapacity,
                        variantSpecification = viewModel.variantSpecification,
                        onEdit = { entryId ->
                            // A sales entry is not a real stock entry, cannot be edited.
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
        entry.quantity > 0 -> Color(0xFF0A842D)
        else -> Color(0xFFD32F2F)
    }
    val typeText = when {
        isSale -> "بيع"
        isTransfer && entry.quantity < 0 -> "نقل مخزون (إخراج)"
        isTransfer && entry.quantity > 0 -> "نقل مخزون (إدخال)"
        else -> "شراء"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = typeText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (entry.status == "pending") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFFF9800).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "معلق",
                                color = Color(0xFFFF9800),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.timestamp.toFormattedString("yyyy-MM-dd HH:mm"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show menu only for editable/deletable entries (Admin only)
                    if (!isSale && !isTransfer && isAdmin) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "خيارات")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("تعديل") },
                                    onClick = {
                                        onEdit(entry.id)
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("حذف") },
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
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = 2
            ) {
                InfoColumnLedger(
                    label = "الكمية",
                    value = entry.quantity.toString(),
                    valueColor = quantityColor,
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp)
                )
                InfoColumnLedger(
                    label = "اسم المنتج",
                    value = productName,
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp)
                )
                InfoColumnLedger(
                    label = "السعة",
                    value = "$variantCapacity أمبير",
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp)
                )
                if (variantSpecification.isNotEmpty()) {
                    InfoColumnLedger(
                        label = "المواصفة",
                        value = variantSpecification,
                        modifier = Modifier.weight(1f).widthIn(min = 100.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المستودع: ${item.warehouseName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (entry.supplier.isNotEmpty() && !isSale) {
                    Text(
                        text = "المورد: ${entry.supplier}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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

private fun formatPrice(price: Double): String {
    return if (price != 0.0) "JD " + String.format(Locale.US, "%.4f", price) else "-"
}

private fun Date.toFormattedString(format: String): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(this)
}
