package com.batterysales.data.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * نموذج الكمبيالة/الشيك
 *
 * يمثل كمبيالة أو شيك واحد
 */
data class Bill(
//    @DocumentId
    val id: String = "",
    val description: String = "", // الوصف
    val amount: Double = 0.0, // القيمة
    val paidAmount: Double = 0.0, // المبلغ المدفوع
    val dueDate: Date = Date(), // تاريخ الاستحقاق
    val status: BillStatus = BillStatus.UNPAID, // الحالة
    val billType: BillType = BillType.CHECK, // نوع الكمبيالة (شيك، كمبيالة، إلخ)
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val paidDate: Date? = null, // تاريخ التسديد
    val notes: String = "",
    val referenceNumber: String = "", // رقم السند/الشيك
    val supplierId: String = "", // Link to Supplier model
    val relatedEntryId: String? = null // Link to a specific StockEntry (Purchase)
) {
    companion object {
        const val COLLECTION_NAME = "bills"
    }
}

/**
 * حالات الكمبيالة
 */
enum class BillStatus {
    PAID,      // مسدد
    UNPAID,    // غير مسدد
    OVERDUE,   // متأخر
    PARTIAL    // مسدد جزئياً
}

/**
 * أنواع الكمبيالات
 */
enum class BillType {
    CHECK,     // شيك
    BILL,      // كمبيالة
    TRANSFER,  // تحويل
    OTHER      // أخرى
}
