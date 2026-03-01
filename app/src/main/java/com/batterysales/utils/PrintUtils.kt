package com.batterysales.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.batterysales.viewmodel.SupplierReportItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PrintUtils {
    private var activeWebView: WebView? = null // Prevent GC during PDF generation

    fun shareSupplierReport(context: Context, item: SupplierReportItem) {
        // Enable drawing the whole document for high-quality PDF
        WebView.enableSlowWholeDocumentDraw()
        
        val webView = WebView(context)
        activeWebView = webView
        
        val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val htmlContent = generateSupplierHtml(item, dateFormatter)
        
        Toast.makeText(context, "جاري تحضير ملف PDF للمشاركة...", Toast.LENGTH_SHORT).show()

        // Important: WebView needs to be laid out to have a size for drawing
        webView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        webView.layout(0, 0, 1200, webView.measuredHeight.coerceAtLeast(1800))
        webView.setBackgroundColor(android.graphics.Color.WHITE)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                android.util.Log.d("PrintUtils", "onPageFinished: WebView loaded successfully")
                val reportsDir = File(context.cacheDir, "reports")
                if (!reportsDir.exists()) {
                    val created = reportsDir.mkdirs()
                    android.util.Log.d("PrintUtils", "Creating reports directory: $created at ${reportsDir.absolutePath}")
                    // Ensure directory is accessible
                    reportsDir.setReadable(true, false)
                    reportsDir.setWritable(true, false)
                }
                
                val fileName = "SupplierReport_${item.supplier.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                val file = File(reportsDir, fileName)
                
                // Reduced delay to improve response speed
                view.postDelayed({
                    try {
                        android.util.Log.d("PrintUtils", "Starting PDF generation. WebView height: ${view.contentHeight}")
                        generatePdfFromWebView(view, file, context)
                        activeWebView = null
                        android.util.Log.d("PrintUtils", "PDF generation completed and shared")
                    } catch (e: Exception) {
                        android.util.Log.e("PrintUtils", "Error during PDF generation", e)
                        Toast.makeText(context, "فشل في إنشاء ملف PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                        activeWebView = null
                    }
                }, 1000)
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
    }

    private fun generatePdfFromWebView(webView: WebView, file: File, context: Context) {
        val pdfDocument = PdfDocument()
        
        // A4 size in points (1/72 inch)
        val width = 595 
        val height = (webView.contentHeight * 595 / webView.width).coerceAtLeast(842)
        
        val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        
        val canvas = page.canvas
        val scale = width.toFloat() / webView.width.toFloat()
        canvas.scale(scale, scale)
        
        webView.draw(canvas)
        pdfDocument.finishPage(page)
        
        try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()
            sharePdfFile(context, file)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل في حفظ ملف PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePdfFile(context: Context, file: File) {
        try {
            if (!file.exists()) {
                android.util.Log.e("PrintUtils", "File does not exist: ${file.absolutePath}")
                Toast.makeText(context, "الملف غير موجود للمشاركة", Toast.LENGTH_SHORT).show()
                return
            }
            
            val authority = "${context.packageName}.fileprovider"
            android.util.Log.d("PrintUtils", "Getting URI for file: ${file.absolutePath} with authority: $authority")
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(shareIntent, "مشاركة التقرير").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            android.util.Log.d("PrintUtils", "Share intent started successfully")
        } catch (e: Exception) {
            android.util.Log.e("PrintUtils", "Error sharing PDF", e)
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
        activeWebView = webView // Prevent GC
        
        val dateFormatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val htmlContent = generateSupplierHtml(item, dateFormatter)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("تقرير المورد - ${item.supplier.name}")
                val jobName = "SupplierReport_${item.supplier.name}"
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                // activeWebView remains set until next call or could be cleared after a delay
            }
        }

        webView.loadDataWithBaseURL(null, htmlContent.toString(), "text/html", "utf-8", null)
    }
}
