package com.batterysales.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }

    if (showCreateDialog) {
        CreateUserDialog(
            warehouses = uiState.warehouses,
            onDismiss = { showCreateDialog = false },
            onConfirm = { email, pass, name, role, whId ->
                viewModel.createUser(email, pass, name, role, whId)
                showCreateDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مستخدم")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
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
                items(uiState.users) { user ->
                    UserCard(
                        user = user,
                        warehouses = uiState.warehouses,
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

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

@Composable
fun CreateUserDialog(
    warehouses: List<com.batterysales.data.models.Warehouse>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String?) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("seller") }
    var selectedWarehouseId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء مستخدم جديد") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("الاسم الكامل") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("كلمة المرور") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("الدور:", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == "admin", onClick = { role = "admin" })
                        Text("مدير")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = role == "seller", onClick = { role = "seller" })
                        Text("بائع")
                    }
                    if (role == "seller") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("المستودع المرتبط:", fontWeight = FontWeight.Bold)
                        warehouses.forEach { warehouse ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedWarehouseId = warehouse.id }) {
                                RadioButton(selected = selectedWarehouseId == warehouse.id, onClick = { selectedWarehouseId = warehouse.id })
                                Text(warehouse.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(email, password, displayName, role, if (role == "seller") selectedWarehouseId else null) }) {
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
