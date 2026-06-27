package com.example.ui.screens.settings.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SoftRed
import kotlinx.coroutines.delay

@Composable
fun DangerDeleteButton(onDeleteConfirmed: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var isPressing by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (isPressing) 1f else 0f,
        animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        label = "DeleteProgress"
    )

    LaunchedEffect(isPressing) {
        if (isPressing) {
            while (isPressing) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(120)
            }
        }
    }

    LaunchedEffect(progress) {
        if (progress == 1f) {
            isPressing = false
            onDeleteConfirmed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SoftRed, RoundedCornerShape(12.dp))
            .background(SoftRed.copy(alpha = 0.04f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressing = true
                        tryAwaitRelease()
                        isPressing = false
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Internal slide filler on hold
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .align(Alignment.CenterStart)
                .background(SoftRed.copy(alpha = 0.16f))
        )

        Text(
            text = if (isPressing) stringResource(R.string.settings_delete_all_data_progress) else stringResource(R.string.settings_delete_all_data),
            color = SoftRed,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
