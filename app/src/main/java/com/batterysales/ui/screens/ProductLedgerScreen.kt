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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
                title = { Text("${viewModel.productName} - ${viewModel.variantCapacity} أمبير", color = Color.White) },
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
            CircularProgressIndicator(modifier = Modifier.fillMaxSize().wrapContentSize())
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("التاريخ", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                        Text("الكمية", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold)
                        Text("سعر القطعة", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("الإجمالي", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("المورد/السبب", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                        Text("المستودع", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    Divider()
                }

                // Ledger Items
                items(ledgerItems) { item ->
                    val entry = item.entry
                    val quantityColor = if (entry.quantity > 0) Color(0xFF008000) else Color.Red
                    val costDisplay = if(entry.costPrice > 0) String.format("%.2f", entry.costPrice) else "-"
                    val totalCostDisplay = if(entry.totalCost > 0) String.format("%.2f", entry.totalCost) else if (entry.quantity < 0) String.format("%.2f", entry.costPrice) else "-"


                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.timestamp.toFormattedString(), modifier = Modifier.weight(1.2f), fontSize = 14.sp)
                        Text(entry.quantity.toString(), modifier = Modifier.weight(0.6f), fontSize = 14.sp, color = quantityColor)
                        Text(costDisplay, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(totalCostDisplay, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(entry.supplier.ifEmpty { "-" }, modifier = Modifier.weight(1.2f), fontSize = 14.sp)
                        Text(item.warehouseName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    }
                    Divider()
                }
            }
        }
    }
}

private fun Date.toFormattedString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(this)
}
