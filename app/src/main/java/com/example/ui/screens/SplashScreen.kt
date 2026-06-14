package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TheMasterSplashScreen(
    onSplashFinished: () -> Unit
) {
    var animationState by remember { mutableStateOf(0) } // 0: Start, 1: Stage 1, 2: Stage 2, 3: Stage 3

    LaunchedEffect(Unit) {
        animationState = 1 // 0.0s - Scale logo
        delay(800)
        animationState = 2 // 0.8s - Fade in tagline
        delay(2500)        // 2500ms stable reading delay for the elegant tagline
        animationState = 3 // 3.3s - Liquid expansion transition
        delay(1000)
        onSplashFinished()
    }

    // Colors
    val bgGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF111215), Color(0xFF111215)) // Matte Dark #111215
    )
    val emeraldColor = Color(0xFF10B981)
    val purpleColor = Color(0xFF9333EA)
    
    // Scale Animation (0.0 to 1.0)
    val logoScale by animateFloatAsState(
        targetValue = if (animationState >= 1) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LogoScale"
    )

    // Text Fade Animation
    val textAlpha by animateFloatAsState(
        targetValue = if (animationState >= 2) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "TextAlpha"
    )

    // Shimmer effect
    val infiniteTransition = rememberInfiniteTransition(label = "Shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Shimmer"
    )

    // Liquid Reveal (Expanding Circle)
    val revealScale by animateFloatAsState(
        targetValue = if (animationState == 3) 50f else 0f, // 50x to cover screen
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "RevealScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
        contentAlignment = Alignment.Center
    ) {
        // Core Content (behind the reveal)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Unified Logo Badge
            Box(
                modifier = Modifier
                    .scale(logoScale)
                    .height(100.dp)
                    .width(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Emerald Circle (Right in RTL - Home)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart) // Start is Right in RTL
                        .padding(start = 15.dp) // Pushes it towards center
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(emeraldColor.copy(alpha = 0.15f))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(emeraldColor.copy(alpha = 0.4f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = "Home",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Purple Circle (Left in RTL - Wallet)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd) // End is Left in RTL
                        .padding(end = 15.dp) // Pushes it towards center
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(purpleColor.copy(alpha = 0.15f))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(purpleColor.copy(alpha = 0.4f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalanceWallet,
                        contentDescription = "Wallet",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Premium Tagline
            Text(
                text = "ميزان الدار.. راحةٌ واستقرار",
                color = Color(0xFFE9E4F0),
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .alpha(textAlpha)
                    .graphicsLayer { alpha = 0.99f } // Needed for blending
                    .drawWithContent {
                        drawContent()
                        if (animationState >= 2) {
                            val gradient = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFFFD700).copy(alpha = 0.4f), // Soft gold
                                    Color.Transparent
                                ),
                                start = Offset(size.width * shimmerTranslate, 0f),
                                end = Offset(size.width * shimmerTranslate + 200f, size.height)
                            )
                            drawRect(brush = gradient, blendMode = BlendMode.SrcAtop)
                        }
                    }
            )
        }

        // Liquid Reveal Circle
        if (revealScale > 0f) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(revealScale)
                    .clip(CircleShape)
                    .background(Color(0xFFF8FAFC)) // Default background of the app
            )
        }
    }
}
