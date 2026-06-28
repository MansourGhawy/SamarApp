package com.example.ui.screens.habayeb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.helper.AutoScaleText
import com.example.ui.helper.formatCurrency

@Composable
fun HabayebFilterTabs(
    selectedFilterTab: Int,
    onFilterTabSelected: (Int) -> Unit,
    totalOwedByThem: Double,
    totalOwedToThem: Double,
    currencySymbol: String,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Right: "لي عند الناس" (Filter Tab 1) - Green Pastel Card
        val isTab1Selected = selectedFilterTab == 1
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE8F5E9)
            ),
            border = BorderStroke(
                width = if (isTab1Selected) 2.dp else 1.dp,
                color = if (isTab1Selected) Color(0xFF10B981) else Color(0xFFA7F3D0)
            ),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    onFilterTabSelected(if (isTab1Selected) 0 else 1)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.habayeb_filter_owed_by),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF047857)
                )
                Spacer(modifier = Modifier.height(2.dp))
                AutoScaleText(
                    text = formatCurrency(totalOwedByThem, currencySymbol),
                    baseFontSize = 14.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Left: "علي للناس" (Filter Tab 2) - Red Pastel Card
        val isTab2Selected = selectedFilterTab == 2
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFEBEE)
            ),
            border = BorderStroke(
                width = if (isTab2Selected) 2.dp else 1.dp,
                color = if (isTab2Selected) Color(0xFFEF4444) else Color(0xFFFECACA)
            ),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    onFilterTabSelected(if (isTab2Selected) 0 else 2)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.habayeb_filter_owed_to),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB91C1C)
                )
                Spacer(modifier = Modifier.height(2.dp))
                AutoScaleText(
                    text = formatCurrency(totalOwedToThem, currencySymbol),
                    baseFontSize = 14.sp,
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
