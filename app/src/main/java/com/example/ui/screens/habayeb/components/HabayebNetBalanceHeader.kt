package com.example.ui.screens.habayeb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

    // Dynamic background gradient brush based on the state of net balance
    val netCardBrush = remember(netTotal) {
        if (netTotal > 0.0) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF3F51B5), // Royal Indigo
                    Color(0xFF0F5257)  // Forest Deep Emerald
                )
            )
        } else if (netTotal < 0.0) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF3F51B5), // Royal Indigo
                    Color(0xFF991B1B)  // Ruby Crimson
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF3F51B5), // Royal Indigo
                    Color(0xFF475569)  // Slate Grey
                )
            )
        }
    }

    // Dynamic title text with contextual arrow indicating asset vs liability
    val netTitle = when {
        netTotal > 0.0 -> stringResource(id = R.string.habayeb_net_title_assets)
        netTotal < 0.0 -> stringResource(id = R.string.habayeb_net_title_liabilities)
        else -> stringResource(id = R.string.habayeb_net_title_balanced)
    }

    // Dynamic explanation capsule badge text
    val netBadgeText = when {
        netTotal > 0.0 -> stringResource(id = R.string.habayeb_net_badge_assets)
        netTotal < 0.0 -> stringResource(id = R.string.habayeb_net_badge_liabilities)
        else -> stringResource(id = R.string.habayeb_net_badge_balanced)
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
                containerColor = Color.Transparent
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = netCardBrush, shape = RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = netTitle,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(id = R.string.habayeb_visibility_toggle),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AutoScaleText(
                        text = if (isPrivacyMode) "*****" else formatCurrency(netTotal, currencySymbol),
                        baseFontSize = 24.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Glassmorphic explanation Capsule Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = netBadgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
