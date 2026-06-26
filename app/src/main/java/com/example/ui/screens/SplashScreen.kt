package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun TheMasterSplashScreen(
    onSplashFinished: () -> Unit
) {
    // Elegant breathing delay - 2500ms
    LaunchedEffect(Unit) {
        delay(2500)
        onSplashFinished()
    }

    // Modern Deep Night Matte Dark Background
    val deepMatteDark = Color(0xFF0F172A) // Premium slate-900 security look

    // Core Animation loop for high refresh rates (120Hz optimal)
    val infiniteTransition = rememberInfiniteTransition(label = "PortalAnimation")

    // Infinite breathing scale pulse
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Infinite breathing transparency pulse
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    // Gentle rotation of the color components
    val rotateDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ColorRotation"
    )

    // Full screen Edge-to-Edge immersive container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(deepMatteDark),
        contentAlignment = Alignment.Center
    ) {
        // Abstract Ambient Morphing - Canvas Light Ring Portal
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    rotationZ = rotateDegrees
                }
                .scale(scalePulse)
                .alpha(alphaPulse)
        ) {
            // Radial Core Glow (Aura)
            val coreGlow = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF2563EB).copy(alpha = 0.22f), // Royal Blue
                    Color(0xFF10B981).copy(alpha = 0.15f), // Emerald Green
                    Color.Transparent
                )
            )
            drawCircle(
                brush = coreGlow,
                radius = size.minDimension * 0.48f
            )

            // Sweep Gradient for rotating light ring spectrum
            val circleBrush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF2563EB), // Royal Blue
                    0.5f to Color(0xFF10B981), // Emerald/Pure Green
                    1.0f to Color(0xFF2563EB)  // Back to Royal Blue
                )
            )

            drawCircle(
                brush = circleBrush,
                radius = size.minDimension * 0.40f,
                style = Stroke(
                    width = 12.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
