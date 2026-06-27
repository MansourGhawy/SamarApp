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

    // Donut Pie chart (Expansible)
    item {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleChartExpanded() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.reports_expenses_chart_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isChartExpanded) "▲" else "▼",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                AnimatedVisibility(
                    visible = isChartExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(14.dp))
                        if (categoryTotals.isEmpty()) {
                            Column(
                                modifier = Modifier.padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🎂", fontSize = 38.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.reports_no_expenses_message),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(170.dp)
                            ) {
                                Canvas(modifier = Modifier.size(140.dp)) {
                                    val total = totalExpense.toDouble()
                                    var currentStartAngle = -90f

                                    categoryTotals.forEachIndexed { index, (_, amount) ->
                                        val sweepAngle = (amount.toDouble() / total * 360f).toFloat()
                                        val col = segmentColors[index % segmentColors.size]

                                        drawArc(
                                            color = col,
                                            startAngle = currentStartAngle,
                                            sweepAngle = sweepAngle,
                                            useCenter = false,
                                            size = Size(size.width, size.height),
                                            style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        currentStartAngle += sweepAngle
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.reports_total_spent_label), fontSize = 10.sp, color = Color.Gray)
                                    Text(formatVal(totalExpense), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Ledger Categories list breakdown
    if (categoryTotals.isNotEmpty()) {
        item {
            Text(
                stringResource(R.string.reports_expenses_by_category_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        items(categoryTotals) { (cat, amount) ->
            val idx = categoryTotals.indexOfFirst { it.first == cat }
            val col = segmentColors[idx % segmentColors.size]
            val pct = if (totalExpense > BigDecimal.ZERO) {
                amount.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 1, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F1EF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(formatVal(amount), fontWeight = FontWeight.Bold, color = EmeraldPrimary, fontSize = 13.sp)
                        Text(stringResource(R.string.reports_expense_percentage_pattern, pct.toString()), fontSize = 10.sp, color = Color.Gray)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.padding(end = 10.dp))
                        Box(modifier = Modifier.size(width = 20.dp, height = 10.dp).clip(RoundedCornerShape(4.dp)).background(col))
                    }
                }
            }
        }
    }

    item { Spacer(modifier = Modifier.height(24.dp)) }
}
