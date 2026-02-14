package com.batterysales.utils

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.batterysales.viewmodel.SupplierReportItem
import java.text.SimpleDateFormat
import java.util.*

object PrintUtils {

    fun printSupplierReport(context: Context, item: SupplierReportItem) {
        val webView = WebView(context)
        val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        val htmlContent = StringBuilder()
        htmlContent.append("""
            <html dir="rtl" lang="ar">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: sans-serif; padding: 20px; }
                    .header { text-align: center; border-bottom: 2px solid #000; padding-bottom: 10px; margin-bottom: 20px; }
                    .info { margin-bottom: 20px; }
                    .info p { margin: 5px 0; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    th, td { border: 1px solid #ddd; padding: 12px; text-align: right; }
                    th { background-color: #f2f2f2; }
                    .total-row { font-weight: bold; background-color: #eee; }
                    .footer { margin-top: 30px; text-align: center; font-size: 12px; color: #666; }
                    .badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
                    .badge-red { background-color: #ffebee; color: #c62828; }
                    .badge-green { background-color: #e8f5e9; color: #2e7d32; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h2>تقرير كشف حساب مورد</h2>
                    <h3>${item.supplier.name}</h3>
                </div>

                <div class="info">
                    <p><strong>تاريخ التقرير:</strong> ${dateFormatter.format(Date())}</p>
                    <p><strong>رصيد المورد:</strong> JD ${String.format("%.3f", item.balance)}</p>
                    <p><strong>إجمالي المسحوبات:</strong> JD ${String.format("%.3f", item.totalDebit)}</p>
                    <p><strong>إجمالي المدفوعات:</strong> JD ${String.format("%.3f", item.totalCredit)}</p>
                </div>

                <h3>تفاصيل طلبيات الشراء</h3>
                <table>
                    <thead>
                        <tr>
                            <th>التاريخ</th>
                            <th>القيمة الإجمالية</th>
                            <th>المدفوع</th>
                            <th>المتبقي</th>
                            <th>المراجع</th>
                        </tr>
                    </thead>
                    <tbody>
        """.trimIndent())

        item.purchaseOrders.forEach { po ->
            htmlContent.append("""
                <tr>
                    <td>${dateFormatter.format(po.entry.timestamp)}</td>
                    <td>JD ${String.format("%.3f", po.entry.totalCost)}</td>
                    <td>JD ${String.format("%.3f", po.linkedPaidAmount)}</td>
                    <td>JD ${String.format("%.3f", po.remainingBalance)}</td>
                    <td>${po.referenceNumbers.joinToString("<br>")}</td>
                </tr>
            """.trimIndent())
        }

        htmlContent.append("""
                    </tbody>
                    <tfoot>
                        <tr class="total-row">
                            <td>الإجمالي</td>
                            <td>JD ${String.format("%.3f", item.totalDebit)}</td>
                            <td>JD ${String.format("%.3f", item.totalCredit)}</td>
                            <td>JD ${String.format("%.3f", item.balance)}</td>
                            <td></td>
                        </tr>
                    </tfoot>
                </table>

                <div class="footer">
                    <p>تم استخراج هذا التقرير من نظام الأصصرية لإدارة مبيعات البطاريات</p>
                </div>
            </body>
            </html>
        """.trimIndent())

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("تقرير المورد - ${item.supplier.name}")
                val jobName = "SupplierReport_${item.supplier.name}"
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            }
        }

        webView.loadDataWithBaseURL(null, htmlContent.toString(), "text/html", "utf-8", null)
    }
}
