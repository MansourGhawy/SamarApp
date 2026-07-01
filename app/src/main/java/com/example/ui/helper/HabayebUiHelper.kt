package com.example.ui.helper

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Pastel Colors for Initials
val PastelColors = listOf(
    Color(0xFFFCA5A5), Color(0xFFFDBA74), Color(0xFFFDE047),
    Color(0xFF86EFAC), Color(0xFF93C5FD), Color(0xFFC4B5FD),
    Color(0xFFF472B6), Color(0xFF2DD4BF)
)

fun getInitialColor(name: String): Color {
    val hash = name.hashCode().coerceAtLeast(0)
    return PastelColors[hash % PastelColors.size]
}

fun formatCurrency(amount: Double, currencySymbol: String): String {
    val absVal = kotlin.math.abs(amount)
    val symbols = DecimalFormatSymbols(Locale.ENGLISH)
    val formatter = DecimalFormat("#,##0", symbols)
    val sign = ""
    return "$sign${formatter.format(absVal)} $currencySymbol"
}

@Composable
fun AutoScaleText(
    text: String,
    baseFontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1
) {
    val adjustedFontSize = when {
        text.length > 18 -> baseFontSize * 0.70f
        text.length > 13 -> baseFontSize * 0.82f
        else -> baseFontSize
    }
    Text(
        text = text,
        color = color,
        fontSize = adjustedFontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
