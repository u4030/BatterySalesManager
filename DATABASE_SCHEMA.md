# 📊 بنية قاعدة البيانات Firebase Firestore

## نظرة عامة

تطبيق Battery Sales Manager يستخدم **Firebase Firestore** كقاعدة بيانات NoSQL. الهيكل أدناه يوضح جميع المجموعات والمستندات والحقول الحالية.

---

## 📁 المجموعات (Collections)

### 1. **users** - المستخدمون
تخزين بيانات المستخدمين والمدراء والصلاحيات.

```
Collection: users
├── Document: {userId}
│   ├── id: string (معرف المستخدم من Firebase Auth)
│   ├── email: string
│   ├── displayName: string
│   ├── role: string (admin | seller)
│   ├── isActive: boolean
│   ├── warehouseId: string (المستودع المرتبط بالبائع)
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

### 2. **warehouses** - المستودعات
تعريف المستودعات المختلفة في النظام.

```
Collection: warehouses
├── Document: {warehouseId}
│   ├── id: string
│   ├── name: string
│   ├── location: string
│   └── createdAt: timestamp
```

### 3. **products** - المنتجات (الرئيسية)
تخزين البيانات العامة للمنتجات (الشركات/الأنواع).

```
Collection: products
├── Document: {productId}
│   ├── id: string
│   ├── name: string
│   ├── brand: string
│   ├── archived: boolean
│   └── createdAt: timestamp
```

### 4. **product_variants** - متغيرات المنتجات
تخزين التفاصيل التقنية والأسعار لكل منتج (حسب الأمبير).

```
Collection: product_variants
├── Document: {variantId}
│   ├── id: string
│   ├── productId: string (ربط بمنتج رئيسي)
│   ├── capacity: number (الأمبير)
│   ├── sellingPrice: number
│   ├── barcode: string
│   ├── minQuantity: number (الحد الأدنى العام)
│   ├── minQuantities: map (حد أدنى مخصص لكل مستودع {warehouseId: quantity})
│   ├── notes: string (تظهر في الواجهة كـ "المواصفة")
│   ├── archived: boolean
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

### 5. **invoices** - الفواتير
تخزين فواتير المبيعات.

```
Collection: invoices
├── Document: {invoiceId}
│   ├── id: string
│   ├── invoiceNumber: string
│   ├── customerName: string
│   ├── customerPhone: string
│   ├── items: list<map> (تفاصيل المنتجات المباعة)
│   ├── totalAmount: number
│   ├── paidAmount: number
│   ├── remainingAmount: number
│   ├── oldBatteriesValue: number (قيمة الخصم مقابل السكراب)
│   ├── status: string (paid | pending | cancelled | draft)
│   ├── warehouseId: string
│   ├── invoiceDate: timestamp
│   ├── sellerName: string
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

### 6. **payments** - الدفعات
تسجيل جميع المبالغ المستلمة (نقد أو دفعات من ذمم).

```
Collection: payments
├── Document: {paymentId}
│   ├── id: string
│   ├── invoiceId: string
│   ├── amount: number
│   ├── paymentMethod: string
│   ├── timestamp: timestamp
│   └── notes: string
```

### 7. **suppliers** - الموردون
بيانات الموردين والأهداف السنوية.

```
Collection: suppliers
├── Document: {supplierId}
│   ├── id: string
│   ├── name: string
│   ├── phone: string
│   ├── yearlyTarget: number
│   ├── resetDate: timestamp (تاريخ تصفير الحساب السنوي)
│   └── createdAt: timestamp
```

### 8. **stock_entries** - سجل المخزون
سجل حركات المخزون (مشتريات، مبيعات، مرتجعات).

```
Collection: stock_entries
├── Document: {entryId}
│   ├── id: string
│   ├── productVariantId: string
│   ├── warehouseId: string
│   ├── quantity: number (موجب للمشتريات، سالب للمبيعات)
│   ├── costPrice: number
│   ├── totalCost: number
│   ├── status: string (approved | pending)
│   ├── supplierId: string
│   ├── invoiceId: string (في حال كان المخرج مبيعة)
│   ├── createdByUserName: string
│   ├── returnedQuantity: number (الكمية المرجعة من المشتريات)
│   └── timestamp: timestamp
```

### 9. **old_battery_transactions** - سجل السكراب
تخزين عمليات استلام وبيع البطاريات القديمة.

```
Collection: old_battery_transactions
├── Document: {transactionId}
│   ├── id: string
│   ├── type: string (INTAKE | SALE | ADJUSTMENT)
│   ├── quantity: number
│   ├── totalAmperes: number
│   ├── amount: number (سعر البيع الإجمالي)
│   ├── warehouseId: string
│   ├── createdByUserName: string
│   ├── invoiceId: string (إذا كان الاستلام مرتبط بفاتورة)
│   └── date: timestamp
```

### 10. **bills** - الكمبيالات والشيكات
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
│   ├── relatedEntryId: string (ربط بطلبية شراء محددة)
│   └── createdAt: timestamp
```

### 11. **bank_transactions** - العمليات البنكية
سجل الشيكات والعمليات في حساب البنك.

```
Collection: bank_transactions
├── Document: {transactionId}
│   ├── billId: string
│   ├── amount: number
│   ├── type: string (DEPOSIT | WITHDRAWAL)
│   ├── description: string
│   ├── referenceNumber: string
│   └── date: timestamp
```

### 12. **transactions** - الخزينة (المحاسبة العامة)
سجل العمليات النقدية اليومية.

```
Collection: transactions
├── Document: {transactionId}
│   ├── type: string (INCOME | EXPENSE | PAYMENT | REFUND)
│   ├── amount: number
│   ├── description: string
│   ├── referenceNumber: string
│   ├── relatedId: string (معرف الدفعة أو الفاتورة أو المصروف)
│   └── createdAt: timestamp
```

---

## 📈 استراتيجيات الفهرسة (Indexing)

يتم استخدام الفهارس التالية لضمان سرعة التقارير:
- `invoices`: `warehouseId` + `status` + `updatedAt` (لشاشة الفواتير والذمم).
- `stock_entries`: `productVariantId` + `warehouseId` + `status` (لحساب المخزون الفعلي).
- `payments`: `timestamp` (لإحصائيات التحصيل اليومي).
- `product_variants`: `barcode` (للبحث السريع).

---

**آخر تحديث**: 2024-05-24
