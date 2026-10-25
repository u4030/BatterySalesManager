# 🚀 دليل رفع وتشغيل Cloud Functions والمعمارية الجديدة (CQRS Architecture)

هذا الدليل يوضح خطوة بخطوة كيفية رفع وتشغيل موديول **Firebase Cloud Functions** المستحدث، وكيفية عمل المعمارية الجديدة ذات الأداء العالي والتكلفة المنخفضة (Server-Driven & CQRS).

---

## 📋 المتطلبات الأساسية

1. **Node.js**: إصدار 20 (أو أحدث).
2. **Firebase CLI**:
   ```bash
   npm install -g firebase-tools
   ```

---

## 🛠️ الخطوات التفصيلية للتثبيت والرفع

### الخطوة 1: تسجيل الدخول واختيار المشروع
```bash
firebase login
firebase use <your-firebase-project-id>
```

### الخطوة 2: تثبيت حزم الموديول وبنائه
قم بالدخول إلى مجلد `functions` وتثبيت الاعتمادات وبناء كود TypeScript:
```bash
cd functions
npm install
npm run build
```

### الخطوة 3: التجربة المحلية (اختياري عبر Firebase Emulator)
لاختبار العمليات والمشغلات محلياً على جهازك قبل الرفع الفعلي:
```bash
npm run serve
# أو عبر الأمر:
firebase emulators:start --only functions,firestore
```

### الخطوة 4: الرفع السحابي لـ Cloud Functions
لرفع المشغلات والمحسّن الموحد إلى سيرفرات جوجل Firebase Cloud Functions (Node.js 20 - v2):
```bash
firebase deploy --only functions
```

---

## ⚡ كيف تعمل المعمارية الجديدة (CQRS & Server-Driven Architecture)؟

1. **الكتابة من التطبيق (Single Write Strategy)**:
   - عندما تقوم بأي عملية بيع، إضافة مخزون، أو حركة خزينة/بنك، يقوم التطبيق بكتابة المستند في المجموعة الأساسية فقط (مثل `invoices` أو `stock_entries`).

2. **التنفيذ التلقائي على الخادم (Firestore Triggers)**:
   - تعمل مشغلات السيرفر المباشرة تلقائياً عند إضافة/تحديث/حذف أي مستند:
     - `onInvoiceCreated` / `onInvoiceUpdated` / `onInvoiceDeleted`
     - `onStockEntryCreated` / `onStockEntryUpdated` / `onStockEntryDeleted`
     - `onTransactionCreated` / `onTransactionUpdated` / `onTransactionDeleted`
     - `onBankTransactionCreated` / `onBillCreated` / `onVariantCreated`
   - يقوم السيرفر بإجراء كافة الحسابات والتجميعات وإعادة حساب الموازين والمخزون، وضخ النتيجة مباشرة في مستند القراءة الموحد: `/summaries/dashboard_hub`.

3. **تصفير استهلاك Firestore Quota على أندرويد (90%+ Reduction)**:
   - **لوحة التحكم (DashboardScreen)**: تقرأ مستنداً واحداً فقط وهو `/summaries/dashboard_hub` بـ (1 Read) وتستمع للحديثات اللحظية.
   - **الشاشات التاريخية (الفواتير، القيود، الخزينة، البنك)**: تعتمد نظام الصفحات المحدودة (`.limit(20)`) وتستمع لرمز التزامن اللحظي `/summaries/sync_registry`. وعند تغير إصدار البيانات على السيرفر، تقوم بعمل إنعاش صامت للصفحة الأولى فقط.
