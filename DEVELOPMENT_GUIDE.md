# 📖 دليل التطوير - Battery Sales Manager

دليل شامل لمطوري المشروع يتضمن أفضل الممارسات والمعايير والإرشادات.

---

## 🎯 أفضل ممارسات Kotlin و Android

### 1. **معايير الترميز (Coding Standards)**

#### تسمية الملفات والفئات:
```kotlin
// ✅ صحيح
class LoginViewModel : ViewModel()
data class Product(val id: String, val name: String)
fun validateEmail(email: String): Boolean

// ❌ خطأ
class login_view_model
class product_data
fun validate_email()
```

#### تسمية المتغيرات:
```kotlin
// ✅ صحيح
val productName: String
var isLoading: Boolean
private val firebaseAuth: FirebaseAuth

// ❌ خطأ
val pname: String
var loading: Boolean
val fb_auth: FirebaseAuth
```

#### التعليقات:
```kotlin
// ✅ صحيح
/**
 * تحديث كمية المنتج في المستودع
 * @param productId معرف المنتج
 * @param quantity الكمية الجديدة
 */
fun updateProductQuantity(productId: String, quantity: Int)

// ❌ خطأ
// تحديث الكمية
fun updateQty(id: String, qty: Int)
```

---

### 2. **معمارية التطبيق (Architecture)**

#### نمط MVVM:
```
┌─────────────────────────────────────┐
│         UI Layer (Screens)          │
│  - Composable Functions             │
│  - State Management                 │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│      ViewModel Layer                │
│  - Business Logic                   │
│  - State Holders                    │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│      Repository Layer               │
│  - Data Access                      │
│  - Caching                          │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│      Data Sources                   │
│  - Firebase Firestore               │
│  - Local Database                   │
└─────────────────────────────────────┘
```

#### مثال على الهيكل:
```kotlin
// ViewModel
class SalesViewModel : ViewModel() {
    private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
    val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

    fun addInvoice(invoice: Invoice) {
        viewModelScope.launch {
            repository.addInvoice(invoice)
        }
    }
}

// Repository
class SalesRepository {
    suspend fun addInvoice(invoice: Invoice) {
        firestore.collection(Collections.INVOICES)
            .add(invoice)
    }
}

// UI
@Composable
fun SalesScreen(viewModel: SalesViewModel) {
    val invoices by viewModel.invoices.collectAsState()
    
    LazyColumn {
        items(invoices) { invoice ->
            InvoiceItem(invoice)
        }
    }
}
```

---

### 3. **إدارة الحالة (State Management)**

#### استخدام StateFlow:
```kotlin
// ✅ صحيح
class ProductViewModel : ViewModel() {
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
}

// في الـ Composable
@Composable
fun ProductScreen(viewModel: ProductViewModel) {
    val products by viewModel.products.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
}
```

#### تجنب الأخطاء الشائعة:
```kotlin
// ❌ خطأ - إعادة إنشاء الـ State في كل render
@Composable
fun BadScreen() {
    val state = remember { mutableStateOf(0) } // ✅ صحيح
    // لا تفعل هذا:
    val badState = mutableStateOf(0) // ❌ خطأ - يتم إنشاء state جديد في كل render
}
```

---

### 4. **Jetpack Compose Best Practices**

#### تقسيم الـ Composables:
```kotlin
// ✅ صحيح - Composables صغيرة وقابلة لإعادة الاستخدام
@Composable
fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text("${product.quantity} وحدة", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ProductList(products: List<Product>) {
    LazyColumn {
        items(products) { product ->
            ProductCard(product) { /* handle click */ }
        }
    }
}

// ❌ خطأ - Composable واحد كبير
@Composable
fun BadProductScreen(products: List<Product>) {
    LazyColumn {
        items(products) { product ->
            Card {
                Column {
                    Text(product.name)
                    Text(product.quantity.toString())
                    // ... كود كثير
                }
            }
        }
    }
}
```

#### استخدام Modifiers بشكل صحيح:
```kotlin
// ✅ صحيح
@Composable
fun Button(modifier: Modifier = Modifier) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(16.dp)
    ) {
        Text("اضغط هنا")
    }
}

// ❌ خطأ - عدم السماح بتخصيص الـ modifier
@Composable
fun BadButton() {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text("اضغط هنا")
    }
}
```

---

### 5. **Coroutines والعمليات غير المتزامنة**

#### استخدام viewModelScope:
```kotlin
// ✅ صحيح
class SalesViewModel : ViewModel() {
    fun loadInvoices() {
        viewModelScope.launch {
            try {
                val invoices = repository.getInvoices()
                _invoices.value = invoices
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}

// ❌ خطأ - استخدام GlobalScope
class BadViewModel {
    fun loadInvoices() {
        GlobalScope.launch { // ❌ تجنب GlobalScope
            val invoices = repository.getInvoices()
        }
    }
}
```

#### معالجة الأخطاء:
```kotlin
// ✅ صحيح
viewModelScope.launch {
    try {
        val data = repository.fetchData()
        _data.value = data
    } catch (e: FirebaseException) {
        _error.value = "خطأ في Firebase"
    } catch (e: IOException) {
        _error.value = "خطأ في الاتصال"
    } catch (e: Exception) {
        _error.value = "خطأ غير متوقع"
    }
}
```

---

### 6. **Dependency Injection مع Hilt**

#### إعداد Hilt:
```kotlin
// في build.gradle.kts
dependencies {
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
}

// في MainActivity.kt
@HiltAndroidApp
class BatterySalesApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity()
```

#### استخدام Hilt في ViewModel:
```kotlin
// ✅ صحيح
@HiltViewModel
class SalesViewModel @Inject constructor(
    private val repository: SalesRepository
) : ViewModel() {
    // ...
}

// في Composable
@Composable
fun SalesScreen(
    viewModel: SalesViewModel = hiltViewModel()
) {
    // ...
}
```

---

### 7. **Firebase Best Practices**

#### قراءة البيانات:
```kotlin
// ✅ صحيح - استخدام Snapshots Listener
private fun observeInvoices() {
    firestore.collection(Collections.INVOICES)
        .whereEqualTo("userId", currentUserId)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                _error.value = error.message
                return@addSnapshotListener
            }
            val invoices = snapshot?.documents?.mapNotNull { 
                it.toObject(Invoice::class.java) 
            } ?: emptyList()
            _invoices.value = invoices
        }
}

// ❌ خطأ - قراءة البيانات في كل مرة
private fun loadInvoices() {
    firestore.collection(Collections.INVOICES)
        .get()
        .addOnSuccessListener { snapshot ->
            val invoices = snapshot.documents.mapNotNull { 
                it.toObject(Invoice::class.java) 
            }
            _invoices.value = invoices
        }
}
```

#### كتابة البيانات:
```kotlin
// ✅ صحيح
suspend fun addInvoice(invoice: Invoice) = withContext(Dispatchers.IO) {
    try {
        firestore.collection(Collections.INVOICES).add(invoice)
    } catch (e: Exception) {
        throw Exception("فشل إضافة الفاتورة: ${e.message}")
    }
}

// ❌ خطأ - عدم معالجة الأخطاء
fun addInvoice(invoice: Invoice) {
    firestore.collection(Collections.INVOICES).add(invoice)
}
```

---

### 8. **اختبار الوحدة (Unit Testing)**

#### مثال على اختبار:
```kotlin
class SalesRepositoryTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: SalesRepository
    private val mockFirestore = mockk<FirebaseFirestore>()

    @Before
    fun setup() {
        repository = SalesRepository(mockFirestore)
    }

    @Test
    fun `test add invoice successfully`() = runTest {
        // Arrange
        val invoice = Invoice(id = "1", invoiceNumber = "INV-001")
        coEvery { mockFirestore.collection(any()).add(any()) } returns mockk()

        // Act
        repository.addInvoice(invoice)

        // Assert
        coVerify { mockFirestore.collection(Collections.INVOICES).add(invoice) }
    }
}
```

---

## 🔒 معايير الأمان

### 1. **حماية البيانات الحساسة**:
```kotlin
// ✅ صحيح - لا تخزن كلمات المرور
val user = User(
    id = "123",
    email = "user@example.com",
    // لا تخزن كلمة المرور هنا
)

// ❌ خطأ
val user = User(
    id = "123",
    email = "user@example.com",
    password = "password123" // ❌ لا تفعل هذا
)
```

### 2. **قواعس الأمان في Firebase**:
```javascript
// ✅ صحيح
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /invoices/{invoiceId} {
      allow read: if request.auth != null;
      allow write: if isAdmin() || request.auth.uid == resource.data.userId;
    }
  }
}
```

---

## 📊 معايير الأداء

### 1. **تحسين الاستعلامات**:
```kotlin
// ✅ صحيح - استعلام محدود
firestore.collection(Collections.INVOICES)
    .whereEqualTo("userId", userId)
    .orderBy("createdAt", Query.Direction.DESCENDING)
    .limit(20)
    .get()

// ❌ خطأ - تحميل جميع البيانات
firestore.collection(Collections.INVOICES)
    .get() // قد يحمل آلاف المستندات
```

### 2. **استخدام الفهارس**:
```kotlin
// تأكد من إنشاء الفهارس المركبة في Firebase Console
// مثال: invoices (userId, createdAt)
```

---

## 🚀 خطوات التطوير

### 1. **قبل البدء**:
- [ ] اقرأ المتطلبات بعناية
- [ ] افهم الهيكل المعماري
- [ ] تحقق من قواعد الأمان

### 2. **أثناء التطوير**:
- [ ] اتبع معايير الترميز
- [ ] اكتب اختبارات الوحدة
- [ ] استخدم Git بشكل صحيح

### 3. **قبل الدمج (Merge)**:
- [ ] تأكد من نجاح جميع الاختبارات
- [ ] راجع الكود
- [ ] تحقق من عدم وجود مشاكل الأداء

---

## 📝 قائمة التحقق (Checklist)

قبل تسليم أي ميزة:

- [ ] الكود يتبع معايير الترميز
- [ ] جميع الاختبارات تمر بنجاح
- [ ] لا توجد تحذيرات Lint
- [ ] الأداء مقبول
- [ ] الأمان محقق
- [ ] التوثيق محدث
- [ ] تم اختبار جميع الحالات الحدية

---

## 🤝 المساهمة

1. أنشئ فرع جديد: `git checkout -b feature/feature-name`
2. اكتب الكود واتبع المعايير
3. اكتب الاختبارات
4. أرسل Pull Request
5. انتظر المراجعة

---

**آخر تحديث**: 2024-01-15
