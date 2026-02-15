package com.batterysales.utils

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.batterysales.viewmodel.SupplierReportItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object PrintUtils {

    fun shareSupplierReport(context: Context, item: SupplierReportItem) {
        val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val fileName = "SupplierReport_${item.supplier.name.replace(" ", "_")}_${System.currentTimeMillis()}.html"
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val file = File(reportsDir, fileName)
        
        val htmlContent = generateSupplierHtml(item, dateFormatter)
        
        try {
            file.writeText(htmlContent)

            // Dynamic authority based on package name
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "مشاركة التقرير")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Toast.makeText(context, "جاري تحضير ملف المشاركة...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل في مشاركة الملف: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateSupplierHtml(item: SupplierReportItem, dateFormatter: SimpleDateFormat): String {
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
                            <th>رقم الفاتورة</th>
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
                    <td>${po.entry.invoiceNumber}</td>
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
        return htmlContent.toString()
    }

    fun printSupplierReport(context: Context, item: SupplierReportItem) {
        val webView = WebView(context)
        val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val htmlContent = generateSupplierHtml(item, dateFormatter)

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
