package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.data.models.Bill
import com.batterysales.data.models.BillStatus
import com.batterysales.viewmodel.BillViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    navController: NavHostController,
    viewModel: BillViewModel = hiltViewModel()
) {
    val bills by viewModel.bills.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddBillDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الكمبيالات والشيكات", fontWeight = FontWeight.Bold) },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBillDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة كمبيالة", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (bills.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد كمبيالات أو شيكات مسجلة", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(bills) { bill ->
                        BillItemCard(
                            bill = bill,
                            onPayClick = { viewModel.markAsPaid(bill.id) },
                            onDeleteClick = { viewModel.deleteBill(bill.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddBillDialog) {
        AddBillDialog(
            onDismiss = { showAddBillDialog = false },
            onAdd = { desc, amount, date ->
                viewModel.addBill(desc, amount, date)
                showAddBillDialog = false
            }
        )
    }
}

@Composable
fun BillItemCard(bill: Bill, onPayClick: () -> Unit, onDeleteClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val isPaid = bill.status == BillStatus.PAID
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(bill.description, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Surface(
                    color = (if (isPaid) Color(0xFF4CAF50) else Color(0xFFFF9800)).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isPaid) "مسددة" else "غير مسددة",
                        color = if (isPaid) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("المبلغ: SR ${String.format("%.2f", bill.amount)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Text("تاريخ الاستحقاق: ${dateFormatter.format(bill.dueDate)}", fontSize = 12.sp, color = Color.Gray)
                }
                
                Row {
                    if (!isPaid) {
                        IconButton(onClick = onPayClick) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "تسديد", tint = Color(0xFF4CAF50))
                        }
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFF44336))
                    }
                }
            }
        }
    }
}

@Composable
fun AddBillDialog(onDismiss: () -> Unit, onAdd: (String, Double, Date) -> Unit) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val dueDate = Date() 

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة كمبيالة/شيك جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("تاريخ الاستحقاق: سيتم تعيينه لليوم", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = { 
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (description.isNotEmpty() && amt > 0) onAdd(description, amt, dueDate)
            }) {
                Text("إضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
