package com.batterysales.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.batterysales.data.models.Product
import com.batterysales.data.models.ProductVariant
import com.batterysales.data.models.Warehouse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(navController: NavController, viewModel: SalesViewModel = hiltViewModel()) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paidAmount by remember { mutableStateOf("") }

    var expandedProduct by remember { mutableStateOf(false) }
    var expandedVariant by remember { mutableStateOf(false) }
    var expandedWarehouse by remember { mutableStateOf(false) }

    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Product Dropdown
        ExposedDropdownMenuBox(expanded = expandedProduct, onExpandedChange = { expandedProduct = !expandedProduct }) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = viewModel.selectedProduct.value?.name ?: "",
                onValueChange = {},
                label = { Text("اختر المنتج") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
            )
            ExposedDropdownMenu(expanded = expandedProduct, onDismissRequest = { expandedProduct = false }) {
                viewModel.products.value.forEach { product ->
                    DropdownMenuItem(
                        text = { Text(product.name) },
                        onClick = {
                            viewModel.onProductSelected(product)
                            expandedProduct = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Variant Dropdown
        ExposedDropdownMenuBox(expanded = expandedVariant, onExpandedChange = { expandedVariant = !expandedVariant }) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = viewModel.selectedVariant.value?.let { "${it.capacity} أمبير" } ?: "",
                onValueChange = {},
                label = { Text("اختر الصنف (السعة)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVariant) },
                enabled = viewModel.selectedProduct.value != null
            )
            ExposedDropdownMenu(expanded = expandedVariant, onDismissRequest = { expandedVariant = false }) {
                viewModel.variants.value.forEach { variant ->
                    DropdownMenuItem(
                        text = { Text("${variant.capacity} أمبير") },
                        onClick = {
                            viewModel.onVariantSelected(variant)
                            expandedVariant = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Warehouse Dropdown
        ExposedDropdownMenuBox(expanded = expandedWarehouse, onExpandedChange = { expandedWarehouse = !expandedWarehouse }) {
            TextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = viewModel.selectedWarehouse.value?.name ?: "",
                onValueChange = {},
                label = { Text("اختر المستودع") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWarehouse) },
            )
            ExposedDropdownMenu(expanded = expandedWarehouse, onDismissRequest = { expandedWarehouse = false }) {
                viewModel.warehouses.value.forEach { warehouse ->
                    DropdownMenuItem(
                        text = { Text(warehouse.name) },
                        onClick = {
                            viewModel.selectedWarehouse.value = warehouse
                            expandedWarehouse = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.quantity.value,
            onValueChange = { viewModel.quantity.value = it },
            label = { Text("الكمية") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.sellingPrice.value,
            onValueChange = { viewModel.sellingPrice.value = it },
            label = { Text("سعر البيع") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = customerName,
            onValueChange = { customerName = it },
            label = { Text("اسم العميل") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = customerPhone,
            onValueChange = { customerPhone = it },
            label = { Text("رقم هاتف العميل") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = paidAmount,
            onValueChange = { paidAmount = it },
            label = { Text("المبلغ المدفوع") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.createSale(
                    customerName = customerName,
                    customerPhone = customerPhone,
                    paidAmount = paidAmount.toDoubleOrNull() ?: 0.0
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.selectedVariant.value != null && viewModel.selectedWarehouse.value != null
        ) {
            Text("إنشاء عملية بيع")
        }
    }
}
