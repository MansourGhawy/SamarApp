package com.example.ui.screens.ledger.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.FixedCommitment
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
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card 1: Net Amount Capsule matching Row 1 style & size
        Box(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEDF7ED)) // Light Pastel Green
                .border(
                    1.dp,
                    Color(0xFFC8E6C9), // Soft green boundary
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.ledger_net_prefix, formatCurrency(BigDecimal.valueOf(netAmount), currencySymbol)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32), // Clear green readability
                textAlign = TextAlign.Center
            )
        }

        // Card 2: Remaining Commitments Capsule matching Row 1 style & size
        Box(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFDEDED)) // Light Pastel Red
                .border(
                    1.dp,
                    Color(0xFFFFCDD2), // Soft red boundary
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.ledger_remaining_commitments, formatCurrency(BigDecimal.valueOf(totalRemainingCommitments), currencySymbol)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC62828), // Clear red readability
                textAlign = TextAlign.Center
            )
        }
    }
}
