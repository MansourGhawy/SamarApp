package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.WelcomeOnboardingDialog
import android.content.pm.PackageManager
import java.security.MessageDigest
import com.example.ui.main.MainAppLayout
import com.example.ui.screens.AppLockScreen
import com.example.ui.theme.MizanTheme
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.screens.habayeb.utils.HabayebRecurringManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logAppSignatureSHA1(this)
        enableEdgeToEdge()

        // Schedule daily automatic backup at end of day
        AutoBackupWorker.scheduleDailyBackupWorker(this)

        setContent {
            val viewModel: FinanceViewModel = viewModel()

            val context = LocalContext.current
            LaunchedEffect(viewModel) {
                withContext(Dispatchers.IO) {
                    // Check and execute any recurring transactions on startup safely on background thread
                    HabayebRecurringManager.checkAndExecuteRecurring(context, viewModel) { count ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.toast_recurring_txs_success, count),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                viewModel.uiEventFlow.collect { event ->
                    when (event) {
                        is com.example.ui.viewmodel.UiEvent.ShowToast -> {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(event.messageRes),
                                if (event.isLong) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        is com.example.ui.viewmodel.UiEvent.ShareFile -> {
                            com.example.ui.helper.shareBackupFile(context, event.file)
                        }
                        is com.example.ui.viewmodel.UiEvent.OpenGoogleDriveApp -> {
                            com.example.ui.helper.openGoogleDriveApp(context)
                        }
                    }
                }
            }

            val settings by viewModel.settingsState.collectAsStateWithLifecycle()
            val isSettingsLoaded by viewModel.isSettingsLoaded.collectAsStateWithLifecycle()
            
            var isUnlocked by remember { mutableStateOf(false) }

            var permissionRequested by remember { mutableStateOf(false) }
            var showOnboardingDialog by remember { mutableStateOf(false) }
            var shouldRequestPermissions by remember { mutableStateOf(false) }

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }
                android.util.Log.d("MainActivity", "Permissions completed: allGranted=$allGranted")
            }

            LaunchedEffect(shouldRequestPermissions) {
                if (shouldRequestPermissions) {
                    val permissions = mutableListOf<String>()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
                        permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        permissions.add("android.permission.READ_MEDIA_IMAGES")
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                    shouldRequestPermissions = false
                }
            }

            // Strictly check first launch status on start, merging database settings and highly-persistent SharedPreferences lock
            val isReallyFirstLaunch = settings.isFirstLaunch && !viewModel.hasShownOnboarding()
            LaunchedEffect(isReallyFirstLaunch) {
                if (isReallyFirstLaunch) {
                    // Let the user breathe, see and experience the app interface behind first (3500ms elegant delay)
                    kotlinx.coroutines.delay(3500)
                    showOnboardingDialog = true
                }
            }

            val darkTheme = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MizanTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (!isSettingsLoaded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Clean elegant dark background matching the matte night styling of the lock screen
                        }
                    } else {
                        if (isReallyFirstLaunch && showOnboardingDialog) {
                            WelcomeOnboardingDialog(
                                onDismiss = {
                                    viewModel.markOnboardingShown() // Persist in SharedPreferences first
                                    val updated = settings.copy(isFirstLaunch = false)
                                    viewModel.saveSettings(updated)
                                    showOnboardingDialog = false
                                    shouldRequestPermissions = true // Request storage/post permissions immediately after welcome greeting!
                                }
                            )
                        }

                        if (settings.isPasscodeEnabled && !isUnlocked) {
                            AppLockScreen(
                                viewModel = viewModel,
                                onUnlockSuccess = { isUnlocked = true },
                                onUnlockBypassedAndDisabled = {
                                    val updated = settings.copy(
                                        isPasscodeEnabled = false,
                                        passcodeHash = null,
                                        recoveryPhraseHash = null
                                    )
                                    viewModel.saveSettings(updated)
                                    isUnlocked = true
                                }
                            )
                        } else {
                            MainAppLayout(viewModel = viewModel, settings = settings, onExit = { 
                                finishAffinity() 
                            })
                        }
                    }
                }
            }
        }
    }

    private fun logAppSignatureSHA1(context: android.content.Context) {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val signatures = info.signatures
            if (signatures != null) {
                for (signature in signatures) {
                    val md = MessageDigest.getInstance("SHA1")
                    val publicKey = md.digest(signature.toByteArray())
                    val hexString = publicKey.joinToString(":") { String.format("%02X", it) }
                    android.util.Log.d("GOOGLE_AUTH_DEBUG", "SHA-1 ACTUAL SIGNATURE: $hexString")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GOOGLE_AUTH_DEBUG", "Error getting signature", e)
        }
    }
}
