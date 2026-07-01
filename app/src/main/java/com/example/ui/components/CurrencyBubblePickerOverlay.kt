package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.habayeb.utils.CurrencyConfig
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun CurrencyBubblePickerOverlay(
    currentCurrencySymbol: String,
    onCurrencySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    // Satellites positions (relative to center of the overlay container)
    // We position YER, SAR, USD in a semi-circle or triangle around the central resting point of the ball.
    val currenciesToDisplay = listOf(
        Triple("ر.ي", "🇾🇪", "ريال يمني"),
        Triple("ر.س", "🇸🇦", "ريال سعودي"),
        Triple("$", "🇺🇸", "دولار أمريكي")
    )

    // Central draggable ball offset (starts at 0f, 0f relative to its center box)
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    // Spring-back/animate states for the main ball when released
    val mainBallScale = remember { Animatable(1f) }
    val entranceAlpha = remember { Animatable(0f) }
    val satellitesScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entranceAlpha.animateTo(1f, animationSpec = tween(300, easing = LinearOutSlowInEasing))
        satellitesScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    // Convert dps to pixels for distance/snap math
    val density = LocalDensity.current
    val bubbleRadiusPx = with(density) { 30.dp.toPx() } // satellite radius
    val mainBallRadiusPx = with(density) { 40.dp.toPx() } // main ball radius

    // Predefined positions of the 3 satellite targets relative to the center (in DP)
    // Satellite 1: Top-Left
    // Satellite 2: Top-Center
    // Satellite 3: Top-Right
    val targetsDp = listOf(
        Pair(-80.dp, -120.dp), // YER
        Pair(0.dp, -160.dp),   // SAR
        Pair(80.dp, -120.dp)   // USD
    )

    val targetsPx = targetsDp.map { (dx, dy) ->
        Pair(with(density) { dx.toPx() }, with(density) { dy.toPx() })
    }

    // Determine if main ball is intersecting/overlapping any target
    var activeHoverIndex by remember { mutableStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f * entranceAlpha.value))
            .pointerInput(Unit) {
                // Dimmer dismisses on tap
            },
        contentAlignment = Alignment.Center
    ) {
        // Main glassmorphic container card
        Card(
            modifier = Modifier
                .width(320.dp)
                .height(440.dp)
                .scale(entranceAlpha.value)
                .shadow(24.dp, shape = RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color(0xFF1E1A3E)
                    )
                }

                // Decorative title
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "اختر عملة التطبيق الأساسية",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1A3E),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اسحب الكرة البنفسجية 🔮 نحو هدفك أو اضغط مباشرة لتأكيد التغيير",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                // The Interactive Area Center
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    // Draw the Orbit paths/lines to make it feel super industrial & beautiful
                    // We'll use simple dashed lines or just beautifully placed dashed connection arcs.
                    
                    // Render Satellites (YER, SAR, USD)
                    currenciesToDisplay.forEachIndexed { index, item ->
                        val (symbol, flag, name) = item
                        val targetDp = targetsDp[index]
                        val isHovered = activeHoverIndex == index

                        val satScale = satellitesScale.value * if (isHovered) 1.25f else 1.0f
                        val satColor = if (isHovered) Color(0xFF00B2FE) else Color(0xFFF0F3FC)
                        val textColor = if (isHovered) Color.White else Color(0xFF1E1A3E)

                        Box(
                            modifier = Modifier
                                .offset(targetDp.first, targetDp.second)
                                .align(Alignment.Center)
                                .size(64.dp)
                                .scale(satScale)
                                .shadow(
                                    elevation = if (isHovered) 10.dp else 4.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0xFF4B36A2),
                                    spotColor = Color(0xFF4B36A2)
                                )
                                .background(
                                    brush = if (isHovered) {
                                        Brush.verticalGradient(listOf(Color(0xFF00B2FE), Color(0xFF008CC6)))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF0F3FC)))
                                    },
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCurrencySelected(symbol)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = flag, fontSize = 20.sp)
                                Text(
                                    text = symbol,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                    }

                    // Draggable Main Ball
                    // It resting position is at the center bottom (0, 60.dp)
                    val baseOffsetY = with(density) { 60.dp.toPx() }
                    val currentBallX = dragOffsetX
                    val currentBallY = dragOffsetY + baseOffsetY

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    dragOffsetX.roundToInt(),
                                    (dragOffsetY + with(density) { 60.dp.toPx() }).roundToInt()
                                )
                            }
                            .align(Alignment.Center)
                            .size(76.dp)
                            .scale(mainBallScale.value)
                            .shadow(12.dp, CircleShape, ambientColor = Color(0xFF4B36A2), spotColor = Color(0xFF4B36A2))
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xFF4B36A2), Color(0xFF8C7CFF))
                                ),
                                shape = CircleShape
                            )
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        // Check if dropped onto a target
                                        if (activeHoverIndex != -1) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val selectedSymbol = currenciesToDisplay[activeHoverIndex].first
                                            onCurrencySelected(selectedSymbol)
                                        } else {
                                            // Reset back to center with spring effect
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            activeHoverIndex = -1
                                        }
                                    },
                                    onDragCancel = {
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        activeHoverIndex = -1
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX += dragAmount.x
                                        dragOffsetY += dragAmount.y

                                        // Calculate distance to targets
                                        var foundHoverIndex = -1
                                        for (i in targetsPx.indices) {
                                            val tx = targetsPx[i].first
                                            val ty = targetsPx[i].second

                                            // Distance from current dragged ball to the target
                                            val dist = sqrt(
                                                (currentBallX - tx) * (currentBallX - tx) +
                                                (currentBallY - ty) * (currentBallY - ty)
                                            )

                                            // Check overlapping threshold
                                            if (dist < 70.dp.toPx()) {
                                                foundHoverIndex = i
                                                break
                                            }
                                        }

                                        if (foundHoverIndex != activeHoverIndex) {
                                            activeHoverIndex = foundHoverIndex
                                            if (activeHoverIndex != -1) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🔮",
                                fontSize = 22.sp
                            )
                            Text(
                                text = currentCurrencySymbol,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
