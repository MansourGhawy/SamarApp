package com.example.ui.screens.ledger.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed

@Composable
fun LedgerBottomDock(
    isSelectionMode: Boolean,
    selectedTxIdsCount: Int,
    onDeleteSelectedClick: () -> Unit,
    onShowCommitmentsClick: () -> Unit,
    onAddIncomeClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Delete selection items floating banner if active
        if (isSelectionMode && selectedTxIdsCount > 0) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDeleteSelectedClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(46.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.ledger_delete_selected_warning, selectedTxIdsCount), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    stringResource(id = R.string.ledger_delete_selected_warning, selectedTxIdsCount),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        if (!isSelectionMode) {
            // Breathe/Pulsing Glow Effect for Goals & Commitments
            val pulsingTransition = rememberInfiniteTransition(label = "CommitmentPulsingTransition")
            val scalePulse by pulsingTransition.animateFloat(
                initialValue = 0.98f,
                targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ScalePulse"
            )
            val borderGlow by pulsingTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BorderGlow"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scalePulse
                        scaleY = scalePulse
                        transformOrigin = TransformOrigin(0.5f, 1.0f)
                    }
                    .clip(CircleShape)
                    .background(Color(0xFFF1F8E9)) // Soft olive-green tint
                    .border(
                        width = 1.dp,
                        color = Color(0xFF33691E).copy(alpha = borderGlow), // Pulsing olive-green border
                        shape = CircleShape
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShowCommitmentsClick()
                    }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.ledger_goals_and_commitments),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20) // Deep Dark Olive/Green text
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(0.95f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Add Income Button (Add Finance) - Sleek Pill style
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAddIncomeClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                shape = RoundedCornerShape(24.dp), // Modern Full-Pill Curve
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp) // Sleek, compressed button height
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.ledger_add_income), tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(id = R.string.ledger_add_income), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // Add Expense Button (Record Platform Expense) - Sleek Pill style
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAddExpenseClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoralAccent),
                shape = RoundedCornerShape(24.dp), // Modern Full-Pill Curve
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp) // Sleek, compressed button height
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = stringResource(id = R.string.ledger_add_expense), tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(id = R.string.ledger_add_expense), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
