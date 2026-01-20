package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.model.StockEntry
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(
                    "${viewModel.productName} - ${viewModel.variantCapacity} أمبير",
                    color = Color.White
                ) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
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
                    LedgerItemCard(item = item)
                }
            }
        }
    }
}

@Composable
fun LedgerItemCard(item: LedgerItem) {
    val entry = item.entry
    val isPositive = entry.quantity > 0
    val quantityColor = if (isPositive) Color(0xFF0A842D) else Color(0xFFD32F2F)
    val typeText = when {
        entry.supplier == "Sale" -> "بيع"
        entry.costPrice == 0.0 && entry.quantity < 0 -> "نقل مخزون (إخراج)"
        entry.costPrice == 0.0 && entry.quantity > 0 -> "نقل مخزون (إدخال)"
        else -> "شراء"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.timestamp.toFormattedString("yyyy-MM-dd"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Card Body
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoColumnLedger(label = "الكمية", value = entry.quantity.toString(), valueColor = quantityColor)
                InfoColumnLedger(label = "سعر القطعة", value = formatPrice(entry.costPrice))
                InfoColumnLedger(label = "الإجمالي", value = formatPrice(entry.totalCost))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer
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
                if (entry.supplier.isNotEmpty() && entry.supplier != "Sale") {
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
fun InfoColumnLedger(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

private fun formatPrice(price: Double): String {
    return if (price > 0) String.format(Locale.US, "%.2f", price) else "-"
}


private fun Date.toFormattedString(format: String): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(this)
}
