package com.example.ui.screens.ledger.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.DayLedger
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed
import java.math.BigDecimal

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayCard(
    dayLedger: DayLedger,
    dayKey: String,
    isDaySelected: Boolean,
    isDaySelectionMode: Boolean,
    haptic: HapticFeedback,
    currencySymbol: String,
    formatCurrency: (BigDecimal, String) -> String,
    onDayClick: (String) -> Unit,
    onDayLongClick: (String) -> Unit
) {
    // Alternating gentle cash flow background colors (beautiful light gradients)
    val cardBrush = if (isDaySelected) {
        Brush.linearGradient(
            colors = listOf(Color(0xFFE6F4EA), Color(0xFFD1FAE5)) // gentle emerald tint when selected
        )
    } else if (dayLedger.netAmount.compareTo(BigDecimal.ZERO) >= 0) {
        Brush.linearGradient(
            colors = listOf(Color(0xFFF3FAF5), Color.White)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFFFF7F7), Color.White)
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = if (isDaySelected) {
            BorderStroke(1.5.dp, Color(0xFF10B981))
        } else {
            BorderStroke(1.dp, Color(0xFFEEEEEC))
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDayClick(dayKey)
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDayLongClick(dayKey)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBrush)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Net balance indicator & sleek interactive detail label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = (if (dayLedger.netAmount.compareTo(BigDecimal.ZERO) > 0) "+" else "") +
                            formatCurrency(dayLedger.netAmount, currencySymbol),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (dayLedger.netAmount.compareTo(BigDecimal.ZERO) >= 0) SoftGreen else SoftRed
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray.copy(alpha = 0.05f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(stringResource(id = R.string.ledger_details_label), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("📑", fontSize = 11.sp)
                }
            }

            // Right: Day title and Date description along with circular Selection indicator (Checkbox)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(id = R.string.ledger_days_prefix, dayLedger.dayNumber, dayLedger.dayOfWeek),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dayLedger.fullDate,
                        fontSize = 9.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                }

                if (isDaySelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isDaySelected) Color(0xFF10B981) else Color.White)
                            .border(1.5.dp, Color(0xFF10B981), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDaySelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
