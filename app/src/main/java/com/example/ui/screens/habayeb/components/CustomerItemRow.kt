package com.example.ui.screens.habayeb.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.helper.AutoScaleText
import com.example.ui.helper.formatCurrency
import com.example.ui.helper.getInitialColor
import androidx.compose.material3.MaterialTheme


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomerItemRow(
    customer: com.example.ui.state.CustomerUiState,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    activeThemeColor: Color,
    activeSubColor: Color,
    currencySymbol: String,
    haptic: HapticFeedback,
    onCustomerClick: () -> Unit,
    onCustomerLongClick: () -> Unit,
    onQuickAdd: () -> Unit
) {
    val lastTxTime = customer.lastTransactionTimestamp
    val onSurfaceTextColor = MaterialTheme.colorScheme.onBackground
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember { java.text.SimpleDateFormat("yyyy/MM/dd hh:mm a", java.util.Locale.ENGLISH) }
    val formattedDate = remember(lastTxTime) {
        val formatted = sdf.format(java.util.Date(lastTxTime * 1000))
        formatted.replace("AM", "ص").replace("PM", "م")
                 .replace("am", "ص").replace("pm", "م")
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) activeThemeColor.copy(alpha = 0.5f) else Color(0xFFF1F5F9)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) activeSubColor.copy(alpha = 0.3f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 200))
            .combinedClickable(
                onClick = onCustomerClick,
                onLongClick = onCustomerLongClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val avatarBgColor = activeThemeColor.copy(alpha = 0.08f)
                    val avatarIconColor = activeThemeColor

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(avatarBgColor)
                            .clickable { onQuickAdd() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(id = R.string.habayeb_add_tx_button).replace(" ➕",""),
                            tint = avatarIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.Start) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(id = R.string.ledger_done_btn),
                                    tint = activeThemeColor,
                                    modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                )
                            }
                            Text(
                                text = customer.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = onSurfaceTextColor
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = R.string.habayeb_last_modified, formattedDate),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = textSecondaryColor
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    val netDebt = customer.netDebt
                    if (netDebt > 0.0) {
                        AutoScaleText(
                            text = formatCurrency(netDebt, currencySymbol),
                            baseFontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = R.string.habayeb_status_owed_by),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = textSecondaryColor
                        )
                    } else if (netDebt < 0.0) {
                        AutoScaleText(
                            text = formatCurrency(netDebt, currencySymbol),
                            baseFontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = R.string.habayeb_status_owed_to),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = textSecondaryColor
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.habayeb_pdf_balance_balanced).replace("الرصيد الصافي (متساوي):", "خالص").trim(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondaryColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = R.string.habayeb_status_balanced),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = textSecondaryColor
                        )
                    }
                }
            }

            // Smooth, lightweight details expansion
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 200)) + shrinkVertically(animationSpec = tween(durationMillis = 200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(
                        color = Color(0xFFF1F5F9),
                        thickness = 1.dp
                    )
                    if (customer.phone.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = stringResource(id = R.string.habayeb_pdf_phone).replace(": %s",""),
                                tint = activeThemeColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = customer.phone,
                                fontSize = 12.sp,
                                color = onSurfaceTextColor
                            )
                        }
                    }
                    if (customer.notes.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(id = R.string.makhzan_transaction_note),
                                tint = activeThemeColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = customer.notes,
                                fontSize = 12.sp,
                                color = onSurfaceTextColor
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = stringResource(id = R.string.habayeb_tx_history),
                            tint = activeThemeColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.habayeb_tx_history) + ": ${customer.totalTransactions}",
                            fontSize = 12.sp,
                            color = onSurfaceTextColor
                        )
                    }
                }
            }
        }
    }
}
