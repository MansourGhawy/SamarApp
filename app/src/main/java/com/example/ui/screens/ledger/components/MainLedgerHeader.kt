package com.example.ui.screens.ledger.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.local.entities.FixedCommitment
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftRed
import com.example.ui.viewmodel.MonthLedger
import java.math.BigDecimal

@Composable
fun PinnedMainLedgerHeader(
    collapseFraction: Float,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onHabayebClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (collapseFraction <= 0f) return

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(10f)
            .background(EmeraldPrimary)
            .statusBarsPadding()
            .height(48.dp)
            .alpha(collapseFraction)
    ) {
        Text(
            text = stringResource(id = R.string.ledger_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(id = R.string.ledger_nav_menu_desc),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSearchClick()
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(id = R.string.habayeb_search_label),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHabayebClick()
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = stringResource(id = R.string.habayeb_title),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun MainLedgerHeader(
    collapseFraction: Float,
    isDaySelectionMode: Boolean,
    selectedDayKeys: List<String>,
    onCancelDaySelection: () -> Unit,
    onSelectAllDays: () -> Unit,
    onDeleteSelectedDays: () -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    totalCash: BigDecimal,
    isPrivacyMode: Boolean,
    onTogglePrivacyMode: () -> Unit,
    currencySymbol: String,
    formatCurrency: (BigDecimal, String) -> String,
    commitments: List<FixedCommitment>,
    computedCommitments: List<Triple<FixedCommitment, Double, Double>>,
    linkHabayebDebts: Boolean,
    onLinkHabayebDebtsChange: (Boolean) -> Unit,
    monthlyLedger: List<MonthLedger>,
    selectedDayKeysCountText: String,
    isSelectAllChecked: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(EmeraldPrimary)
            .statusBarsPadding()
            .padding(bottom = 4.dp)
    ) {
        val topRowHeight = if (isDaySelectionMode) {
            50.dp
        } else {
            (50 * (1f - collapseFraction)).dp
        }
        val topRowAlpha = if (isDaySelectionMode) 1f else (1f - collapseFraction)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topRowHeight)
                .alpha(topRowAlpha)
        ) {
            if (isDaySelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                onCancelDaySelection()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.common_cancel),
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectAllDays()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = if (isSelectAllChecked) stringResource(id = R.string.ledger_cancel_all) else stringResource(id = R.string.ledger_select_all),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = selectedDayKeysCountText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            if (selectedDayKeys.isNotEmpty()) {
                                onDeleteSelectedDays()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.ledger_bulk_delete_days_desc),
                            tint = if (selectedDayKeys.isEmpty()) Color.White.copy(alpha = 0.4f) else Color(0xFFFF8A80),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Right/Start Element: Menu button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMenuClick()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(id = R.string.ledger_nav_menu_desc),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Centered Element
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(id = R.string.ledger_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = Color.White
                        )
                    }

                    // Left/End Element: Search button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSearchClick()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(id = R.string.habayeb_search_label),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.ledger_actual_cash),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = onTogglePrivacyMode,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(id = R.string.ledger_visibility_desc),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPrivacyMode) "*****" else formatCurrency(totalCash, currencySymbol),
                            fontSize = 22.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (commitments.isNotEmpty()) {
                val totalTarget = commitments.sumOf { it.targetAmount }
                val totalAllocated = computedCommitments.sumOf { it.second }
                val percentFloat = if (totalTarget > 0.0) {
                    (totalAllocated / totalTarget).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }

                val cashPercentFloat = remember(commitments, totalCash) {
                    if (totalTarget > 0.0) {
                        var remainingCash = totalCash.toDouble()
                        val allocated = commitments.sumOf { fc ->
                            val needed = (fc.targetAmount - fc.currentProgress).coerceAtLeast(0.0)
                            if (remainingCash >= needed) {
                                remainingCash -= needed
                                needed
                            } else if (remainingCash > 0) {
                                val temp = remainingCash
                                remainingCash = 0.0
                                temp
                            } else {
                                0.0
                            }
                        }
                        ((commitments.sumOf { it.currentProgress } + allocated) / totalTarget).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.ledger_link_debts),
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Switch(
                        checked = linkHabayebDebts,
                        onCheckedChange = onLinkHabayebDebtsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFF3E8FF),
                            checkedTrackColor = Color(0xFF8B5CF6),
                            uncheckedThumbColor = Color(0xFFE2E8F0),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.height(18.dp).scale(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF00E676), RoundedCornerShape(5.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${(percentFloat * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E676)
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.ledger_commitments_ratio),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                
                val neonGradient = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF00E676),
                        Color(0xFF00B0FF)
                    )
                )

                Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    if (linkHabayebDebts && percentFloat > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percentFloat)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFC4B5FD))
                        )
                    }
                    val frontPercent = if (linkHabayebDebts) cashPercentFloat else percentFloat
                    if (frontPercent > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frontPercent)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(neonGradient)
                        )
                    }
                }
            }
        }
    }
}
