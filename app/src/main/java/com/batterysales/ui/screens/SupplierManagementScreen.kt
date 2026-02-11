package com.batterysales.ui.screens

import androidx.compose.foundation.background
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
import androidx.navigation.NavController
import com.batterysales.data.models.Supplier
import com.batterysales.viewmodel.SupplierViewModel
import com.batterysales.ui.components.CustomKeyboardTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierManagementScreen(
    navController: NavController,
    viewModel: SupplierViewModel = hiltViewModel()
) {
    val suppliers by viewModel.suppliers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var supplierToEdit by remember { mutableStateOf<Supplier?>(null) }
    var supplierToDelete by remember { mutableStateOf<Supplier?>(null) }

    val bgColor = MaterialTheme.colorScheme.background
    val accentColor = Color(0xFFFB8C00)
    val headerGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    )

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = accentColor,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مورد")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Gradient Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = headerGradient,
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }

                            Text(
                                text = "إدارة الموردين",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { viewModel.loadSuppliers() },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                            }
                        }
                    }
                }
            }

            if (isLoading && suppliers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            } else if (suppliers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("لا يوجد موردين مضافين", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            } else {
                items(suppliers) { supplier ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SupplierItemCard(
                            supplier = supplier,
                            onEdit = { supplierToEdit = supplier },
                            onDelete = { supplierToDelete = supplier }
                        )
                    }
                }
            }
        }

        error?.let {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) { Text("إغلاق") }
                }
            ) { Text(it) }
        }
    }

    if (showAddDialog) {
        SupplierDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, email, address, target ->
                viewModel.addSupplier(name, phone, email, address, target)
            }
        )
    }

    supplierToEdit?.let { supplier ->
        SupplierDialog(
            supplier = supplier,
            onDismiss = { supplierToEdit = null },
            onConfirm = { name, phone, email, address, target ->
                viewModel.updateSupplier(supplier.copy(
                    name = name,
                    phone = phone,
                    email = email,
                    address = address,
                    yearlyTarget = target
                ))
            }
        )
    }

    supplierToDelete?.let { supplier ->
        AlertDialog(
            onDismissRequest = { supplierToDelete = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف المورد '${supplier.name}'؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSupplier(supplier.id)
                        supplierToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { supplierToDelete = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun SupplierItemCard(
    supplier: Supplier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = supplier.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (supplier.phone.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, size = 14.sp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = supplier.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            if (supplier.yearlyTarget > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.1f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, size = 16.sp, tint = Color(0xFFFACC15))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الهدف السنوي: JD ${String.format("%,.3f", supplier.yearlyTarget)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun Icon(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.TextUnit, tint: Color) {
    Icon(icon, contentDescription, modifier = Modifier.size(size.value.dp), tint = tint)
}

@Composable
fun SupplierDialog(
    supplier: Supplier? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Double) -> Unit
) {
    var name by remember { mutableStateOf(supplier?.name ?: "") }
    var phone by remember { mutableStateOf(supplier?.phone ?: "") }
    var email by remember { mutableStateOf(supplier?.email ?: "") }
    var address by remember { mutableStateOf(supplier?.address ?: "") }
    var target by remember { mutableStateOf(supplier?.yearlyTarget?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (supplier == null) "إضافة مورد جديد" else "تعديل بيانات المورد") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CustomKeyboardTextField(value = name, onValueChange = { name = it }, label = "اسم المورد")
                CustomKeyboardTextField(value = phone, onValueChange = { phone = it }, label = "رقم الهاتف")
                CustomKeyboardTextField(value = email, onValueChange = { email = it }, label = "البريد الإلكتروني")
                CustomKeyboardTextField(value = address, onValueChange = { address = it }, label = "العنوان")
                CustomKeyboardTextField(value = target, onValueChange = { target = it }, label = "الهدف السنوي (Target)")
                Spacer(modifier = Modifier.height(com.batterysales.ui.components.LocalCustomKeyboardController.current.keyboardHeight.value))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name, phone, email, address, target.toDoubleOrNull() ?: 0.0)
                    onDismiss()
                }
            }) { Text(if (supplier == null) "إضافة" else "حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
