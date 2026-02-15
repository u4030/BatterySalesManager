package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.data.models.User
import com.batterysales.viewmodel.UserManagementViewModel
import com.batterysales.ui.components.SharedHeader
import com.batterysales.ui.components.HeaderIconButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserManagementScreen(
    navController: NavHostController,
    viewModel: UserManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val bgColor = MaterialTheme.colorScheme.background
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFFE53935), Color(0xFFFB8C00))
    )

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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = bgColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = accentColor,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مستخدم")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Gradient Header
            item {
                SharedHeader(
                    title = "إدارة المستخدمين",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        HeaderIconButton(
                            icon = Icons.Default.Refresh,
                            onClick = { /* Reload logic if available */ },
                            contentDescription = "Refresh"
                        )
                    }
                )
            }

            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else if (uiState.users.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("لا يوجد مستخدمين مضافين", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            } else {
                items(uiState.users) { user ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        UserCard(
                            user = user,
                            warehouses = uiState.warehouses,
                            onRoleChange = { role -> viewModel.updateUserRole(user, role) },
                            onWarehouseChange = { warehouseId -> viewModel.linkUserToWarehouse(user, warehouseId) },
                            onPermissionToggle = { permission -> viewModel.togglePermission(user, permission) },
                            onStatusToggle = { viewModel.toggleUserStatus(user) },
                            onDelete = { userToDelete = user }
                        )
                    }
                }
            }
        }
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("حذف المستخدم") },
            text = { Text("هل أنت متأكد من حذف المستخدم '${userToDelete?.displayName}'؟ لن يتمكن من تسجيل الدخول وسيمسح ملفه الشخصي.") },
            confirmButton = {
                Button(
                    onClick = {
                        userToDelete?.id?.let { viewModel.deleteUser(it) }
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("إلغاء") }
            }
        )
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserCard(
    user: User,
    warehouses: List<com.batterysales.data.models.Warehouse>,
    onRoleChange: (String) -> Unit,
    onWarehouseChange: (String?) -> Unit,
    onPermissionToggle: (String) -> Unit,
    onStatusToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var showRoleDialog by remember { mutableStateOf(false) }
    var showWarehouseDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (user.isActive) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                        if (!user.isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color.Red.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "موقوف",
                                    color = Color.Red,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!user.isActive) {
                        Button(
                            onClick = onStatusToggle,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).padding(end = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("إعادة تفعيل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = onStatusToggle) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = "Deactivate",
                                tint = Color(0xFFFF9800)
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.1f))
            Spacer(modifier = Modifier.height(20.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Role Info
                Surface(
                    modifier = Modifier.weight(1f).widthIn(min = 140.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("الدور", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (user.role == "admin") "مدير" else "بائع",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { showRoleDialog = true }, contentPadding = PaddingValues(0.dp)) {
                                Text("تغيير", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Warehouse Info
                Surface(
                    modifier = Modifier.weight(1f).widthIn(min = 140.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("المستودع", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val warehouseName = warehouses.find { it.id == user.warehouseId }?.name ?: "غير مرتبط"
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = warehouseName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = { showWarehouseDialog = true },
                                enabled = user.role == "seller",
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("ربط", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (user.role == "seller") {
                Spacer(modifier = Modifier.height(16.dp))
                Text("صلاحيات الخزينة:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = user.permissions.contains(User.PERMISSION_VIEW_TREASURY),
                        onClick = { onPermissionToggle(User.PERMISSION_VIEW_TREASURY) },
                        label = { Text("اطلاع على الخزينة", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = user.permissions.contains(User.PERMISSION_USE_TREASURY),
                        onClick = { onPermissionToggle(User.PERMISSION_USE_TREASURY) },
                        label = { Text("استخدام الخزينة", fontSize = 11.sp) }
                    )
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
                    items(warehouses.filter { it.isActive }) { warehouse ->
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
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = "الاسم الكامل",
                    modifier = Modifier.fillMaxWidth()
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "البريد الإلكتروني",
                    modifier = Modifier.fillMaxWidth()
                )
                com.batterysales.ui.components.CustomKeyboardTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "كلمة المرور",
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("الدور:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "admin", onClick = { role = "admin" })
                    Text("مدير")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = role == "seller", onClick = { role = "seller" })
                    Text("بائع")
                }
                
                if (role == "seller") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("المستودع المرتبط:", style = MaterialTheme.typography.titleSmall)
                    warehouses.filter { it.isActive }.forEach { warehouse ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selectedWarehouseId = warehouse.id }
                        ) {
                            RadioButton(selected = selectedWarehouseId == warehouse.id, onClick = { selectedWarehouseId = warehouse.id })
                            Text(warehouse.name)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
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
