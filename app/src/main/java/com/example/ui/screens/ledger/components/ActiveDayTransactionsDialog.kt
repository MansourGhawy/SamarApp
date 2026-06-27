package com.example.ui.screens.ledger.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.TransactionDb
import com.example.domain.DateUtils
import com.example.domain.extractEmoji
import com.example.domain.getEmojiBgColor
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed
import com.example.ui.viewmodel.DayLedger
import java.math.BigDecimal

@Composable
fun ActiveDayTransactionsDialog(
    activeDayKey: String?,
    activeDayLedger: DayLedger?,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onEditTransaction: (TransactionDb) -> Unit,
    formatCurrency: (BigDecimal, String) -> String,
    formatDoubleCurrency: (Double, String) -> String,
    modifier: Modifier = Modifier
) {
    if (activeDayKey == null || activeDayLedger == null) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.ledger_daily_record),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${activeDayLedger.dayOfWeek}، " + stringResource(id = R.string.ledger_days_prefix, activeDayLedger.dayNumber, activeDayLedger.fullDate),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            val sortedTxs = remember(activeDayLedger.transactions) {
                activeDayLedger.transactions.sortedBy { it.timestamp }
            }

            if (sortedTxs.isEmpty()) {
                LaunchedEffect(Unit) {
                    onDismiss()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(sortedTxs, key = { it.id }) { tx ->
                        val itemBg = if (tx.type == "INCOME") {
                            Color(0xFFF3FAF5)
                        } else {
                            Color(0xFFFFF7F7)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(itemBg)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    modifier = Modifier.size(28.dp),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDeleteTransaction(tx.id)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(id = R.string.ledger_commitment_delete),
                                        tint = SoftRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.size(28.dp),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEditTransaction(tx)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(id = R.string.ledger_edit_transaction_title),
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = (if (tx.type == "INCOME") "+" else "-") +
                                                formatDoubleCurrency(tx.amount, currencySymbol),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (tx.type == "INCOME") SoftGreen else SoftRed
                                    )
                                    Text(
                                        text = DateUtils.formatTime24Or12(tx.timestamp),
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = tx.description.ifBlank { stringResource(id = R.string.ledger_unspecified_description) },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Right
                                    )
                                    if (tx.category.isNotBlank() && tx.category != stringResource(id = R.string.ledger_category_expense)) {
                                        Text(
                                            text = tx.category,
                                            fontSize = 9.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Right
                                        )
                                    }
                                }

                                val parsedEmoji = extractEmoji(tx.category, if (tx.type == "INCOME") "💰" else "🛒")
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(getEmojiBgColor(parsedEmoji), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = parsedEmoji, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F1EF))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.ledger_daily_totals_title),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = (if (activeDayLedger.netAmount.compareTo(BigDecimal.ZERO) >= 0) "+" else "") +
                                formatCurrency(activeDayLedger.netAmount, currencySymbol),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (activeDayLedger.netAmount.compareTo(BigDecimal.ZERO) >= 0) SoftGreen else SoftRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(stringResource(id = R.string.ledger_done_close_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val builder = java.lang.StringBuilder()
                val income = activeDayLedger.transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
                val expense = activeDayLedger.transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                builder.append(context.getString(R.string.ledger_daily_record_share_title, activeDayLedger.dayOfWeek, activeDayLedger.fullDate))
                builder.append(context.getString(R.string.ledger_share_total_income, formatDoubleCurrency(income, currencySymbol)))
                builder.append(context.getString(R.string.ledger_share_total_expense, formatDoubleCurrency(expense, currencySymbol)))
                builder.append("___________________\n\n")

                val txs = activeDayLedger.transactions.sortedBy { it.timestamp }
                if (txs.isEmpty()) {
                    builder.append(context.getString(R.string.ledger_no_txs_today))
                } else {
                    txs.forEach { tx ->
                        val icon = if (tx.type == "INCOME") "🟢 (+)" else "🔴 (-)"
                        builder.append(context.getString(R.string.ledger_share_tx_format, icon, tx.category, formatDoubleCurrency(tx.amount, currencySymbol)))
                        if (tx.description.isNotBlank()) {
                            builder.append(context.getString(R.string.ledger_share_tx_desc, tx.description))
                        }
                    }
                }

                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, builder.toString())
                }
                try {
                    shareIntent.setPackage("com.whatsapp")
                    context.startActivity(shareIntent)
                } catch (e: android.content.ActivityNotFoundException) {
                    try {
                        shareIntent.setPackage("com.whatsapp.w4b")
                        context.startActivity(shareIntent)
                    } catch (e2: android.content.ActivityNotFoundException) {
                        shareIntent.setPackage(null)
                        context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.ledger_share_via)))
                    }
                }
            }) {
                Text(stringResource(id = R.string.ledger_whatsapp_whatsapp), color = Color(0xFF25D366), fontWeight = FontWeight.Bold)
            }
        }
    )
}
