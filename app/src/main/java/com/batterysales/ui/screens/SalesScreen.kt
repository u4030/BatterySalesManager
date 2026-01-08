package com.batterysales.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.batterysales.data.models.Invoice
import com.batterysales.data.models.InvoiceItem
import com.batterysales.data.models.Product
import com.batterysales.viewmodel.InvoiceViewModel
import com.batterysales.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    navController: NavHostController,
    invoiceViewModel: InvoiceViewModel = hiltViewModel(),
    productViewModel: ProductViewModel = hiltViewModel()
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paidAmount by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("0") }
    var oldBatteriesCount by remember { mutableStateOf("0") }
    var oldBatteryPrice by remember { mutableStateOf("0") }
    var invoiceItems by remember { mutableStateOf<List<InvoiceItem>>(emptyList()) }
    var showProductPicker by remember { mutableStateOf(false) }

    val isLoading by invoiceViewModel.isLoading.collectAsState()
    val successMessage by invoiceViewModel.successMessage.collectAsState()

    val products by productViewModel.products.collectAsState()


    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            navController.popBackStack()
            invoiceViewModel.clearSuccess()
        }
    }

    val totalAmount = invoiceItems.sumOf { it.totalPrice }
    val oldBatteriesValue = (oldBatteriesCount.toIntOrNull() ?: 0) * (oldBatteryPrice.toDoubleOrNull() ?: 0.0)
    val finalAmount = totalAmount - (discount.toDoubleOrNull() ?: 0.0) - oldBatteriesValue

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تسجيل مبيعة جديدة", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CustomerInfoCard(customerName, { customerName = it }, customerPhone, { customerPhone = it })
            InvoiceItemsCard(invoiceItems, { showProductPicker = true }) { updatedItems -> invoiceItems = updatedItems }
            OldBatteriesCard(oldBatteriesCount, { oldBatteriesCount = it }, oldBatteryPrice, { oldBatteryPrice = it })
            PaymentDetailsCard(discount, { discount = it }, paidAmount, { paidAmount = it }, totalAmount, oldBatteriesValue, finalAmount)
            SaveButton(isLoading) {
                val invoice = Invoice(
                    customerName = customerName,
                    customerPhone = customerPhone,
                    items = invoiceItems,
                    totalAmount = totalAmount,
                    discount = discount.toDoubleOrNull() ?: 0.0,
                    oldBatteriesValue = oldBatteriesValue,
                    finalAmount = finalAmount,
                    paidAmount = paidAmount.toDoubleOrNull() ?: 0.0,
                    remainingAmount = finalAmount - (paidAmount.toDoubleOrNull() ?: 0.0),
                    status = if ((paidAmount.toDoubleOrNull() ?: 0.0) >= finalAmount) "paid" else "pending"
                )
                invoiceViewModel.createInvoice(invoice)
            }
        }
    }

    if (showProductPicker) {
        ProductPickerDialog(
            products = products,
            onDismiss = { showProductPicker = false },
            onProductSelected = { product, quantity ->
                val newItem = InvoiceItem(
                    productId = product.id,
                    productName = product.name,
                    quantity = quantity,
                    unitPrice = product.sellingPrice,
                    totalPrice = product.sellingPrice * quantity
                )
                invoiceItems = invoiceItems + newItem
                showProductPicker = false
            }
        )
    }
}

@Composable
fun CustomerInfoCard(name: String, onNameChange: (String) -> Unit, phone: String, onPhoneChange: (String) -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("بيانات العميل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("اسم العميل") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = onPhoneChange, label = { Text("رقم الجوال") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        }
            }
            }

@Composable
fun InvoiceItemsCard(items: List<InvoiceItem>, onAddClick: () -> Unit, onItemsChange: (List<InvoiceItem>) -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("المنتجات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Button(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة منتج")
                }
            }
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.quantity} x ${item.productName} @ ${item.unitPrice}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { onItemsChange(items.filterIndexed { i, _ -> i != index }) }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun OldBatteriesCard(count: String, onCountChange: (String) -> Unit, price: String, onPriceChange: (String) -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("البطاريات القديمة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = count, onValueChange = onCountChange, label = { Text("العدد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(value = price, onValueChange = onPriceChange, label = { Text("سعر الواحدة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PaymentDetailsCard(discount: String, onDiscountChange: (String) -> Unit, paidAmount: String, onPaidAmountChange: (String) -> Unit, total: Double, oldBatteriesValue: Double, final: Double) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("الدفع", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(value = discount, onValueChange = onDiscountChange, label = { Text("الخصم") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = paidAmount, onValueChange = onPaidAmountChange, label = { Text("المبلغ المدفوع") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Text("الإجمالي: $total د.أ")
            Text("قيمة البطاريات القديمة: $oldBatteriesValue د.أ")
            Text("المبلغ النهائي: $final د.أ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SaveButton(isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("حفظ المبيعة وإصدار فاتورة")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPickerDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onProductSelected: (Product, Int) -> Unit
) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var quantity by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار منتج") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (selectedProduct == null) {
                    LazyColumn {
                        items(products) { product ->
                            ListItem(
                                headlineContent = { Text(product.name) },
                                supportingContent = { Text("الكمية المتاحة: ${product.quantity}") },
                                modifier = Modifier.clickable { selectedProduct = product }
                            )
                        }
                    }
                } else {
                    Text("المنتج المختار: ${selectedProduct!!.name}")
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("الكمية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onProductSelected(selectedProduct!!, quantity.toIntOrNull() ?: 1) },
                enabled = selectedProduct != null
            ) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
