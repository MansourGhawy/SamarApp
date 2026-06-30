package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Mizan Al-Dar Color Palette
val EmeraldPrimary = Color(0xFF0F5257)      // Deep Warm Emerald Green (Security and growth)
val EmeraldLight = Color(0xFF1B7A7D)        // Lighter Emerald for gradients
val CoralAccent = Color(0xFFFF7B54)        // Warm Soft Coral (Action and vivid touch)
val IvoryBackground = Color(0xFFFBFBF9)    // Soft Peaceful Ivory (Eye safe background)

val SoftRed = Color(0xFFD9534F)            // Warm Soft Red (Incomplete or Expense)
val SoftGreen = Color(0xFF5CB85C)          // Warm Soft Green (Complete or Income)

val DarkBackground = Color(0xFF121210)     // Soft Midnight Warm Dark Background
val DarkSurface = Color(0xFF1E1E1C)        // Warm dark card surface
val LightSurface = Color(0xFFFFFFFF)       // Clean white card surface

val TextPrimaryDark = Color(0xFFE3E3DE)
val TextSecondaryDark = Color(0xFF9E9E97)
val TextPrimaryLight = Color(0xFF2C322E)
val TextSecondaryLight = Color(0xFF6F7671)

val PrimaryGradient = Brush.linearGradient(
    colors = listOf(EmeraldPrimary, EmeraldLight)
)

val CoralGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFF9171), CoralAccent)
)

