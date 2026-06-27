package com.example.ui.screens.habayeb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.screens.AutoScaleText
import com.example.ui.screens.formatCurrency

@Composable
fun HabayebNetBalanceHeader(
    totalOwedByThem: Double,
    totalOwedToThem: Double,
    currencySymbol: String,
    isPrivacyMode: Boolean,
    onTogglePrivacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val netTotal = totalOwedByThem - totalOwedToThem

    // Solid Royal Indigo background (No bad new colors or gradients)
    val cardBgColor = Color(0xFF3F51B5)

    // Dynamic title text exactly as requested:
    // "إجمالي الصافي لك" if netTotal > 0
    // "إجمالي الصافي عليك" if netTotal < 0
    // "إجمالي الرصيد الصافي" if netTotal == 0
    val netTitle = when {
        netTotal > 0.0 -> "إجمالي الصافي لك"
        netTotal < 0.0 -> "إجمالي الصافي عليك"
        else -> "إجمالي الرصيد الصافي"
    }

    // Dynamic amount color: Red if money is owed to people (netTotal < 0.0), otherwise White
    val amountColor = if (netTotal < 0.0) {
        Color(0xFFFF5252) // Bright readable red
    } else {
        Color.White
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBgColor
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = netTitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier.size(24.dp).padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(id = R.string.habayeb_visibility_toggle),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    AutoScaleText(
                        text = if (isPrivacyMode) "*****" else formatCurrency(netTotal, currencySymbol),
                        baseFontSize = 26.sp,
                        color = amountColor,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
