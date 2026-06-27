package com.example.ui.screens.ledger.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.FixedCommitment
import com.example.data.local.MonthLedger
import com.example.ui.theme.EmeraldPrimary
import java.math.BigDecimal

@Composable
fun MainLedgerHeader(
    collapseFraction: Float,
    isPrivacyMode: Boolean,
    isDaySelectionMode: Boolean,
    selectedDayKeys: List<String>,
    monthlyLedger: List<MonthLedger>,
    totalCash: BigDecimal,
    currencySymbol: String,
    commitments: List<FixedCommitment>,
    linkHabayebDebts: Boolean,
    percentFloat: Float,
    cashPercentFloat: Float,
    onTogglePrivacyMode: () -> Unit,
    onToggleLinkHabayebDebts: (Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCancelDaySelection: () -> Unit,
    onSelectAllDaysClick: () -> Unit,
    onBulkDeleteDaysClick: () -> Unit,
    formatCurrency: (BigDecimal, String) -> String,
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
        val topRowHeight = if (isDaySelectionMode) 52.dp else (46 * (1f - collapseFraction)).dp
        val topRowAlpha = if (isDaySelectionMode) 1f else (1f - collapseFraction)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topRowHeight)
                .alpha(topRowAlpha)
        ) {
            if (isDaySelectionMode) {
                // شريط الاختيار المتعدد للأيام
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
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCancelDaySelection()
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
                                onSelectAllDaysClick()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            modifier = Modifier.height(34.dp)
                        ) {
                            val allKeys = monthlyLedger.flatMap { ml -> ml.days.map { "${ml.monthKey}_${it.dayNumber}" } }
                            Text(
                                text = if (selectedDayKeys.size == allKeys.size) {
                                    stringResource(id = R.string.ledger_cancel_all)
                                } else {
                                    stringResource(id = R.string.ledger_select_all)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    val selectedCountText = when (selectedDayKeys.size) {
                        1 -> stringResource(id = R.string.ledger_selected_days_count_1)
                        2 -> stringResource(id = R.string.ledger_selected_days_count_2)
                        else -> stringResource(id = R.string.ledger_selected_days_count_more, selectedDayKeys.size)
                    }
                    Text(
                        text = selectedCountText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            if (selectedDayKeys.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBulkDeleteDaysClick()
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
                // الهيدر الطبيعي (العنوان وسر البحث والمنيو)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(id = R.string.ledger_nav_menu_desc),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 12.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.ledger_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = stringResource(id = R.string.ledger_subtitle),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFFB2DFDB)
                        )
                    }

                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(id = R.string.habayeb_search_label),
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // لوحة الرصيد الفعلي الزجاجية Glassmorphic Card Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.ledger_actual_cash),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFEF08A)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onTogglePrivacyMode,
                            modifier = Modifier.size(24.dp).padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(id = R.string.ledger_visibility_desc),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = if (isPrivacyMode) "*****" else formatCurrency(totalCash, currencySymbol),
                            fontSize = 24.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // مؤشر ونسبة التغطية
            if (commitments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
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
                        onCheckedChange = onToggleLinkHabayebDebts,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFF3E8FF),
                            checkedTrackColor = Color(0xFF8B5CF6),
                            uncheckedThumbColor = Color(0xFFE2E8F0),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.height(18.dp).scale(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

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

                val neonGradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF00E676),
                        Color(0xFF00B0FF)
                    )
                )

                Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    if (linkHabayebDebts && percentFloat > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percentFloat)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFC4B5FD))
                        )
                    }
                    val frontPercent = if (linkHabayebDebts) cashPercentFloat else percentFloat
                    if (frontPercent > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frontPercent)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(neonGradient)
                        )
                    }
                }
            }
        }
    }
}