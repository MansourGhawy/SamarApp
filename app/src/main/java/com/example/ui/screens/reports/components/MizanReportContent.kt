package com.example.ui.screens.reports.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

fun LazyListScope.mizanReportContent(
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
    onShowCustomDialog: () -> Unit,
    totalIncome: BigDecimal,
    totalExpense: BigDecimal,
    netSavings: BigDecimal,
    isChartExpanded: Boolean,
    onToggleChartExpanded: () -> Unit,
    categoryTotals: List<Pair<String, BigDecimal>>,
    segmentColors: List<Color>,
    formatVal: (BigDecimal) -> String
) {
    item { Spacer(modifier = Modifier.height(8.dp)) }

    // Date period filtering selection row
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFF1F1EF), RoundedCornerShape(16.dp))
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PeriodTab(
                title = stringResource(R.string.reports_period_daily),
                selected = selectedPeriod == "DAILY"
            ) { onPeriodSelected("DAILY") }

            PeriodTab(
                title = stringResource(R.string.reports_period_weekly),
                selected = selectedPeriod == "WEEKLY"
            ) { onPeriodSelected("WEEKLY") }

            PeriodTab(
                title = stringResource(R.string.reports_period_monthly),
                selected = selectedPeriod == "MONTHLY"
            ) { onPeriodSelected("MONTHLY") }

            PeriodTab(
                title = stringResource(R.string.reports_period_yearly),
                selected = selectedPeriod == "YEARLY"
            ) { onPeriodSelected("YEARLY") }

            PeriodTab(
                title = stringResource(R.string.reports_period_custom),
                selected = selectedPeriod == "CUSTOM"
            ) {
                onPeriodSelected("CUSTOM")
                onShowCustomDialog()
            }

            PeriodTab(
                title = stringResource(R.string.reports_period_all),
                selected = selectedPeriod == "ALL"
            ) { onPeriodSelected("ALL") }
        }
    }

    // Cash flow balance card
    item {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reports_total_income_label), fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(formatVal(totalIncome), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                }

                Box(modifier = Modifier.width(1.dp).height(35.dp).background(Color(0xFFE9ECEF)))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reports_total_expense_label), fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(formatVal(totalExpense), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoftRed)
                }

                Box(modifier = Modifier.width(1.dp).height(35.dp).background(Color(0xFFE9ECEF)))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reports_net_savings_label), fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        formatVal(netSavings),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (netSavings >= BigDecimal.ZERO) SoftGreen else SoftRed
                    )
                }
            }
        }
    }

    item { Spacer(modifier = Modifier.height(24.dp)) }
}
