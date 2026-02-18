package com.batterysales.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
        WebView.enableSlowWholeDocumentDraw()

        val webView = WebView(context)
        activeWebView = webView

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
        }

        val displayMetrics = context.resources.displayMetrics
        val widthPx = (595 * displayMetrics.density).toInt().coerceAtLeast(600)

        webView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val heightPx = webView.measuredHeight.coerceAtLeast((842 * displayMetrics.density * 3).toInt())
        webView.layout(0, 0, widthPx, heightPx)
        webView.setBackgroundColor(Color.WHITE)

        // ──────────────────────────────────────────────────────────────
        // الجزء الجديد: أضف WebView مخفيًا إلى الشاشة لإجبار الرسم
        // ──────────────────────────────────────────────────────────────
        val rootView = (context as? Activity)?.window?.decorView as? ViewGroup
        if (rootView != null) {
            webView.visibility = View.INVISIBLE
            rootView.addView(webView, 0, ViewGroup.LayoutParams(widthPx, heightPx))
            Log.d("PrintUtils", "تم إضافة WebView مخفي إلى الـ root view لإجبار الـ rendering")
        } else {
            Log.w("PrintUtils", "لم نتمكن من إضافة WebView إلى root – قد يبقى contentHeight 0")
        }

        val reportsDir = File(context.cacheDir, "reports")
        reportsDir.mkdirs()

        val fileName = "SupplierReport_${item.supplier.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(reportsDir, fileName)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                Log.d("PrintUtils", "onPageFinished → تم التحميل، ننتظر 1500ms + فحص JS")

                view.postDelayed({
                    Log.d("PrintUtils", "بدء فحص JS بعد تأخير كبير")
                    checkJsHeightAndGenerate(view, file, context, rootView)
                }, 1500)  // تأخير أكبر لإعطاء وقت للرسم بعد الإضافة إلى الشاشة
            }
        }

        webView.loadDataWithBaseURL(null, generateSupplierHtml(item, SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())), "text/html", "utf-8", null)
    }

    // دالة فحص JS مع إزالة WebView بعد الانتهاء
    private fun checkJsHeightAndGenerate(webView: WebView, file: File, context: Context, rootView: ViewGroup?) {
        webView.evaluateJavascript(
            """
        (function() {
            return Math.max(
                document.body.scrollHeight,
                document.documentElement.scrollHeight,
                document.body.offsetHeight,
                document.documentElement.offsetHeight,
                document.body.clientHeight,
                document.documentElement.clientHeight
            );
        })();
        """
        ) { result ->
            val heightStr = result?.replace("\"", "")?.trim() ?: "0"
            val jsHeight = heightStr.toIntOrNull() ?: 0

            Log.d("PrintUtils", "ارتفاع من JS: $jsHeight px (raw result: $result)")

            if (jsHeight > 400) {
                // تحديث الـ layout بناءً على الارتفاع الفعلي
                webView.layout(0, 0, webView.width, (jsHeight * webView.width / 595).coerceAtLeast(842))
                Log.d("PrintUtils", "تم تحديث layout إلى ارتفاع $jsHeight px → توليد PDF")
                generatePdfFromWebView(webView, file, context)
            } else {
                Log.w("PrintUtils", "JS height منخفض جدًا ($jsHeight) – نحاول على أي حال")
                generatePdfFromWebView(webView, file, context)
            }

            // إزالة WebView من الشاشة لتحرير الموارد
            rootView?.removeView(webView)
            activeWebView = null
        }
    }

    // دالة منفصلة لفحص الاستقرار + استخدام JS كبديل
    private fun checkAndGenerateWhenStable(webView: WebView, file: File, context: Context) {
        val runnable = object : Runnable {
            private var lastHeight = 0
            private var stableCount = 0
            private var attempts = 0
            private val maxAttempts = 30  // ~6 ثوانٍ

            override fun run() {
                attempts++
                var currentHeight = webView.contentHeight

                // محاولة الحصول على الارتفاع الحقيقي عبر JavaScript (أكثر دقة في كثير من الحالات)
                if (webView.settings.javaScriptEnabled) {
                    webView.evaluateJavascript(
                        "(function() { return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, document.body.offsetHeight, document.documentElement.offsetHeight); })();"
                    ) { result ->
                        try {
                            val jsHeight = result?.toString()?.toIntOrNull() ?: 0
                            if (jsHeight > currentHeight) {
                                currentHeight = jsHeight
                                Log.d("PrintUtils", "ارتفاع من JS: $jsHeight (أفضل من contentHeight=$currentHeight)")
                            }
                        } catch (ignored: Exception) { }
                        processHeight(currentHeight)
                    }
                } else {
                    processHeight(currentHeight)
                }
            }

            private fun processHeight(height: Int) {
                Log.d("PrintUtils", "محاولة #$attempts | contentHeight=$height | stable=$stableCount")

                if (height > 300 && height == lastHeight) {
                    stableCount++
                    if (stableCount >= 3) {
                        Log.d("PrintUtils", "استقرار مؤكد عند $height → توليد PDF الآن")
                        generatePdfFromWebView(webView, file, context)
                        activeWebView = null
                        return
                    }
                } else {
                    stableCount = 0
                }

                lastHeight = height

                if (attempts < maxAttempts) {
                    webView.postDelayed(this, 200)
                } else {
                    Log.w("PrintUtils", "انتهت المحاولات، نحاول توليد PDF رغم عدم الاستقرار الكامل")
                    generatePdfFromWebView(webView, file, context)
                    activeWebView = null
                }
            }
        }

        webView.post(runnable)
    }

    private fun generatePdfFromWebView(webView: WebView, file: File, context: Context) {
        val pdfDocument = PdfDocument()

        // A4 size
        val pageWidth = 595
        val pageHeight = (webView.contentHeight * pageWidth / webView.width).coerceAtLeast(842)

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas = page.canvas
        val scale = pageWidth.toFloat() / webView.width.toFloat()
        canvas.scale(scale, scale)

        Log.d("PrintUtils", "قبل الرسم → width=${webView.width}, contentHeight=${webView.contentHeight}, pageHeight=$pageHeight")

        webView.draw(canvas)

        pdfDocument.finishPage(page)

        try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            Log.d("PrintUtils", "PDF تم حفظه → ${file.absolutePath} | حجم=${file.length()} بايت")

            if (file.length() < 1000) {
                Toast.makeText(context, "تم إنشاء PDF لكنه فارغ (حجم ${file.length()} بايت)", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "تم إنشاء PDF (${file.length() / 1024} KB)", Toast.LENGTH_SHORT).show()
            }

            sharePdfFile(context, file)
        } catch (e: Exception) {
            Log.e("PrintUtils", "خطأ أثناء حفظ PDF", e)
            Toast.makeText(context, "فشل في حفظ ملف PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sharePdfFile(context: Context, file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "الملف غير موجود للمشاركة", Toast.LENGTH_SHORT).show()
                return
            }

            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "مشاركة التقرير")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("PrintUtils", "Error sharing PDF", e)
            Toast.makeText(context, "فشل في مشاركة الملف: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateSupplierHtml(item: SupplierReportItem, dateFormatter: SimpleDateFormat): String {
        // (نفس الكود الأصلي بالكامل - لم أغيره)
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
                    <p><strong>المتبقي:</strong> JD ${String.format("%.3f", item.balance)}</p>
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
                    <p>تم استخراج هذا التقرير من نظام العصرية لإدارة مبيعات البطاريات</p>
                </div>
            </body>
            </html>
        """.trimIndent())
        return htmlContent.toString()
    }

    fun printSupplierReport(context: Context, item: SupplierReportItem) {
        // (نفس الكود الأصلي - لم أغيره)
        val webView = WebView(context)
        activeWebView = webView

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