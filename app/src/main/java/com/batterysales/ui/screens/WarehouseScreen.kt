package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.viewmodel.WarehouseViewModel
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseScreen(navController: NavController, viewModel: WarehouseViewModel = hiltViewModel()) {
    val warehouses by viewModel.warehouses.collectAsState()
    val stockLevels by viewModel.stockLevels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Stock, 1: Manage
    var warehouseToDelete by remember { mutableStateOf<com.batterysales.data.models.Warehouse?>(null) }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surface
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

    Scaffold(
        containerColor = bgColor
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Gradient Header
            item {
                SharedHeader(
                    title = if (selectedTab == 0) "مخزون المستودعات" else "إدارة المستودعات",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Default.Refresh,
                            onClick = { /* Refresh handled by flow */ },
                            contentDescription = "Refresh"
                        )
                    }
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = accentColor,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = accentColor
                            )
                        },
                        divider = {}
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                            Text("المخزون", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = if(selectedTab == 0) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                            Text("الإدارة", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = if(selectedTab == 1) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (selectedTab == 0) {
            if (isLoading && stockLevels.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else {
                // Group items by warehouse
                stockLevels.groupBy { it.warehouse.name }.forEach { (warehouseName, items) ->
                    // Warehouse Header
                    item {
                        Text(
                            text = warehouseName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    // Stock Items in this warehouse
                    items(items) { stockItem ->
                        val threshold = stockItem.variant.minQuantities[stockItem.warehouse.id] ?: stockItem.variant.minQuantity
                        val isLowStock = threshold > 0 && stockItem.quantity <= threshold
                        val lowStockColor = Color(0xFFEF4444)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLowStock) lowStockColor.copy(alpha = 0.05f) else cardBgColor
                            ),
                            border = if (isLowStock) androidx.compose.foundation.BorderStroke(1.dp, lowStockColor.copy(alpha = 0.2f)) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${stockItem.product.name} - ${stockItem.variant.capacity}A",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (stockItem.variant.specification.isNotEmpty()) {
                                        Text(
                                            text = stockItem.variant.specification,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    if (threshold > 0) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            color = if (isLowStock) lowStockColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "تنبيه الحد الأدنى: $threshold",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                color = if (isLowStock) lowStockColor else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stockItem.quantity.toString(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLowStock) lowStockColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isLowStock) {
                                        Text(
                                            text = "مخزون منخفض",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = lowStockColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } else {
                // Manage Warehouses Tab
                items(warehouses) { warehouse ->
                    WarehouseManagementCard(
                        warehouse = warehouse,
                        onToggleStatus = { viewModel.toggleWarehouseStatus(warehouse) },
                        onDelete = { warehouseToDelete = warehouse }
                    )
                }

                if (warehouses.isEmpty() && !isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد مستودعات حالياً", color = Color.Gray)
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (warehouseToDelete != null) {
        AlertDialog(
            onDismissRequest = { warehouseToDelete = null },
            title = { Text("حذف المستودع") },
            text = { Text("هل أنت متأكد من حذف مستودع '${warehouseToDelete!!.name}'؟ سيؤدي هذا إلى حذف بيانات المستودع ولا يمكن التراجع عنه.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWarehouse(warehouseToDelete!!.id)
                        warehouseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { warehouseToDelete = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun WarehouseManagementCard(
    warehouse: com.batterysales.data.models.Warehouse,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = warehouse.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (warehouse.location.isNotEmpty()) {
                    Text(
                        text = warehouse.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = if (warehouse.isActive) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (warehouse.isActive) "نشط" else "متوقف / معلق",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (warehouse.isActive) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onToggleStatus,
                    modifier = Modifier.size(40.dp).background(if (warehouse.isActive) Color(0xFFEF4444).copy(alpha = 0.1f) else Color(0xFF10B981).copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (warehouse.isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Status",
                        tint = if (warehouse.isActive) Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp).background(Color(0xFF3B1F1F), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                }
            }
        }
    }
}
