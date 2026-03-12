package com.batterysales.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object BarcodeUtils {

    fun generateBarcodeBitmap(
        content: String,
        format: BarcodeFormat = BarcodeFormat.CODE_128,
        width: Int = 500,
        height: Int = 200
    ): Bitmap? {
        if (content.isEmpty()) return null
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, format, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateDataMatrixBitmap(
        content: String,
        width: Int = 300,
        height: Int = 300
    ): Bitmap? {
        return generateBarcodeBitmap(content, BarcodeFormat.DATA_MATRIX, width, height)
    }

    fun generateQrCodeBitmap(
        content: String,
        width: Int = 300,
        height: Int = 300
    ): Bitmap? {
        return generateBarcodeBitmap(content, BarcodeFormat.QR_CODE, width, height)
    }
}
