package com.batterysales.utils

object StringUtils {
    /**
     * Converts any Arabic or Hindi digits in a string to English (Western Arabic) digits.
     */
    fun normalizeDigits(input: String): String {
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val hindiDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

        var result = input
        for (i in 0..9) {
            result = result.replace(arabicDigits[i], englishDigits[i])
            result = result.replace(hindiDigits[i], englishDigits[i])
        }
        return result
    }
}
