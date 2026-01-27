package com.batterysales.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import com.batterysales.data.models.User
import com.batterysales.viewmodel.UserManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    navController: NavHostController,
    viewModel: UserManagementViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsState()
    val warehouses by viewModel.warehouses.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة المستخدمين", fontWeight = FontWeight.Bold) },
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(users) { user ->
                    UserCard(
                        user = user,
                        warehouses = warehouses,
                        onRoleChange = { role -> viewModel.updateUserRole(user, role) },
                        onWarehouseChange = { warehouseId -> viewModel.linkUserToWarehouse(user, warehouseId) }
                    )
                }
            }
        }
    }
}

@Composable
fun UserCard(
    user: User,
    warehouses: List<com.batterysales.data.models.Warehouse>,
    onRoleChange: (String) -> Unit,
    onWarehouseChange: (String?) -> Unit
) {
    var showRoleDialog by remember { mutableStateOf(false) }
    var showWarehouseDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = user.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text(text = user.email, color = Color.Gray, fontSize = 14.sp)

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("الدور:", fontSize = 12.sp, color = Color.Gray)
                    Text(text = if (user.role == "admin") "مدير" else "بائع", fontWeight = FontWeight.Medium)
                }
                Button(onClick = { showRoleDialog = true }, shape = RoundedCornerShape(8.dp)) {
                    Text("تغيير الدور")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("المستودع المرتبط:", fontSize = 12.sp, color = Color.Gray)
                    val warehouseName = warehouses.find { it.id == user.warehouseId }?.name ?: "غير مرتبط"
                    Text(text = warehouseName, fontWeight = FontWeight.Medium)
                }
                Button(onClick = { showWarehouseDialog = true }, shape = RoundedCornerShape(8.dp), enabled = user.role == "seller") {
                    Text("ربط مستودع")
                }
            }
        }
    }

    if (showRoleDialog) {
        AlertDialog(
            onDismissRequest = { showRoleDialog = false },
            title = { Text("تغيير دور المستخدم") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onRoleChange("admin"); showRoleDialog = false }) {
                        RadioButton(selected = user.role == "admin", onClick = { onRoleChange("admin"); showRoleDialog = false })
                        Text("مدير")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onRoleChange("seller"); showRoleDialog = false }) {
                        RadioButton(selected = user.role == "seller", onClick = { onRoleChange("seller"); showRoleDialog = false })
                        Text("بائع")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showRoleDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showWarehouseDialog) {
        AlertDialog(
            onDismissRequest = { showWarehouseDialog = false },
            title = { Text("ربط بمستودع") },
            text = {
                LazyColumn {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onWarehouseChange(null); showWarehouseDialog = false }) {
                            RadioButton(selected = user.warehouseId == null, onClick = { onWarehouseChange(null); showWarehouseDialog = false })
                            Text("إلغاء الربط")
                        }
                    }
                    items(warehouses) { warehouse ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onWarehouseChange(warehouse.id); showWarehouseDialog = false }) {
                            RadioButton(selected = user.warehouseId == warehouse.id, onClick = { onWarehouseChange(warehouse.id); showWarehouseDialog = false })
                            Text(warehouse.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showWarehouseDialog = false }) { Text("إلغاء") } }
        )
    }
}
