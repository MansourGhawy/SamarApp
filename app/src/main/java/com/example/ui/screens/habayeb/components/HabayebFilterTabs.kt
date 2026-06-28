package com.example.ui.screens.habayeb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

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
    // ضغط كتل التصفية بالكامل أفقياً بارتفاع 38dp ميكروي مميز
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // تبويب الكل الرشيق
        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selectedFilterTab == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else Color.Transparent
                )
                .border(
                    BorderStroke(
                        0.8.dp,
                        if (selectedFilterTab == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    RoundedCornerShape(12.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFilterTabSelected(0)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "الكل",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedFilterTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // كبسولة لي عند الناس الأفقية منخفضة الارتفاع
        val isOwedByThemSelected = selectedFilterTab == 1
        val formattedOwedByThem = String.format(java.util.Locale.ENGLISH, "%,.0f", totalOwedByThem)
        Box(
            modifier = Modifier
                .weight(1.8f)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isOwedByThemSelected) Color(0xFFE8F5E9)
                    else Color.Transparent
                )
                .border(
                    BorderStroke(
                        0.8.dp,
                        if (isOwedByThemSelected) Color(0xFF10B981)
                        else Color(0xFF10B981).copy(alpha = 0.25f)
                    ),
                    RoundedCornerShape(12.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFilterTabSelected(1)
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "لي عند الناس: ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isOwedByThemSelected) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.8f)
                )
                Text(
                    text = "$formattedOwedByThem $currencySymbol",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOwedByThemSelected) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.8f)
                )
            }
        }

        // كبسولة علي للناس الأفقية منخفضة الارتفاع
        val isOwedToThemSelected = selectedFilterTab == 2
        val formattedOwedToThem = String.format(java.util.Locale.ENGLISH, "%,.0f", totalOwedToThem)
        Box(
            modifier = Modifier
                .weight(1.8f)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isOwedToThemSelected) Color(0xFFFFEBEE)
                    else Color.Transparent
                )
                .border(
                    BorderStroke(
                        0.8.dp,
                        if (isOwedToThemSelected) Color(0xFFEF4444)
                        else Color(0xFFEF4444).copy(alpha = 0.25f)
                    ),
                    RoundedCornerShape(12.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFilterTabSelected(2)
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "علي للناس: ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isOwedToThemSelected) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.8f)
                )
                Text(
                    text = "$formattedOwedToThem $currencySymbol",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOwedToThemSelected) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.8f)
                )
            }
        }
    }
}
