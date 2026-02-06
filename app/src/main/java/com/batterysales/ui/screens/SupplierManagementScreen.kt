package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.data.models.Supplier
import com.batterysales.viewmodel.SupplierViewModel
import com.batterysales.ui.components.CustomKeyboardTextField
import com.batterysales.ui.theme.LocalInputTextStyle

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة الموردين", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مورد")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading && suppliers.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (suppliers.isEmpty()) {
                Text("لا يوجد موردين مضافين", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(suppliers) { supplier ->
                        SupplierItemCard(
                            supplier = supplier,
                            onEdit = { supplierToEdit = supplier },
                            onDelete = { supplierToDelete = supplier }
                        )
                    }
                }
            }

            error?.let {
                Snackbar(
                    modifier = Modifier.padding(16.dp).align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) { Text("إغلاق") }
                    }
                ) { Text(it) }
            }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(supplier.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (supplier.phone.isNotEmpty()) {
                    Text("هاتف: ${supplier.phone}", style = MaterialTheme.typography.bodyMedium)
                }
                if (supplier.yearlyTarget > 0) {
                    Text("الهدف السنوي: JD ${String.format("%.3f", supplier.yearlyTarget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomKeyboardTextField(value = name, onValueChange = { name = it }, label = "اسم المورد")
                CustomKeyboardTextField(value = phone, onValueChange = { phone = it }, label = "رقم الهاتف")
                CustomKeyboardTextField(value = email, onValueChange = { email = it }, label = "البريد الإلكتروني")
                CustomKeyboardTextField(value = address, onValueChange = { address = it }, label = "العنوان")
                CustomKeyboardTextField(value = target, onValueChange = { target = it }, label = "الهدف السنوي (Target)")
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
