package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    val activeThemeColor = Color(0xFF3F51B5) // Royal Indigo
    val activeSubColor = Color(0xFFE8EAF6)   // Pastel Lavender/Blue

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(64.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(32.dp),
                    clip = false,
                    ambientColor = Color(0xFF1E293B).copy(alpha = 0.08f),
                    spotColor = Color(0xFF1E293B).copy(alpha = 0.12f)
                )
                .background(Color.White, shape = RoundedCornerShape(32.dp))
                .border(
                    width = 1.dp,
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { (screen, icon, label) ->
                val isSelected = currentScreen == screen

                if (isSelected) {
                    // Selected state: capsule background, showing both icon and text
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(activeSubColor)
                            .clickable { onNavigate(screen) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = activeThemeColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = activeThemeColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else {
                    // Non-selected state: transparent background, showing only icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onNavigate(screen) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
