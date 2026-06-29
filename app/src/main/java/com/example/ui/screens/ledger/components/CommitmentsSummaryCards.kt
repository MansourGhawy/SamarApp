package com.example.ui.screens.ledger.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entities.FixedCommitment
import java.math.BigDecimal

@Composable
fun CommitmentsSummaryCards(
    commitments: List<FixedCommitment>,
    computedCommitments: List<Triple<FixedCommitment, Double, Double>>,
    totalCash: BigDecimal,
    currencySymbol: String,
    formatCurrency: (BigDecimal, String) -> String,
    modifier: Modifier = Modifier
) {
    if (commitments.isEmpty()) return

    val totalRemainingCommitments = computedCommitments.sumOf { it.third }
    val allocatedFromCashTotal = computedCommitments.sumOf {
        val needed = (it.first.targetAmount - it.first.currentProgress).coerceAtLeast(0.0)
        needed - it.third
    }
    val netAmount = (totalCash.toDouble() - allocatedFromCashTotal).coerceAtLeast(0.0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card 1: Net Amount Capsule
        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEDF7ED).copy(alpha = 0.5f))
                .border(
                    BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.ledger_net_prefix)} ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = formatCurrency(BigDecimal.valueOf(netAmount), currencySymbol),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32)
                )
            }
        }

        // Card 2: Remaining Commitments Capsule
        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFDEDED).copy(alpha = 0.5f))
                .border(
                    BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.ledger_remaining_commitments)} ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFC62828)
                )
                Text(
                    text = formatCurrency(BigDecimal.valueOf(totalRemainingCommitments), currencySymbol),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFC62828)
                )
            }
        }
    }
}
