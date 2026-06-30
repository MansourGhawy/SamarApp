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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF4B36A2).copy(alpha = 0.4f),
                spotColor = Color(0xFF4B36A2).copy(alpha = 0.4f)
            )
            .border(
                1.dp,
                Color(0xFFF0F3FC),
                RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFFFFFFF))
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = Color(0xFF1E1A3E).copy(alpha = 0.5f),
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            items.forEach { (screen, icon, label) ->
                NavigationBarItem(
                    selected = currentScreen == screen,
                    onClick = { onNavigate(screen) },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (currentScreen == screen) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4B36A2),
                        unselectedIconColor = Color(0xFF1E1A3E).copy(alpha = 0.5f),
                        selectedTextColor = Color(0xFF4B36A2),
                        unselectedTextColor = Color(0xFF1E1A3E).copy(alpha = 0.5f),
                        indicatorColor = Color(0xFF8C7CFF).copy(alpha = 0.15f)
                    )
                )
            }
        }
    }
}
