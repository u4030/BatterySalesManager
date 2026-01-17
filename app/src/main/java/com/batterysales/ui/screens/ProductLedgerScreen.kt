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
                        Text("التاريخ", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                        Text("الكمية", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("التكلفة", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("المورد", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                        Text("المستودع", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                    }
                    Divider()
                }

                // Ledger Items
                items(ledgerItems) { item ->
                    val entry = item.entry
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(entry.timestamp.toFormattedString(), modifier = Modifier.weight(1.5f), fontSize = 14.sp)
                        Text(entry.quantity.toString(), modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(String.format("%.2f", entry.costPrice), modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(entry.supplier.ifEmpty { "-" }, modifier = Modifier.weight(1.5f), fontSize = 14.sp)
                        Text(item.warehouseName, modifier = Modifier.weight(1.5f), fontSize = 14.sp)
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
