package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.navigation.Screen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.AccountBalanceWallet

@Composable
fun MainBottomNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items = remember(context) {
        listOf(
            Triple(Screen.HABAYEB, Icons.Default.People, context.getString(R.string.nav_habayeb_plain)),
            Triple(Screen.LEDGER, Icons.Default.AccountBalanceWallet, context.getString(R.string.nav_ledger_plain))
        )
    }

    if (currentScreen != Screen.HABAYEB && currentScreen != Screen.LEDGER) return

    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .navigationBarsPadding() // Perfect safety padding against system navigation bar overlap
            .fillMaxWidth(0.85f)
            .height(58.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(29.dp),
                clip = false,
                ambientColor = Color(0xFF1E293B).copy(alpha = 0.12f),
                spotColor = Color(0xFF1E293B).copy(alpha = 0.22f)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFFAFAFA))
                ),
                shape = RoundedCornerShape(29.dp)
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFE2E8F0))
                ),
                shape = RoundedCornerShape(29.dp)
            )
            .padding(4.dp)
    ) {
        val totalWidth = maxWidth
        val itemWidth = totalWidth / 2

        // Smooth sliding background pill
        val targetIndex = if (currentScreen == Screen.HABAYEB) 0 else 1
        val targetOffset = if (layoutDirection == LayoutDirection.Rtl) {
            if (targetIndex == 0) itemWidth else 0.dp
        } else {
            if (targetIndex == 0) 0.dp else itemWidth
        }

        val offsetX by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "PillSlide"
        )

        // The high-fidelity sliding indicator
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(2.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(25.dp),
                    clip = false,
                    ambientColor = Color(0xFF0F5257).copy(alpha = 0.15f),
                    spotColor = Color(0xFF0F5257).copy(alpha = 0.25f)
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F5257), // EmeraldPrimary
                            Color(0xFF1E3A8A)  // Deep Navy
                        )
                    ),
                    shape = RoundedCornerShape(25.dp)
                )
        )

        // Row of tabs
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (screen, icon, label) ->
                val isSelected = currentScreen == screen

                // State-driven animations for icons and labels
                val textAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.65f,
                    animationSpec = tween(durationMillis = 200),
                    label = "TextAlpha"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "IconScale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(25.dp))
                        .clickable { onNavigate(screen) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Color.White else Color(0xFF64748B),
                            modifier = Modifier
                                .scale(iconScale)
                                .size(20.dp)
                        )
                        Text(
                            text = label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF64748B),
                            modifier = Modifier.alpha(textAlpha),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
