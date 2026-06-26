package com.example.domain

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.R

fun extractEmoji(category: String, defaultEmoji: String): String {
    val cat = category.lowercase()
    return when {
        cat.contains("دقيق") || cat.contains("🌾") -> "🌾"
        cat.contains("غاز") || cat.contains("🔥") -> "🔥"
        cat.contains("كهرباء") || cat.contains("⚡") -> "⚡"
        cat.contains("ماء") || cat.contains("💧") -> "💧"
        cat.contains("حليب") || cat.contains("🍼") -> "🍼"
        cat.contains("حفاظ") || cat.contains("👶") -> "👶"
        cat.contains("سكر") || cat.contains("🍬") -> "🍬"
        cat.contains("شاي") || cat.contains("☕") -> "☕"
        cat.contains("نت") || cat.contains("رصيد") || cat.contains("🌐") || cat.contains("إنترنت") -> "🌐"
        cat.contains("مدرس") || cat.contains("🎒") -> "🎒"
        cat.contains("ادخار") || cat.contains("🏦") -> "🏦"
        cat.contains("طوارئ") || cat.contains("🚨") -> "🚨"
        cat.contains("علاج") || cat.contains("💊") -> "💊"
        cat.contains("أثاث") || cat.contains("🛋️") -> "🛋️"
        else -> {
            // Check if there is already an emoji in the string
            val emojiRegex = "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+".toRegex()
            val match = emojiRegex.find(category)
            match?.value ?: defaultEmoji
        }
    }
}

fun getEmojiBgColor(emoji: String): Color {
    return when (emoji) {
        "🌾" -> Color(0xFFFEF3C7) // amber-100 (🌾)
        "🍬", "🍭" -> Color(0xFFFCE7F3) // pink-100 (🍬)
        "☕" -> Color(0xFFEFEFEF) // gray-100 (☕)
        "🔥" -> Color(0xFFFEE2E2) // red-100 (🔥)
        "⚡" -> Color(0xFFFEF9C3) // yellow-100 (⚡)
        "💧" -> Color(0xFFDBEAFE) // blue-100 (💧)
        "🚀", "🌐" -> Color(0xFFE0F2FE) // sky-100 (🌐)
        "🍼", "👶" -> Color(0xFFF3E8FF) // purple-100 (👶, 🍼)
        "🎒" -> Color(0xFFE0F2FE) // sky-100 (🎒)
        "🏦" -> Color(0xFFD1FAE5) // emerald-100 (🏦)
        "🚨" -> Color(0xFFFFE4E6) // rose-100 (🚨)
        "💊" -> Color(0xFFFCE7F3) // pink-100 (💊)
        "🛋️" -> Color(0xFFF3F4F6) // gray-100 (🛋️)
        "💰" -> Color(0xFFECFDF5) // green-50
        else -> Color(0xFFF1F5F9) // slate-100
    }
}

fun getAuditLogGroupDate(timestampMs: Long, context: Context? = null): String {
    val logCal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
    val todayCal = java.util.Calendar.getInstance()
    val yesterdayCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val dayBeforeCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -2) }

    val isSameDay = logCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
            logCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)

    val isYesterday = logCal.get(java.util.Calendar.YEAR) == yesterdayCal.get(java.util.Calendar.YEAR) &&
            logCal.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCal.get(java.util.Calendar.DAY_OF_YEAR)

    val isDayBefore = logCal.get(java.util.Calendar.YEAR) == dayBeforeCal.get(java.util.Calendar.YEAR) &&
            logCal.get(java.util.Calendar.DAY_OF_YEAR) == dayBeforeCal.get(java.util.Calendar.DAY_OF_YEAR)

    return when {
        isSameDay -> context?.getString(R.string.ledger_day_today) ?: "اليوم"
        isYesterday -> context?.getString(R.string.ledger_day_yesterday) ?: "الأمس"
        isDayBefore -> context?.getString(R.string.ledger_day_before_yesterday) ?: "أول أمس"
        else -> {
            val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale("ar")).format(java.util.Date(timestampMs))
            val dateNumbers = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.ENGLISH).format(java.util.Date(timestampMs))
            "$dayName، $dateNumbers"
        }
    }
}

fun formatAuditLogTime(timestampMs: Long): String {
    val date = java.util.Date(timestampMs)
    val datePart = java.text.SimpleDateFormat("dd-MM-yyyy | hh:mm", java.util.Locale.ENGLISH).format(date)
    val amPmPart = java.text.SimpleDateFormat("a", java.util.Locale("ar")).format(date)
    return "$datePart $amPmPart"
}
