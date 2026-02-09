# 📊 بنية قاعدة البيانات Firebase Firestore

## نظرة عامة

تطبيق Battery Sales Manager يستخدم **Firebase Firestore** كقاعدة بيانات NoSQL. الهيكل أدناه يوضح جميع المجموعات والمستندات والحقول الحالية بناءً على آخر تحديثات النظام.

---

## 📁 المجموعات (Collections)

### 1. **users** - المستخدمون

تخزين بيانات المستخدمين والمدراء مع الصلاحيات والارتباط بالمستودعات.

```
Collection: users
├── Document: {userId}
│   ├── id: string (معرف المستخدم)
│   ├── email: string
│   ├── displayName: string
│   ├── phone: string
│   ├── role: string (admin | manager | seller | accountant | warehouse)
│   ├── warehouseId: string? (المستودع المرتبط بالبائع)
│   ├── permissions: list<string> (قائمة الصلاحيات المخصصة)
│   ├── isActive: boolean
│   ├── isEmailVerified: boolean
│   ├── notes: string
│   ├── profileImage: string?
│   ├── address: string
│   ├── city: string
│   ├── postalCode: string
│   ├── createdAt: timestamp
│   ├── updatedAt: timestamp
│   └── lastLoginAt: timestamp
```

---

### 2. **warehouses** - المستودعات

إدارة مواقع التخزين المختلفة.

```
Collection: warehouses
├── Document: {warehouseId}
│   ├── id: string
│   ├── name: string (اسم المستودع)
│   └── location: string (الموقع)
```

---

### 3. **products** - المنتجات (العلامات التجارية)

تخزين أسماء الشركات المصنعة أو العلامات التجارية.

```
Collection: products
├── Document: {productId}
│   ├── id: string
│   ├── name: string (مثلاً: Bosch, ACDelco)
│   ├── notes: string (تستخدم للمواصفة الفنية)
│   ├── archived: boolean
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

---

### 4. **product_variants** - أنواع المنتجات (السعات)

تخزين التفاصيل الفنية لكل سعة تابعة لعلامة تجارية.

```
Collection: product_variants
├── Document: {variantId}
│   ├── id: string
│   ├── productId: string (الارتباط بالمنتج الأساسي)
│   ├── capacity: number (السعة بالأمبير)
│   ├── sellingPrice: number
│   ├── barcode: string
│   ├── minQuantity: number (الحد الأدنى العام)
│   ├── minQuantities: map<string, number> (الحد الأدنى لكل مستودع: warehouseId -> qty)
│   ├── notes: string (تستخدم للمواصفة الفنية)
│   ├── archived: boolean
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

---

### 5. **stock_entries** - حركات المخزون (وارد وصادر)

تسجيل جميع عمليات التوريد، التحويل، والبيع المؤثرة على المخزون.

```
Collection: stock_entries
├── Document: {entryId}
│   ├── id: string
│   ├── productVariantId: string
│   ├── warehouseId: string
│   ├── quantity: number (موجب للتوريد، سالب للبيع/التحويل الصادر)
│   ├── costPrice: number (سعر التكلفة للوحدة)
│   ├── totalCost: number (إجمالي التكلفة)
│   ├── timestamp: timestamp
│   ├── supplier: string (اسم المورد - نصي للنسخ القديمة)
│   ├── supplierId: string (الارتباط بمجموعة الموردين)
│   ├── invoiceId: string? (مرتبط بفاتورة مبيعات في حال كان صادر)
│   ├── status: string (approved | pending)
│   ├── createdBy: string (معرف المستخدم)
│   └── createdByUserName: string (اسم المستخدم الذي قام بالعملية)
```

---

### 6. **invoices** - فواتير المبيعات

تخزين الفواتير وتفاصيل المبيعات والذمم.

```
Collection: invoices
├── Document: {invoiceId}
│   ├── id: string
│   ├── invoiceNumber: string
│   ├── customerName: string
│   ├── customerPhone: string
│   ├── items: list<map> (InvoiceItem: productId, productName, quantity, price, total...)
│   ├── subtotal: number
│   ├── totalAmount: number
│   ├── oldBatteriesValue: number (قيمة الخصم مقابل السكراب)
│   ├── oldBatteriesQuantity: number
│   ├── oldBatteriesTotalAmperes: number
│   ├── finalAmount: number (المبلغ النهائي بعد خصم السكراب)
│   ├── paidAmount: number
│   ├── remainingAmount: number (الذمم)
│   ├── status: string (paid | pending | cancelled)
│   ├── invoiceDate: timestamp
│   ├── sellerId: string
│   ├── sellerName: string
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

---

### 7. **suppliers** - الموردون

إدارة بيانات الموردين والأهداف السنوية.

```
Collection: suppliers
├── Document: {supplierId}
│   ├── id: string
│   ├── name: string
│   ├── phone: string
│   ├── email: string
│   ├── yearlyTarget: number (الهدف السنوي للمشتريات)
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

---

### 8. **bills** - الشيكات والكمبيالات

إدارة الالتزامات المالية للموردين.

```
Collection: bills
├── Document: {billId}
│   ├── id: string
│   ├── description: string
│   ├── amount: number
│   ├── paidAmount: number
│   ├── dueDate: timestamp
│   ├── status: string (PAID | UNPAID | OVERDUE | PARTIAL)
│   ├── billType: string (CHECK | BILL | TRANSFER | OTHER)
│   ├── referenceNumber: string (رقم الشيك أو السند)
│   ├── supplierId: string
│   ├── relatedEntryId: string? (مرتبط بعملية توريد محددة)
│   ├── paidDate: timestamp?
│   └── createdAt: timestamp
```

---

### 9. **transactions** - الخزينة (Treasury)

تسجيل جميع التدفقات النقدية الفعلية.

```
Collection: transactions
├── Document: {transactionId}
│   ├── id: string
│   ├── type: string (INCOME | EXPENSE | PAYMENT | REFUND)
│   ├── amount: number
│   ├── description: string
│   ├── relatedId: string? (مرتبط بفاتورة أو دفعة)
│   ├── referenceNumber: string (رقم الشيك أو السند المالي)
│   ├── createdAt: timestamp
│   └── notes: string
```

---

### 10. **bank_transactions** - حركة البنك

تسجيل عمليات الإيداع والسحب البنكي (خاصة الشيكات).

```
Collection: bank_transactions
├── Document: {bankTransId}
│   ├── id: string
│   ├── billId: string? (الارتباط بالكمبيالة/الشيك)
│   ├── amount: number
│   ├── type: string (DEPOSIT | WITHDRAWAL)
│   ├── description: string
│   ├── referenceNumber: string
│   ├── date: timestamp
│   └── notes: string
```

---

### 11. **old_battery_transactions** - سجل البطاريات القديمة (السكراب)

إدارة مخزون السكراب في المستودعات المختلفة.

```
Collection: old_battery_transactions
├── Document: {scrapId}
│   ├── id: string
│   ├── invoiceId: string? (إذا كان مستلم من فاتورة)
│   ├── warehouseId: string (المستودع الذي توجد فيه البطاريات)
│   ├── quantity: number
│   ├── totalAmperes: number
│   ├── type: string (INTAKE | SALE | ADJUSTMENT)
│   ├── amount: number (قيمة البيع في حال كان النوع SALE)
│   ├── date: timestamp
│   └── notes: string
```

---

## 🔐 قواعد الأمان (Security Rules)

تعتمد قواعد الأمان على أدوار المستخدمين المعرفة في مجموعة `users`. يتم التحقق من حقل `role` للسماح بالوصول:
- **admin/manager**: وصول كامل للقراءة والكتابة لجميع المجموعات.
- **seller**: وصول للقراءة لجميع المجموعات، وكتابة محدودة للفواتير وعمليات السكراب وحركات المخزون الخاصة بمستودعه فقط.
- **accountant**: وصول كامل للبيانات المالية (bills, transactions, bank) وتقارير الموردين.

---

## 📋 ملاحظات برمجية (Developer Notes)

1. **الارتباط التلقائي**:
   - تسجيل دفعة لفاتورة ينشئ تلقائياً مستند في `transactions`.
   - تسجيل شيك مسدد ينشئ تلقائياً مستند في `bank_transactions` و `transactions`.
   - استلام سكراب في فاتورة مبيعات ينشئ تلقائياً مستند في `old_battery_transactions` مرتبط بنفس مستودع الفاتورة.

2. **الدقة المالية**: يتم حفظ المبالغ كـ `number` بدقة تصل إلى 4 منازل عشرية لمنع أخطاء التقريب في العملات (JD).

3. **الحذف المنطقي**: يتم استخدام حقل `archived` في المنتجات لمنع حذف البيانات التي لها سجلات تاريخية.
