package com.example.ui.screens.habayeb.utils

import com.example.data.local.entities.HabayebTransaction

data class Currency(
    val code: String,
    val symbol: String,
    val arabicName: String,
    val flagEmoji: String
)

object CurrencyConfig {
    val currencies = listOf(
        Currency("YER", "ر.ي", "ريال يمني", "🇾🇪"),
        Currency("SAR", "ر.س", "ريال سعودي", "🇸🇦"),
        Currency("USD", "$", "دولار أمريكي", "🇺🇸"),
        Currency("EUR", "€", "يورو", "🇪🇺"),
        Currency("AED", "د.إ", "درهم إماراتي", "🇦🇪")
    )

    fun getBySymbol(symbol: String): Currency? =
        currencies.find { it.symbol == symbol || it.code == symbol }

    fun getByCode(code: String): Currency? =
        currencies.find { it.code == code }

    /**
     * Extracts the currency symbol and clean description from a transaction's description.
     * If no currency is tagged, returns the provided defaultCurrencySymbol.
     */
    fun parseTransactionCurrency(description: String, defaultCurrencySymbol: String): Pair<String, String> {
        // Look for [Symbol] pattern at the beginning
        for (currency in currencies) {
            val tag = "[${currency.symbol}]"
            if (description.startsWith(tag)) {
                val cleanDesc = description.substring(tag.length).trim()
                return Pair(currency.symbol, cleanDesc)
            }
        }
        return Pair(defaultCurrencySymbol, description)
    }

    /**
     * Helper to wrap a transaction description with a currency tag.
     */
    fun formatDescriptionWithCurrency(description: String, symbol: String): String {
        return "[$symbol] $description"
    }

    /**
     * Normalizes Arabic and Farsi digits to Western Arabic (English) digits, and replaces commas with dots.
     */
    fun normalizeDigits(input: String): String {
        var result = input.replace(',', '.')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val farsiDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        for (i in 0..9) {
            result = result.replace(arabicDigits[i], (i + '0'.code).toChar())
            result = result.replace(farsiDigits[i], (i + '0'.code).toChar())
        }
        return result
    }
}
