package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Mizan Al-Dar Color Palette - Now styled with the premium Violet & Neon Cyan "الدفتر الذكي" Palette
val EmeraldPrimary = Color(0xFF5D2EEC)      // Premium Glowing Violet/Purple
val EmeraldLight = Color(0xFF865CFF)        // Lighter Purple/Indigo for beautiful gradients
val CoralAccent = Color(0xFF00E5FF)         // Neon Cyan / Electric Blue Accent
val IvoryBackground = Color(0xFFF5F6FC)     // Soft modern lavender-tinted background (Eye safe)

val SoftRed = Color(0xFFE05252)            // Warm Soft Red (Incomplete or Expense)
val SoftGreen = Color(0xFF3CD070)          // Vibrant Soft Green (Complete or Income)

val DarkBackground = Color(0xFF0B081F)     // Luxury Midnight Indigo/Violet Background
val DarkSurface = Color(0xFF161233)        // Deep indigo-purple card surface
val LightSurface = Color(0xFFFFFFFF)       // Clean white card surface

val TextPrimaryDark = Color(0xFFE2E0FF)     // Light lavender-gray primary text
val TextSecondaryDark = Color(0xFF9C99CD)   // Muted lavender-gray secondary text
val TextPrimaryLight = Color(0xFF1E1B4B)    // Deep indigo-slate primary text
val TextSecondaryLight = Color(0xFF5C58A5)  // Muted purple-slate secondary text

val PrimaryGradient = Brush.linearGradient(
    colors = listOf(EmeraldPrimary, EmeraldLight)
)

val CoralGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF00E5FF), Color(0xFF007BFF))
)

