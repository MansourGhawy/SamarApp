package com.example.ui.screens.ledger.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed
import java.math.BigDecimal

@Composable
fun BudgetAdviceBanner(diffExp: BigDecimal) {
    val showBanner = diffExp.compareTo(BigDecimal.ZERO) > 0

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (showBanner) SoftRed.copy(alpha = 0.08f) else SoftGreen.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (showBanner) {
                Text(
                    text = stringResource(id = R.string.ledger_obligation_reached_high, diffExp.toString()),
                    fontSize = 12.sp,
                    color = SoftRed,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Right,
                    lineHeight = 16.sp
                )
            } else {
                Text(
                    text = stringResource(id = R.string.ledger_financial_status_stable),
                    fontSize = 12.sp,
                    color = SoftGreen,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Right,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
