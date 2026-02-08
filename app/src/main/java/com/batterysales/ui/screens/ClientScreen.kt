package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.navigation.NavHostController
import com.batterysales.data.models.Client
import com.batterysales.viewmodel.ClientViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(
    navController: NavHostController,
    viewModel: ClientViewModel = hiltViewModel()
) {
    val clients by viewModel.clients.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة العملاء", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "إضافة عميل")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (clients.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "لا يوجد عملاء مسجلين حالياً",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(clients) { client ->
                        ClientItemCard(client) {
                            viewModel.deleteClient(client.id)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة عميل جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotEmpty()) {
                        viewModel.addClient(name, phone, email, address)
                        showAddDialog = false
                        name = ""; phone = ""; email = ""; address = ""
                    }
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun ClientItemCard(client: Client, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(client.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(client.phone, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
