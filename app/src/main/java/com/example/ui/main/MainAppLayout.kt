package com.example.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entities.AppSettings
import com.example.ui.components.*
import com.example.ui.navigation.Screen
import com.example.ui.screens.BackupRestoreBottomSheet
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.ui.viewmodel.SyncSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppLayout(
    viewModel: FinanceViewModel,
    syncViewModel: SyncSettingsViewModel,
    settings: AppSettings,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.2"
        } catch (e: Exception) {
            "1.2"
        }
    }
    val sdfName = remember { java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US) }
    val defaultStartDest by viewModel.defaultStartDestinationState.collectAsStateWithLifecycle()
    
    var currentScreen by remember { mutableStateOf(Screen.HABAYEB) }
    var hasInitializedStartScreen by remember { mutableStateOf(false) }

    LaunchedEffect(defaultStartDest) {
        if (!hasInitializedStartScreen) {
            currentScreen = try {
                Screen.valueOf(defaultStartDest)
            } catch (e: Exception) {
                Screen.HABAYEB
            }
            hasInitializedStartScreen = true
        }
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showBackupRestoreSheet by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            syncViewModel.getBackupJsonForClipboard { jsonStr ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(jsonStr.toByteArray())
                            }
                        }
                        Toast.makeText(context, context.getString(R.string.toast_backup_export_success), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.toast_backup_export_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val safRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonText = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    if (jsonText.isNotBlank()) {
                        syncViewModel.executeMasterRestore(jsonText, context) { success, _ ->
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.toast_restore_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_restore_invalid_file), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val totalCash by viewModel.totalCashState.collectAsStateWithLifecycle()
    val commitments by viewModel.commitmentsState.collectAsStateWithLifecycle()
    val monthlyLedger by viewModel.monthlyLedgerState.collectAsStateWithLifecycle()

    BackHandler {
        val defaultStart = try {
            Screen.valueOf(defaultStartDest)
        } catch (e: Exception) {
            Screen.HABAYEB
        }
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentScreen != defaultStart) {
            currentScreen = defaultStart
        } else {
            if (settings.doubleCheckExit) {
                showExitConfirmDialog = true
            } else {
                onExit()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    currentScreen = screen
                    scope.launch { drawerState.close() }
                },
                onBackupClick = {
                    scope.launch { drawerState.close() }
                    showBackupRestoreSheet = true
                },
                versionName = versionName
            )
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            bottomBar = {
                MainBottomNavigation(
                    currentScreen = currentScreen,
                    onNavigate = { currentScreen = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MainAppContent(
                    currentScreen = currentScreen,
                    viewModel = viewModel,
                    syncViewModel = syncViewModel,
                    settings = settings,
                    monthlyLedger = monthlyLedger,
                    totalCash = totalCash,
                    commitments = commitments,
                    onNavigate = { currentScreen = it },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onExit = {
                        if (settings.doubleCheckExit) {
                            showExitConfirmDialog = true
                        } else {
                            onExit()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    ExitConfirmDialog(
        show = showExitConfirmDialog,
        onDismiss = { showExitConfirmDialog = false },
        onConfirm = { dontShowAgain ->
            if (dontShowAgain) {
                viewModel.saveSettings(settings.copy(doubleCheckExit = false))
            }
            showExitConfirmDialog = false
            onExit()
        }
    )

    if (showBackupRestoreSheet) {
        BackupRestoreBottomSheet(
            settings = settings,
            viewModel = viewModel,
            syncViewModel = syncViewModel,
            onExportMzd = {
                val dateStr = sdfName.format(java.util.Date())
                safExportLauncher.launch("Mizan_$dateStr.mzd")
            },
            onImportMzd = {
                safRestoreLauncher.launch(arrayOf("application/*"))
            },
            onImportBase64 = { base64JsonText ->
                syncViewModel.executeMasterRestore(base64JsonText, context) { success, _ ->
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.toast_sync_restore_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_sync_decrypt_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showBackupRestoreSheet = false }
        )
    }
}
