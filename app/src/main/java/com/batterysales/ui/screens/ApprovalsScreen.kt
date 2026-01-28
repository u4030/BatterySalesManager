package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.viewmodel.ApprovalItem
import com.batterysales.viewmodel.ApprovalsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(
    navController: NavHostController,
    viewModel: ApprovalsViewModel = hiltViewModel()
) {
    val items by viewModel.approvalItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الموافقات المعلقة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد طلبات موافقة معلقة", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items) { item ->
                    ApprovalCard(
                        item = item,
                        onApprove = { viewModel.approveEntry(item.entry.id) },
                        onReject = { viewModel.rejectEntry(item.entry.id) },
                        onEdit = { navController.navigate("stock_entry?entryId=${item.entry.id}") }
                    )
                }
            }
        }
    }
}

@Composable
fun ApprovalCard(item: ApprovalItem, onApprove: () -> Unit, onReject: () -> Unit, onEdit: () -> Unit) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = item.productName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = if (item.entry.quantity > 0) "+${item.entry.quantity}" else "${item.entry.quantity}",
                    color = if (item.entry.quantity > 0) Color(0xFF4CAF50) else Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(text = "سعة: ${item.variantCapacity} أمبير", fontSize = 14.sp, color = Color.Gray)
            Text(text = "المستودع: ${item.warehouseName}", fontSize = 14.sp, color = Color.Gray)
            Text(text = "المورد/النوع: ${item.entry.supplier}", fontSize = 14.sp, color = Color.Gray)
            Text(text = "التاريخ: ${dateFormatter.format(item.entry.timestamp)}", fontSize = 12.sp, color = Color.Gray)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تعديل")
                }

                Row {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("رفض")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("موافقة")
                    }
                }
            }
        }
    }
}
