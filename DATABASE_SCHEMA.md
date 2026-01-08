# 📊 بنية قاعدة البيانات Firebase Firestore

## نظرة عامة

تطبيق Battery Sales Manager يستخدم **Firebase Firestore** كقاعدة بيانات NoSQL. الهيكل أدناه يوضح جميع المجموعات والمستندات والحقول.

---

## 📁 المجموعات (Collections)

### 1. **users** - المستخدمون

تخزين بيانات المستخدمين والمدراء.

```
Collection: users
├── Document: {userId}
│   ├── id: string (معرف المستخدم من Firebase Auth)
│   ├── email: string
│   ├── displayName: string
│   ├── role: string (ADMIN | SELLER)
│   ├── isActive: boolean
│   ├── phoneNumber: string (اختياري)
│   ├── createdAt: timestamp
│   ├── updatedAt: timestamp
│   └── lastLoginAt: timestamp (اختياري)
```

**الفهارس:**
- `role` (للفرز حسب الدور)
- `isActive` (للبحث عن المستخدمين النشطين)
- `createdAt` (للترتيب الزمني)

---

### 2. **products** - المنتجات

تخزين بيانات البطاريات والمنتجات في المستودع.

```
Collection: products
├── Document: {productId}
│   ├── id: string
│   ├── name: string (اسم المنتج)
│   ├── capacity: number (السعة بالأمبير)
│   ├── type: string (نوع/الشركة)
│   ├── costPrice: number (سعر التكلفة - مخفي عن البائع)
│   ├── barcode: string (الباركود/QR Code)
│   ├── quantity: number (الكمية الحالية)
│   ├── minimumQuantity: number (الحد الأدنى)
│   ├── isArchived: boolean (للحذف المنطقي)
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

**الفهارس:**
- `barcode` (للبحث السريع)
- `name` (للبحث التقريبي)
- `quantity` (للتنبيهات)
- `isArchived` (لعرض المنتجات النشطة فقط)

---

### 3. **invoices** - الفواتير/المبيعات

تخزين بيانات الفواتير والمبيعات.

```
Collection: invoices
├── Document: {invoiceId}
│   ├── id: string
│   ├── invoiceNumber: string (رقم الفاتورة الفريد)
│   ├── productId: string (معرف المنتج)
│   ├── productName: string
│   ├── capacity: number (السعة المباعة)
│   ├── salePrice: number (سعر البيع)
│   ├── buyerName: string (اسم المشتري)
│   ├── buyerPhone: string (رقم هاتف المشتري)
│   ├── remainingAmount: number (المبلغ المتبقي - ذمم)
│   ├── oldBatteryCapacity: number (سعة البطارية القديمة)
│   ├── userId: string (معرف البائع)
│   ├── isDeleted: boolean (للحذف المنطقي)
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

**الفهارس:**
- `invoiceNumber` (للبحث السريع)
- `userId` + `createdAt` (لعرض فواتير البائع اليومية)
- `remainingAmount` (للبحث عن الذمم)
- `createdAt` (للترتيب الزمني)

**Sub-collection: payments**
```
Collection: invoices/{invoiceId}/payments
├── Document: {paymentId}
│   ├── id: string
│   ├── amount: number (مبلغ الدفعة)
│   ├── paymentDate: timestamp
│   ├── paymentMethod: string (نقد، شيك، تحويل)
│   ├── notes: string
│   └── createdAt: timestamp
```

---

### 4. **old_batteries** - البطاريات القديمة

تخزين بيانات البطاريات القديمة المرتجعة.

```
Collection: old_batteries
├── Document: {batteryId}
│   ├── id: string
│   ├── capacity: number (السعة بالأمبير)
│   ├── quantity: number (الكمية)
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

**الفهارس:**
- `createdAt` (للترتيب الزمني)

---

### 5. **bills** - الكمبيالات والشيكات

تخزين بيانات الكمبيالات والشيكات.

```
Collection: bills
├── Document: {billId}
│   ├── id: string
│   ├── description: string (الوصف)
│   ├── amount: number (القيمة)
│   ├── dueDate: timestamp (تاريخ الاستحقاق)
│   ├── status: string (PAID | UNPAID | OVERDUE | PARTIAL)
│   ├── billType: string (CHECK | BILL | TRANSFER | OTHER)
│   ├── paidDate: timestamp (تاريخ التسديد - اختياري)
│   ├── notes: string
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

**الفهارس:**
- `status` (للفرز حسب الحالة)
- `dueDate` (للتنبيهات)
- `createdAt` (للترتيب الزمني)

---

### 6. **expenses** - المصروفات

تخزين بيانات المصروفات.

```
Collection: expenses
├── Document: {expenseId}
│   ├── id: string
│   ├── description: string (وصف المصروف)
│   ├── amount: number (مبلغ المصروف)
│   ├── category: string (SALARY | UTILITIES | RENT | TRANSPORTATION | MAINTENANCE | SUPPLIES | ADVERTISING | INSURANCE | TAXES | OTHER)
│   ├── relatedBillId: string (معرف الكمبيالة المرتبطة - اختياري)
│   ├── notes: string
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

**الفهارس:**
- `category` (للفرز حسب الفئة)
- `createdAt` (للترتيب الزمني)

---

### 7. **transactions** - العمليات المحاسبية

تسجيل جميع العمليات المالية (واردة وصادرة).

```
Collection: transactions
├── Document: {transactionId}
│   ├── id: string
│   ├── type: string (INCOME | EXPENSE | PAYMENT | REFUND)
│   ├── amount: number (المبلغ)
│   ├── description: string (الوصف)
│   ├── relatedId: string (معرف الفاتورة أو المصروف - اختياري)
│   ├── notes: string
│   └── createdAt: timestamp
```

**الفهارس:**
- `type` (للفرز حسب النوع)
- `createdAt` (للترتيب الزمني)

---

### 8. **daily_sales_summary** - ملخص المبيعات اليومية

تخزين ملخص المبيعات لكل يوم.

```
Collection: daily_sales_summary
├── Document: {date}_{userId}
│   ├── id: string
│   ├── date: timestamp (تاريخ اليوم)
│   ├── totalSales: number (إجمالي المبيعات)
│   ├── totalInvoices: number (عدد الفواتير)
│   ├── totalDebts: number (إجمالي الذمم)
│   ├── totalOldBatteryCapacity: number (إجمالي سعة البطاريات القديمة)
│   ├── userId: string (معرف البائع)
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

**الفهارس:**
- `date` + `userId` (للبحث عن ملخص يوم معين لبائع معين)

---

### 9. **daily_accounting_summary** - ملخص المحاسبة اليومية

تخزين ملخص الواردات والمصروفات لكل يوم.

```
Collection: daily_accounting_summary
├── Document: {date}
│   ├── id: string
│   ├── date: timestamp (تاريخ اليوم)
│   ├── totalIncome: number (إجمالي الواردات)
│   ├── totalExpenses: number (إجمالي المصروفات)
│   ├── netAmount: number (الصافي)
│   ├── totalDebts: number (إجمالي الذمم المتبقية)
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
```

**الفهارس:**
- `date` (للبحث عن ملخص يوم معين)

---

## 🔐 قواعد الأمان (Security Rules)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // السماح للمستخدمين المصرح لهم فقط
    match /users/{userId} {
      allow read: if request.auth.uid == userId || isAdmin();
      allow write: if isAdmin();
    }

    match /products/{productId} {
      allow read: if request.auth != null;
      allow write: if isAdmin();
    }

    match /invoices/{invoiceId} {
      allow read: if request.auth != null;
      allow write: if isAdmin() || request.auth.uid == resource.data.userId;
      
      match /payments/{paymentId} {
        allow read: if request.auth != null;
        allow write: if isAdmin() || request.auth.uid == get(/databases/$(database)/documents/invoices/$(invoiceId)).data.userId;
      }
    }

    match /old_batteries/{batteryId} {
      allow read: if request.auth != null;
      allow write: if isAdmin();
    }

    match /bills/{billId} {
      allow read: if request.auth != null;
      allow write: if isAdmin();
    }

    match /expenses/{expenseId} {
      allow read: if request.auth != null;
      allow write: if isAdmin();
    }

    match /transactions/{transactionId} {
      allow read: if request.auth != null;
      allow write: if isAdmin();
    }

    match /daily_sales_summary/{document=**} {
      allow read: if request.auth != null;
      allow write: if isAdmin();
    }

    match /daily_accounting_summary/{document=**} {
      allow read: if request.auth != null;
      allow write: if isAdmin();
    }

    // دالة مساعدة للتحقق من أن المستخدم مدير
    function isAdmin() {
      return get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'ADMIN';
    }
  }
}
```

---

## 📈 استراتيجيات الفهرسة

### الفهارس المركبة المهمة:

1. **invoices** - `userId` + `createdAt` (للحصول على فواتير البائع اليومية)
2. **invoices** - `remainingAmount` + `createdAt` (للبحث عن الذمم)
3. **products** - `isArchived` + `quantity` (للتنبيهات)
4. **bills** - `status` + `dueDate` (للتنبيهات)
5. **expenses** - `category` + `createdAt` (للتقارير)

---

## 🔄 تدفق البيانات

### عملية البيع:
```
1. البائع يختار منتج من قائمة products
2. يتم إنشاء invoice جديد
3. يتم تقليل quantity في products
4. يتم تحديث daily_sales_summary
5. إذا كان هناك ذمم، يتم تسجيل remainingAmount
```

### تسديد الذمم:
```
1. يتم إضافة payment إلى sub-collection payments
2. يتم تحديث remainingAmount في invoice
3. يتم إنشاء transaction من نوع PAYMENT
4. يتم تحديث daily_accounting_summary
```

### إضافة مصروف:
```
1. يتم إنشاء expense جديد
2. يتم إنشاء transaction من نوع EXPENSE
3. يتم تحديث daily_accounting_summary
```

---

## 📋 ملاحظات مهمة

1. **الحذف المنطقي**: بدلاً من حذف البيانات مباشرة، يتم وضع علم `isDeleted` أو `isArchived`
2. **الفهارس**: يتم إنشاء الفهارس تلقائياً عند الحاجة
3. **الأمان**: جميع العمليات محمية بقواعد الأمان
4. **الأداء**: استخدام sub-collections للبيانات المرتبطة (مثل الدفعات)
5. **التقارير**: يتم استخدام المجموعات الملخصة لتسريع التقارير

---

## 🚀 الخطوات التالية

1. إعداد Firebase Project وتفعيل Firestore
2. تطبيق قواعد الأمان
3. إنشاء الفهارس المركبة
4. ربط التطبيق بـ Firebase
5. تطبيق عمليات CRUD في الـ Repository Layer
