package com.example.ui.screens.ledger.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.FixedCommitment
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun CommitmentsListDialog(
    showCommitmentsListSheet: Boolean,
    commitments: List<FixedCommitment>,
    computedCommitments: List<Triple<FixedCommitment, Double, Double>>,
    totalCash: BigDecimal,
    currencySymbol: String,
    formatCurrency: (BigDecimal, String) -> String,
    formatDoubleCurrency: (Double, String) -> String,
    onDismissRequest: () -> Unit,
    onAddCommitmentClick: () -> Unit,
    onEditCommitmentClick: (FixedCommitment) -> Unit,
    onDeleteCommitment: (String) -> Unit,
    onReorderCommitment: (FixedCommitment, Int) -> Unit,
    onCheckedChange: (FixedCommitment, Boolean) -> Unit,
    onSetReorderTarget: (FixedCommitment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showCommitmentsListSheet) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val commitmentsScaleFraction = remember { Animatable(0f) }
    LaunchedEffect(showCommitmentsListSheet) {
        commitmentsScaleFraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        )
    }

    val closeAction = {
        scope.launch {
            commitmentsScaleFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            )
            onDismissRequest()
        }
    }

    Dialog(onDismissRequest = { closeAction() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .graphicsLayer(
                    scaleX = commitmentsScaleFraction.value,
                    scaleY = commitmentsScaleFraction.value,
                    alpha = commitmentsScaleFraction.value,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onAddCommitmentClick,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFEF3C7))
                        ) {
                            Icon(Icons.Default.Add, stringResource(id = R.string.ledger_add_commitment_title), tint = Color(0xFFD97706))
                        }
                        IconButton(
                            onClick = {
                                val builder = StringBuilder()
                                builder.append(context.getString(R.string.ledger_commitment_box_title))
                                var idx = 1
                                commitments.forEach { fc ->
                                    builder.append(context.getString(R.string.ledger_commitment_share_format, idx, fc.name, formatDoubleCurrency(fc.targetAmount, currencySymbol)))
                                    idx++
                                }
                                
                                val totalReq = commitments.sumOf { it.targetAmount }
                                val totalRemaining = computedCommitments.sumOf { it.third }
                                
                                builder.append(context.getString(R.string.ledger_commitment_total_req, formatDoubleCurrency(totalReq, currencySymbol)))
                                builder.append(context.getString(R.string.ledger_commitment_total_current, formatCurrency(totalCash, currencySymbol)))
                                builder.append(context.getString(R.string.ledger_commitment_total_remaining, formatDoubleCurrency(totalRemaining, currencySymbol)))
                                
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, builder.toString())
                                }
                                try {
                                    shareIntent.setPackage("com.whatsapp")
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    shareIntent.setPackage(null)
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.ledger_share_via)))
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE5F6FD))
                        ) {
                            Icon(Icons.Default.Share, stringResource(id = R.string.ledger_whatsapp_whatsapp), tint = Color(0xFF0369A1), modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    Text(
                        text = stringResource(id = R.string.ledger_goals_and_commitments),
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldPrimary,
                        fontSize = 16.sp
                    )
                }
                
                if (commitments.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.ledger_commitment_empty_state),
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                } else {
                    val totalTargetSum = commitments.sumOf { it.targetAmount }
                    val totalAllocatedSum = computedCommitments.sumOf { it.second }
                    val coveredCount = computedCommitments.count { it.third <= 0.0 }
                    
                    Text(
                        text = stringResource(
                            id = R.string.ledger_commitment_coverage_status,
                            coveredCount.toString(),
                            commitments.size,
                            formatDoubleCurrency(totalAllocatedSum, currencySymbol),
                            formatDoubleCurrency(totalTargetSum, currencySymbol)
                        ),
                        fontSize = 11.sp,
                        color = if (totalAllocatedSum >= totalTargetSum) SoftGreen else Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxHeight(0.6f)
                    ) {
                        itemsIndexed(computedCommitments) { index, (fc, allocated, remaining) ->
                            val isCovered = remaining <= 0.0
                            
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left side actions & state
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            var dragOffset by remember { mutableFloatStateOf(0f) }
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        onSetReorderTarget(fc)
                                                    }
                                                    .pointerInput(Unit) {
                                                        detectDragGestures(
                                                            onDragStart = { _ -> dragOffset = 0f },
                                                            onDrag = { _, dragAmount ->
                                                                dragOffset += dragAmount.y
                                                                if (dragOffset > 70f) {
                                                                    dragOffset = 0f
                                                                    val pos = index + 2
                                                                    if (pos <= commitments.size) {
                                                                        onReorderCommitment(fc, pos)
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    }
                                                                } else if (dragOffset < -70f) {
                                                                    dragOffset = 0f
                                                                    val pos = index
                                                                    if (pos >= 1) {
                                                                        onReorderCommitment(fc, pos)
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    }
                                                                }
                                                            },
                                                            onDragEnd = { dragOffset = 0f }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Menu, stringResource(id = R.string.ledger_reorder_apply), tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    onEditCommitmentClick(fc)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, stringResource(id = R.string.ledger_edit_commitment_title), tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    onDeleteCommitment(fc.name)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, stringResource(id = R.string.ledger_commitment_delete), tint = SoftRed, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.Start) {
                                            if (isCovered) {
                                                Text(stringResource(id = R.string.ledger_commitment_completed), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SoftGreen)
                                            } else {
                                                Text("-${formatDoubleCurrency(remaining, currencySymbol)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SoftRed)
                                                if (allocated > 0.0) {
                                                    Text(stringResource(id = R.string.ledger_commitment_covered_amount, formatDoubleCurrency(allocated, currencySymbol)), fontSize = 9.sp, color = SoftGreen)
                                                }
                                            }
                                        }
                                    }

                                    // Right side Name/Check
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val neededToComplete = (fc.targetAmount - fc.currentProgress).coerceAtLeast(0.0)
                                                val canAffordButNotCovered = !isCovered && totalCash.toDouble() >= neededToComplete
                                                if (canAffordButNotCovered) {
                                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                                    val alphaAnim by infiniteTransition.animateFloat(
                                                        initialValue = 0.2f,
                                                        targetValue = 1.0f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween(800, easing = LinearEasing),
                                                            repeatMode = RepeatMode.Reverse
                                                        ),
                                                        label = "alpha"
                                                    )
                                                    Text("🟢", modifier = Modifier.alpha(alphaAnim), fontSize = 10.sp)
                                                }
                                                Text(fc.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EmeraldPrimary)
                                            }
                                            Text(stringResource(id = R.string.ledger_commitment_target_prefix, formatDoubleCurrency(fc.targetAmount, currencySymbol)), fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Checkbox(
                                            checked = isCovered,
                                            onCheckedChange = { checked ->
                                                onCheckedChange(fc, checked)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = SoftGreen, checkmarkColor = Color.White),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { closeAction() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.ledger_done_btn), fontSize = 14.sp)
                }
            }
        }
    }
}
