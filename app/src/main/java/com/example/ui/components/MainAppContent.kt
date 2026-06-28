package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.local.entities.AppSettings
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.MonthLedger
import java.math.BigDecimal

@Composable
fun MainAppContent(
    currentScreen: Screen,
    viewModel: FinanceViewModel,
    settings: AppSettings,
    monthlyLedger: List<MonthLedger>,
    totalCash: BigDecimal,
    commitments: List<com.example.data.local.entities.FixedCommitment>,
    contentPadding: PaddingValues = PaddingValues(),
    onNavigate: (Screen) -> Unit,
    onMenuClick: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = currentScreen,
            animationSpec = tween(durationMillis = 200),
            label = "ScreenSwitch"
        ) { screen ->
            when (screen) {
                Screen.HABAYEB -> {
                    HabayebScreen(
                        viewModel = viewModel,
                        onMenuClick = onMenuClick,
                        onClose = onExit,
                        contentPadding = contentPadding
                    )
                }
                Screen.LEDGER -> {
                    MainLedgerView(
                        viewModel = viewModel,
                        monthlyLedger = monthlyLedger,
                        totalCash = totalCash,
                        commitments = commitments,
                        settings = settings,
                        onBackIntercept = {},
                        onMenuClick = onMenuClick,
                        contentPadding = contentPadding
                    )
                }
                Screen.REPORTS -> {
                    ReportsView(
                        viewModel = viewModel,
                        settings = settings,
                        currencySymbol = settings.currencySymbol,
                        contentPadding = contentPadding
                    )
                }
                Screen.SETTINGS -> {
                    SettingsView(
                        viewModel = viewModel,
                        settings = settings,
                        onNavigateToSecurity = { onNavigate(Screen.SECURITY) },
                        contentPadding = contentPadding
                    )
                }
                Screen.TRASH -> {
                    TrashScreen(
                        viewModel = viewModel,
                        onBack = { onNavigate(Screen.HABAYEB) },
                        contentPadding = contentPadding
                    )
                }
                Screen.BUSINESS_PROFILE -> {
                    BusinessProfileScreen(
                        viewModel = viewModel,
                        onBack = { onNavigate(Screen.HABAYEB) },
                        contentPadding = contentPadding
                    )
                }
                Screen.SECURITY -> {
                    SecurityScreen(
                        settings = settings,
                        viewModel = viewModel,
                        onBack = { onNavigate(Screen.LEDGER) },
                        contentPadding = contentPadding
                    )
                }
            }
        }
    }
}
