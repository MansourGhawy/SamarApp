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
        val sdf = SimpleDateFormat("hh:mm a", arabicLocale)
        return sdf.format(date) // "08:45 م"
    }

    fun formatDateFull(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        val sdf = SimpleDateFormat("yyyy-MM-dd", arabicLocale)
        return sdf.format(date) // "2026-06-01"
    }

    // Returns a YearMonth (e.g. "2026-06")
    fun getYearMonthKey(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(date)
    }

    fun getDayOfMonth(timestampSec: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestampSec * 1000
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    fun getMonthNameArabic(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        val sdf = SimpleDateFormat("MMMM yyyy", arabicLocale)
        return sdf.format(date)
    }

    fun formatDurationBetween(newerSec: Long, olderSec: Long): String {
        val diffSec = (newerSec - olderSec).coerceAtLeast(0)
        val days = diffSec / (24 * 3600)
        val remainingAfterDays = diffSec % (24 * 3600)
        val hours = remainingAfterDays / 3600

        return when {
            days > 30 -> "منذ أكثر من شهر"
            days > 1 -> "بفارق $days يوماً"
            days == 1L -> "بفارق يوم واحد"
            hours > 1 -> "بفارق $hours ساعة"
            else -> "متقاربان جداً"
        }
    }
}
