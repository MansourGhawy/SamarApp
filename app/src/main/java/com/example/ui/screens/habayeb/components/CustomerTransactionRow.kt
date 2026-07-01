package com.example.ui.screens.habayeb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entities.HabayebTransaction
import com.example.ui.screens.habayeb.utils.CurrencyConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomerTransactionRow(
    tx: HabayebTransaction,
    currencySymbol: String,
    isSelected: Boolean,
    isTxMultiSelectActive: Boolean,
    hasActiveRecurring: Boolean,
    txSeqNo: Int,
    parentTxSeq: Int?,
    currentHistBalance: Double,
    activeThemeColor: Color,
    onSelectToggle: () -> Unit,
    onLongClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onExchangeRateClick: () -> Unit
) {
    val parsedCurrencyInfo = remember(tx.description) {
        CurrencyConfig.parseTransactionCurrency(tx.description, currencySymbol)
    }
    val txCurrencySymbol = if (tx.is_foreign) tx.currency_code else parsedCurrencyInfo.first
    val cleanDescription = parsedCurrencyInfo.second

    val formattedDate = remember(tx.timestamp) {
        SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Date(tx.timestamp * 1000))
    }

    val isPositiveSign = tx.type == "PAYMENT_BY_THEM" || tx.type == "OWED_TO_THEM"
    val isGreenColor = tx.type == "PAYMENT_BY_THEM" || tx.type == "PAYMENT_TO_THEM"
    val indicatorColor = if (isGreenColor) Color(0xFF10B981) else Color(0xFFEF4444)
    val rowBgColor = if (isSelected) activeThemeColor.copy(alpha = 0.08f) else Color.White
    val borderColor = if (isSelected) activeThemeColor else Color(0xFFE2E8F0)

    val formattedHistBal = try { String.format(Locale.ENGLISH, "%,.0f", currentHistBalance) } catch (e: Exception) { currentHistBalance.toString() }
    val amountToFormat = if (tx.is_foreign) tx.foreign_amount else tx.amount
    val formattedAmount = try { String.format(Locale.ENGLISH, "%,.0f", amountToFormat) } catch (e: Exception) { amountToFormat.toString() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .combinedClickable(
                onClick = {
                    if (isTxMultiSelectActive) {
                        onSelectToggle()
                    } else {
                        onOptionsClick()
                    }
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = rowBgColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(indicatorColor, indicatorColor.copy(alpha = 0.6f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Date/Time (Rightmost)
                Column(
                    modifier = Modifier.weight(1.1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "#$txSeqNo",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeThemeColor,
                            modifier = Modifier
                                .background(activeThemeColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        if (hasActiveRecurring) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(activeThemeColor.copy(alpha = 0.12f), CircleShape)
                                    .clickable { onScheduleClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "تعديل التكرار التلقائي",
                                    tint = activeThemeColor,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                // 2. Details (Middle-Right)
                Column(
                    modifier = Modifier.weight(1.7f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val typeStr = when (tx.type) {
                        "OWED_BY_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_owed_by)
                        "PAYMENT_BY_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_payment_by)
                        "OWED_TO_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_owed_to)
                        "PAYMENT_TO_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_payment_to)
                        else -> stringResource(id = R.string.habayeb_pdf_tx_generic)
                    }
                    Text(
                        text = typeStr,
                        fontSize = 9.sp,
                        color = indicatorColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = cleanDescription.ifEmpty { "لا يوجد ملاحظات" },
                        fontSize = 12.sp,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasActiveRecurring) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                                .border(0.5.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "مصدر تكرار مجدول 🔄",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    } else if (tx.linkedMainTxId != null) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                                .border(0.5.dp, Color(0xFF3B82F6), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "توليد تلقائي (تابع للمعامله #${parentTxSeq ?: "?"})",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }

                    if (tx.is_foreign) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (tx.is_rate_calculated) Color(0xFFE6F4EA) else Color(0xFFF1F3F4))
                                .border(0.5.dp, if (tx.is_rate_calculated) Color(0xFF137333) else Color(0xFF9AA0A6), RoundedCornerShape(6.dp))
                                .clickable { onExchangeRateClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (tx.is_rate_calculated) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (tx.is_rate_calculated) Color(0xFF137333) else Color(0xFFD93025),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (tx.is_rate_calculated) "نشط (صرف: ${tx.exchange_rate})" else "صرف غير نشط ❌",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tx.is_rate_calculated) Color(0xFF137333) else Color(0xFF5F6368)
                            )
                        }
                    }
                }

                // 3. Amount with colorful indicator arrow (Middle-Left)
                Column(
                    modifier = Modifier.weight(1.0f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val txArrow = when (tx.type) {
                            "PAYMENT_TO_THEM" -> Icons.Default.ArrowUpward
                            "OWED_TO_THEM" -> Icons.Default.ArrowDownward
                            "PAYMENT_BY_THEM" -> Icons.Default.ArrowDownward
                            "OWED_BY_THEM" -> Icons.Default.ArrowUpward
                            else -> if (isPositiveSign) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                        }
                        Icon(
                            imageVector = txArrow,
                            contentDescription = null,
                            tint = indicatorColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$formattedAmount $txCurrencySymbol",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = indicatorColor
                        )
                    }
                    if (tx.is_foreign && tx.is_rate_calculated) {
                        val formattedEquiv = try { String.format(Locale.ENGLISH, "%,.0f", tx.equivalent_amount) } catch (e: Exception) { tx.equivalent_amount.toString() }
                        Text(
                            text = "($formattedEquiv $currencySymbol)",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 4. Running Balance (Leftmost)
                val balCurrency = if (tx.is_foreign && !tx.is_rate_calculated) tx.currency_code else currencySymbol
                Text(
                    text = "$formattedHistBal $balCurrency",
                    modifier = Modifier.weight(0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
