package com.batterysales.data.models

data class CustomerSummary(
    val lastOrderDate: String = "",
    val totalSalesToDate: Double = 0.0,
    val recentInvoicesSummary: List<RecentInvoiceSummary> = emptyList()
)

data class RecentInvoiceSummary(
    val orderId: String = "",
    val total: Double = 0.0,
    val status: String = ""
)

data class SalesInvoice(
    val orderId: String = "",
    val customerId: String = "",
    val timestamp: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val status: String = "PENDING_PROCESSING"
)
