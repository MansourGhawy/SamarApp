package com.example.domain

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Shared utility functions for text normalization, particularly for Arabic language character mapping.
 */
object StringUtils {
    @JvmStatic
    fun normalizeArabic(text: String): String {
        return text.replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .trim()
    }
}

/**
 * Shared formatting utilities for displaying monetary values/currency.
 */
object FormatUtils {
    @JvmStatic
    fun formatCurrency(amount: BigDecimal, symbol: String = "ر.ي"): String {
        return try {
            val symbols = DecimalFormatSymbols(Locale.ENGLISH)
            val formatter = DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        } catch (e: Exception) {
            val symbols = DecimalFormatSymbols(Locale.ENGLISH)
            val formatter = DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        }
    }

    @JvmStatic
    fun formatDoubleCurrency(amount: Double, symbol: String = "ر.ي"): String {
        return try {
            val symbols = DecimalFormatSymbols(Locale.ENGLISH)
            val formatter = DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        } catch (e: Exception) {
            val symbols = DecimalFormatSymbols(Locale.ENGLISH)
            val formatter = DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        }
    }
}
