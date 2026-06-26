package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun WelcomeOnboardingDialog(
    onDismiss: () -> Unit
) {
    // Dark Olive Green color constant
    val mizanGreen = Color(0xFF14452F)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = LinearOutSlowInEasing),
        label = "onboarding_fade"
    )

    AlertDialog(
        onDismissRequest = { /* Prevent dismissing by clicking outside to force onboarding action */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        tonalElevation = 6.dp,
        confirmButton = {},
        dismissButton = {},
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 20.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(alpha = alpha) // Custom fade-in for elite elegance
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // الشعار المضيء - Intersecting glowing circles logo
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(mizanGreen.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(56.dp)) {
                        val radius = size.minDimension / 3.5f
                        drawCircle(
                            color = mizanGreen.copy(alpha = 0.12f),
                            radius = radius * 1.3f,
                            center = center.copy(x = center.x - 10f)
                        )
                        drawCircle(
                            color = mizanGreen.copy(alpha = 0.20f),
                            radius = radius * 1.3f,
                            center = center.copy(x = center.x + 10f)
                        )
                        drawCircle(
                            color = mizanGreen,
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = mizanGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(id = R.string.onboarding_slogan),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = mizanGreen,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Features list in scrollable container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    OnboardingFeatureItem(
                        iconEmoji = "🏠",
                        title = stringResource(id = R.string.onboarding_mizan_title),
                        description = stringResource(id = R.string.onboarding_mizan_desc)
                    )
                    OnboardingFeatureItem(
                        iconEmoji = "🤝",
                        title = stringResource(id = R.string.onboarding_habayeb_title),
                        description = stringResource(id = R.string.onboarding_habayeb_desc)
                    )

                    OnboardingFeatureItem(
                        iconEmoji = "🗑️",
                        title = stringResource(id = R.string.onboarding_trash_title),
                        description = stringResource(id = R.string.onboarding_trash_desc)
                    )
                    OnboardingFeatureItem(
                        iconEmoji = "🛡️",
                        title = stringResource(id = R.string.onboarding_backup_title),
                        description = stringResource(id = R.string.onboarding_backup_desc)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scale & Bounce effect state
                val infiniteTransition = rememberInfiniteTransition(label = "bounce")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.04f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = mizanGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                ) {
                    Text(
                        text = stringResource(id = R.string.onboarding_start_button),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    )
}

@Composable
fun OnboardingFeatureItem(
    iconEmoji: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Color(0xFFF1F5F9), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconEmoji, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text portion
        val mizanGreen = Color(0xFF14452F)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = mizanGreen,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = Color(0xFF64748B),
                lineHeight = 16.sp,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
