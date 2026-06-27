package com.example.ui.screens.habayeb.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun HabayebFilterToolbar(
    filteredCustomersCount: Int,
    financialSortMode: Int,
    onFinancialSortModeChanged: (Int) -> Unit,
    historicalSortMode: Int,
    onHistoricalSortModeChanged: (Int) -> Unit,
    activeThemeColor: Color,
    activeSubColor: Color,
    haptic: HapticFeedback,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Right side (RTL left): Count Badge
        Box(
            modifier = Modifier
                .height(28.dp)
                .background(activeSubColor, RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.habayeb_customers_count, filteredCustomersCount),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = activeThemeColor
            )
        }

        // Left side (RTL right): Filtering and Sorting Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Financial Sorting Button
            val isFinSorted = financialSortMode != 0
            val finText = when (financialSortMode) {
                1 -> stringResource(id = R.string.habayeb_sort_amount_desc)
                2 -> stringResource(id = R.string.habayeb_sort_amount_asc)
                else -> stringResource(id = R.string.habayeb_sort_amount)
            }
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isFinSorted) activeSubColor else activeSubColor.copy(alpha = 0.5f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onHistoricalSortModeChanged(0)
                        val nextFin = when (financialSortMode) {
                            0 -> 1
                            1 -> 2
                            else -> 0
                        }
                        onFinancialSortModeChanged(nextFin)
                        onScrollToTop()
                    }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = finText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = activeThemeColor
                )
            }

            // Historical Sorting Button
            val isHistSorted = historicalSortMode != 0
            val histText = when (historicalSortMode) {
                1 -> stringResource(id = R.string.habayeb_sort_date_desc)
                2 -> stringResource(id = R.string.habayeb_sort_date_asc)
                else -> stringResource(id = R.string.habayeb_sort_date)
            }
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isHistSorted) activeSubColor else activeSubColor.copy(alpha = 0.5f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFinancialSortModeChanged(0)
                        val nextHist = when (historicalSortMode) {
                            0 -> 1
                            1 -> 2
                            else -> 0
                        }
                        onHistoricalSortModeChanged(nextHist)
                        onScrollToTop()
                    }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = histText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = activeThemeColor
                )
            }
        }
    }
}
