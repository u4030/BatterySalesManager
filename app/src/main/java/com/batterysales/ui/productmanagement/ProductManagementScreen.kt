package com.batterysales.ui.productmanagement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.viewmodel.ProductManagementViewModel

@Composable
fun ProductManagementScreen(viewModel: ProductManagementViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddProductDialog by remember { mutableStateOf(false) }
    var showAddVariantDialog by remember { mutableStateOf(false) }

    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var variantToEdit by remember { mutableStateOf<ProductVariant?>(null) }

    var productToArchive by remember { mutableStateOf<Product?>(null) }
    var variantToArchive by remember { mutableStateOf<ProductVariant?>(null) }

    // Dialogs
    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onAddProduct = { name, notes -> viewModel.addProduct(name, notes) }
        )
    }

    productToEdit?.let { product ->
        EditProductDialog(
            product = product,
            onDismiss = { productToEdit = null },
            onUpdateProduct = { updatedProduct -> viewModel.updateProduct(updatedProduct) }
        )
    }

    if (showAddVariantDialog) {
        AddVariantDialog(
            onDismiss = { showAddVariantDialog = false },
            onAddVariant = { capacity, sellingPrice, barcode, notes ->
                viewModel.addVariant(capacity, sellingPrice, barcode, notes)
            }
        )
    }

    variantToEdit?.let { variant ->
        EditVariantDialog(
            variant = variant,
            onDismiss = { variantToEdit = null },
            onUpdateVariant = { updatedVariant -> viewModel.updateVariant(updatedVariant) }
        )
    }

    productToArchive?.let { product ->
        DeleteConfirmationDialog(
            itemName = product.name,
            onDismiss = { productToArchive = null },
            onConfirm = { viewModel.archiveProduct(product) }
        )
    }

    variantToArchive?.let { variant ->
        DeleteConfirmationDialog(
            itemName = "${variant.capacity} Amp",
            onDismiss = { variantToArchive = null },
            onConfirm = { viewModel.archiveVariant(variant) }
        )
    }


    Row(modifier = Modifier.fillMaxSize()) {
        // Products Column
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
            Text("المنتجات", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showAddProductDialog = true }) { Text("إضافة منتج") }
            Spacer(modifier = Modifier.height(8.dp))
            if(uiState.isLoading) {
                CircularProgressIndicator()
            }
            LazyColumn {
                items(uiState.products) { product ->
                    ProductItem(
                        product = product,
                        onClick = { viewModel.selectProduct(product) },
                        onEdit = { productToEdit = product },
                        onDelete = { productToArchive = product }
                    )
                }
            }
        }

        // Variants Column
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
            val selectedProduct = uiState.selectedProduct
            if (selectedProduct != null) {
                Text("سعات: ${selectedProduct.name}", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showAddVariantDialog = true }) { Text("إضافة سعة") }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(uiState.variants) { variant ->
                        VariantItem(
                            variant = variant,
                            onEdit = { variantToEdit = variant },
                            onDelete = { variantToArchive = variant }
                        )
                    }
                }
            } else {
                Text("الرجاء تحديد منتج لعرض السعات")
            }
        }
    }
}

// Dialog Composables (Add, Edit, Delete Confirmation)

@Composable
fun AddProductDialog(onDismiss: () -> Unit, onAddProduct: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة منتج جديد") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المنتج") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = { Button(onClick = { onAddProduct(name, notes); onDismiss() }) { Text("إضافة") } },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun EditProductDialog(product: Product, onDismiss: () -> Unit, onUpdateProduct: (Product) -> Unit) {
    var name by remember { mutableStateOf(product.name) }
    var notes by remember { mutableStateOf(product.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل المنتج") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المنتج") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onUpdateProduct(product.copy(name = name, notes = notes))
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AddVariantDialog(onDismiss: () -> Unit, onAddVariant: (Int, Double, String, String) -> Unit) {
    var capacity by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة سعة جديدة") },
        text = {
            Column {
                OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("السعة (أمبير)") })
                OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = it }, label = { Text("سعر البيع") })
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("الباركود") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onAddVariant(capacity.toIntOrNull() ?: 0, sellingPrice.toDoubleOrNull() ?: 0.0, barcode, notes)
                onDismiss()
            }) { Text("إضافة") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun EditVariantDialog(variant: ProductVariant, onDismiss: () -> Unit, onUpdateVariant: (ProductVariant) -> Unit) {
    var capacity by remember { mutableStateOf(variant.capacity.toString()) }
    var sellingPrice by remember { mutableStateOf(variant.sellingPrice.toString()) }
    var barcode by remember { mutableStateOf(variant.barcode) }
    var notes by remember { mutableStateOf(variant.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل السعة") },
        text = {
            Column {
                OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("السعة (أمبير)") })
                OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = it }, label = { Text("سعر البيع") })
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("الباركود") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onUpdateVariant(variant.copy(
                    capacity = capacity.toIntOrNull() ?: 0,
                    sellingPrice = sellingPrice.toDoubleOrNull() ?: 0.0,
                    barcode = barcode,
                    notes = notes
                ))
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun DeleteConfirmationDialog(itemName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأكيد الحذف") },
        text = { Text("هل أنت متأكد أنك تريد حذف '$itemName'؟ سيتم أرشفة هذا العنصر.") },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) { Text("حذف") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("إلغاء") } }
    )
}


// Item Composables

@Composable
fun ProductItem(product: Product, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = product.name, modifier = Modifier.weight(1f))
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
            }
        }
    }
}

@Composable
fun VariantItem(variant: ProductVariant, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("السعة: ${variant.capacity} أمبير")
            Text("السعر: ${variant.sellingPrice} ريال")
            Text("الباركود: ${variant.barcode}")
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
            }
        }
    }
}
