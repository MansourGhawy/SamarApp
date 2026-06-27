package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val arabicLocale = Locale("ar")

    fun getDayOfWeekArabic(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        val sdf = SimpleDateFormat("EEEE", arabicLocale)
        return sdf.format(date) // "السبت", "الأحد", etc.
    }

    fun formatTime24Or12(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        val timePart = SimpleDateFormat("hh:mm", Locale.ENGLISH).format(date)
        val amPmPart = SimpleDateFormat("a", arabicLocale).format(date)
        return "$timePart $amPmPart" // "08:45 م"
    }

    fun formatDateFull(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        return sdf.format(date) // "2026-06-01"
    }

    // Returns a YearMonth (e.g. "2026-06")
    fun getYearMonthKey(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        val sdf = SimpleDateFormat("yyyy-MM", Locale.ENGLISH)
        return sdf.format(date)
    }

    fun getDayOfMonth(timestampSec: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestampSec * 1000
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    fun getMonthNameArabic(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        val monthPart = SimpleDateFormat("MMMM", arabicLocale).format(date)
        val yearPart = SimpleDateFormat("yyyy", Locale.ENGLISH).format(date)
        return "$monthPart $yearPart"
    }

    fun formatDurationBetween(newerSec: Long, olderSec: Long, context: android.content.Context? = null): String {
        val diffSec = (newerSec - olderSec).coerceAtLeast(0)
        val days = diffSec / (24 * 3600)
        val remainingAfterDays = diffSec % (24 * 3600)
        val hours = remainingAfterDays / 3600

        return when {
            days > 30 -> context?.getString(com.example.R.string.date_diff_over_month) ?: "منذ أكثر من شهر"
            days > 1 -> context?.getString(com.example.R.string.date_diff_days_pattern, days) ?: "بفارق $days يوماً"
            days == 1L -> context?.getString(com.example.R.string.date_diff_one_day) ?: "بفارق يوم واحد"
            hours > 1 -> context?.getString(com.example.R.string.date_diff_hours_pattern, hours) ?: "بفارق $hours ساعة"
            else -> context?.getString(com.example.R.string.date_diff_very_close) ?: "متقاربان جداً"
        }
    }
}

fun String.toEnglishDigits(): String {
    var result = this
    val arabicIndicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    val westernDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    for (i in 0..9) {
        result = result.replace(arabicIndicDigits[i], westernDigits[i])
    }
    return result
}
