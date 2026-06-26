package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AppSettings
import com.example.data.CloudSyncState
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import android.app.Activity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    viewModel: FinanceViewModel,
    settings: AppSettings
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val systemInDarkMode = androidx.compose.foundation.isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = true // Dark texts for Ivory background
            insetsController.isAppearanceLightNavigationBars = !systemInDarkMode
        }
    }
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val googleCloudSyncState by viewModel.googleDriveSyncState.collectAsStateWithLifecycle()
    var showCloudBackupsSheet by remember { mutableStateOf(false) }
    var isSyncLoggingOut by remember { mutableStateOf(false) }
    var showBackupPermissionExplanationDialog by remember { mutableStateOf(false) }
    var onPermissionGrantedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showGoogleLoginWebView by remember { mutableStateOf(false) }

    var showTrapDialog by remember { mutableStateOf(false) }
    var currencySymbol by remember { mutableStateOf(settings.currencySymbol) }
    var schoolExpenses by remember { mutableStateOf(settings.schoolExpensesEnabled) }
    var isAutoBackupEnabled by remember { mutableStateOf(settings.isAutoBackupEnabled) }
    
    // Backup Paste UI states
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedBackupText by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Security Passcode Setup Sheet states
    var showPasscodeSetupSheet by remember { mutableStateOf(false) }
    var tempPasscode by remember { mutableStateOf("") }
    var tempConfirmPasscode by remember { mutableStateOf("") }
    var tempRecoveryPhrase by remember { mutableStateOf("") }
    var tempRecoveryHint by remember { mutableStateOf("") }
    var tempCheckAcknowledged by remember { mutableStateOf(false) }

    // Identity Verification Gate states
    var showSecurityGateDialog by remember { mutableStateOf(false) }
    var securityGateInput by remember { mutableStateOf("") }
    var securityGateError by remember { mutableStateOf("") }
    var securityGateAction by remember { mutableStateOf("") } // "TOGGLE_OFF", "CHANGE_PASSCODE", "CHANGE_RECOVERY"

    val coroutineScope = rememberCoroutineScope()

    // Backup SAF Create Document launcher
    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.getBackupJsonForClipboard { jsonStr ->
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(jsonStr.toByteArray())
                            launch(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.settings_toast_synced_desc), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.toast_backup_export_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Backup SAF Open Document launcher
    val safRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (jsonText.isNotBlank()) {
                    viewModel.executeMasterRestore(jsonText, context) { success, restoredSettings ->
                        if (success && restoredSettings != null) {
                            // Reload settings safely using newly restored values
                            currencySymbol = restoredSettings.currencySymbol
                            schoolExpenses = restoredSettings.schoolExpensesEnabled
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val checkBackupPermissionsGranted = remember(context) {
        {
            val hasWrite = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            
            val hasRead = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            val hasNotification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            
            val hasManage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else true
            
            hasWrite && hasRead && hasNotification && hasManage
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val writeGranted = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            results[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        } else true
        
        val readGranted = results[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val notificationGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            results[android.Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Toast.makeText(context, context.getString(R.string.settings_toast_permission_manage_files), Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            } else {
                onPermissionGrantedCallback?.invoke()
            }
        } else {
            if (writeGranted && readGranted) {
                onPermissionGrantedCallback?.invoke()
            } else {
                Toast.makeText(context, context.getString(R.string.settings_toast_permission_denied_err), Toast.LENGTH_LONG).show()
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            viewModel.createLocalBackup(context) { }
        } else {
            Toast.makeText(context, context.getString(R.string.settings_toast_permission_denied_simple), Toast.LENGTH_SHORT).show()
        }
    }

    // Official Google Sign-In SDK configuration
    val googleSignInClient = remember(context) {
        viewModel.googleDriveSyncHelper.getGoogleSignInClient(context)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intent = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && intent != null) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(intent)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val authCode = account?.serverAuthCode
                val email = account?.email ?: "account@google.com"
                if (authCode != null) {
                    viewModel.handleGoogleOAuthCode(authCode, email) { success ->
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.settings_gdrive_link_success_pattern, email), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.settings_gdrive_link_failed_network), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.settings_gdrive_link_failed_invalid_code), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SettingsView", "Google sign in failed", e)
                Toast.makeText(context, context.getString(R.string.settings_gdrive_link_failed_error_pattern, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
            }
        } else {
            var handledError = false
            if (intent != null) {
                try {
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(intent)
                    task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    val sc = e.statusCode
                    Log.e("SettingsView", "Sign in failed with code $sc", e)
                    Toast.makeText(context, context.getString(R.string.settings_gdrive_link_failed_api_code_pattern, sc), Toast.LENGTH_LONG).show()
                    handledError = true
                } catch (e: Exception) {
                    Log.e("SettingsView", "Sign in task exception", e)
                }
            }
            if (!handledError) {
                Toast.makeText(context, context.getString(R.string.settings_gdrive_link_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(googleCloudSyncState) {
        if (googleCloudSyncState is CloudSyncState.SessionExpired) {
            Toast.makeText(context, "يرجى إعادة ربط حساب السحاب لتجديد الصلاحية الأمنية 🔐", Toast.LENGTH_LONG).show()
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInClient.revokeAccess().addOnCompleteListener {
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
            }
        } else if (googleCloudSyncState is CloudSyncState.Success) {
            Toast.makeText(context, context.getString(R.string.settings_gdrive_sync_success), Toast.LENGTH_SHORT).show()
        }
    }

    // Save changes helper (retains structural properties)
    fun saveAllSettings() {
        val updated = settings.copy(
            currencySymbol = currencySymbol,
            schoolExpensesEnabled = schoolExpenses,
            isAutoBackupEnabled = isAutoBackupEnabled
        )
        viewModel.saveSettings(updated)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Settings Header Card (Visual Title Card)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 1. General Preferences Card (تخصيص الرموز والعملة)
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.settings_currency_title),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF075E54),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = currencySymbol,
                        onValueChange = {
                            currencySymbol = it
                            saveAllSettings()
                        },
                        label = { Text(stringResource(R.string.settings_currency_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                }
            }
        }

        // Card for custom Signature Fingerprint (بصمة وتوقيع التطبيق المعتمدة)
        item {
            val contextForSig = LocalContext.current
            val clipboardSigManager = androidx.compose.ui.platform.LocalClipboardManager.current
            val sha1Fingerprint = remember {
                try {
                    val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        contextForSig.packageManager.getPackageInfo(
                            contextForSig.packageName,
                            android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_SIGNATURES.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        contextForSig.packageManager.getPackageInfo(
                            contextForSig.packageName,
                            android.content.pm.PackageManager.GET_SIGNATURES
                        )
                    }
                    val signatures = @Suppress("DEPRECATION") packageInfo.signatures
                    if (signatures != null && signatures.isNotEmpty()) {
                        val md = java.security.MessageDigest.getInstance("SHA-1")
                        val publicKey = md.digest(signatures[0].toByteArray())
                        publicKey.joinToString(":") { String.format("%02X", it) }
                    } else {
                        "غير متوفر"
                    }
                } catch (e: Exception) {
                    "غير متوفر"
                }
            }
            val sha256Fingerprint = remember {
                try {
                    val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        contextForSig.packageManager.getPackageInfo(
                            contextForSig.packageName,
                            android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_SIGNATURES.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        contextForSig.packageManager.getPackageInfo(
                            contextForSig.packageName,
                            android.content.pm.PackageManager.GET_SIGNATURES
                        )
                    }
                    val signatures = @Suppress("DEPRECATION") packageInfo.signatures
                    if (signatures != null && signatures.isNotEmpty()) {
                        val md = java.security.MessageDigest.getInstance("SHA-256")
                        val publicKey = md.digest(signatures[0].toByteArray())
                        publicKey.joinToString(":") { String.format("%02X", it) }
                    } else {
                        "غير متوفر"
                    }
                } catch (e: Exception) {
                    "غير متوفر"
                }
            }

            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F766E).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.settings_signature_title),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F766E),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.settings_signature_desc),
                        fontSize = 11.sp,
                        color = Color(0xFF4B5563),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Divider(color = Color.LightGray.copy(alpha = 0.4f))

                    Spacer(modifier = Modifier.height(8.dp))

                    // SHA-1 field
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    clipboardSigManager.setText(AnnotatedString(sha1Fingerprint))
                                    Toast.makeText(contextForSig, contextForSig.getString(R.string.settings_sha1_copied), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ بصمة SHA-1",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Text(
                                text = stringResource(R.string.settings_sha1_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = sha1Fingerprint,
                                fontSize = 10.sp,
                                color = Color(0xFF1F2937),
                                textAlign = TextAlign.Left,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // SHA-256 field
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    clipboardSigManager.setText(AnnotatedString(sha256Fingerprint))
                                    Toast.makeText(contextForSig, contextForSig.getString(R.string.settings_sha256_copied), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ بصمة SHA-256",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Text(
                                text = stringResource(R.string.settings_sha256_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = sha256Fingerprint,
                                fontSize = 10.sp,
                                color = Color(0xFF1F2937),
                                textAlign = TextAlign.Left,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // 2. Security & Protection Card (بطاقة الأمن والحماية) - New Luxurious Feature
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = settings.isPasscodeEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    // Open setup Sheet as normal
                                    tempPasscode = ""
                                    tempConfirmPasscode = ""
                                    tempRecoveryPhrase = ""
                                    tempRecoveryHint = ""
                                    tempCheckAcknowledged = false
                                    showPasscodeSetupSheet = true
                                } else {
                                    if (settings.isPasscodeEnabled) {
                                        // Identity Verification Gate!
                                        securityGateInput = ""
                                        securityGateError = ""
                                        securityGateAction = "TOGGLE_OFF"
                                        showSecurityGateDialog = true
                                    } else {
                                        // Turn off screen lock and clear security tokens
                                        val updated = settings.copy(
                                            isPasscodeEnabled = false,
                                            passcodeHash = null,
                                            recoveryPhraseHash = null
                                        )
                                        viewModel.saveSettings(updated)
                                        Toast.makeText(context, context.getString(R.string.settings_app_lock_disabled), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF075E54)
                            )
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_app_lock_label),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF075E54)
                            )
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "الأمن والحماية",
                                tint = Color(0xFF075E54),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = settings.isPasscodeEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            
                            Spacer(modifier = Modifier.height(2.dp))

                            // Change Passcode
                            OutlinedButton(
                                onClick = {
                                    if (settings.isPasscodeEnabled) {
                                        securityGateInput = ""
                                        securityGateError = ""
                                        securityGateAction = "CHANGE_PASSCODE"
                                        showSecurityGateDialog = true
                                    } else {
                                        tempPasscode = ""
                                        tempConfirmPasscode = ""
                                        tempRecoveryPhrase = ""
                                        tempRecoveryHint = ""
                                        tempCheckAcknowledged = false
                                        showPasscodeSetupSheet = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.settings_change_passcode), fontSize = 12.sp, color = Color(0xFF075E54))
                                }
                            }

                            // Modify Recovery Action
                            OutlinedButton(
                                onClick = {
                                    if (settings.isPasscodeEnabled) {
                                        securityGateInput = ""
                                        securityGateError = ""
                                        securityGateAction = "CHANGE_RECOVERY"
                                        showSecurityGateDialog = true
                                    } else {
                                        tempPasscode = ""
                                        tempConfirmPasscode = ""
                                        tempRecoveryPhrase = ""
                                        tempRecoveryHint = ""
                                        tempCheckAcknowledged = false
                                        showPasscodeSetupSheet = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.settings_update_recovery), fontSize = 12.sp, color = Color(0xFF075E54))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Backup & Cloud Synchronization Card (مركز النسخ السحابي والمحلي الموحد - Quad-Backup)
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.settings_quad_backup_title),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF075E54),
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_quad_backup_subtitle),
                        fontSize = 11.sp,
                        color = Color(0xFF5A625E),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. الخيار الأول: تصدير واستيراد ملفات (.mzd)
                        QuadBackupItem(
                            title = stringResource(R.string.settings_backup_portable_title),
                            description = stringResource(R.string.settings_backup_portable_desc),
                            accentColor = EmeraldPrimary,
                            icon = { Icon(Icons.Default.Save, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp)) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val runBackup = {
                                            viewModel.createLocalBackup(context) { file ->
                                                if (file != null) {
                                                    com.example.ui.helper.shareBackupFile(context, file)
                                                }
                                            }
                                        }
                                        if (checkBackupPermissionsGranted()) {
                                            runBackup()
                                        } else {
                                            onPermissionGrantedCallback = runBackup
                                            showBackupPermissionExplanationDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text(stringResource(R.string.settings_export_mzd), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        safRestoreLauncher.launch(arrayOf("application/*"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text(stringResource(R.string.settings_import_mzd), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 2. الخيار الثاني: الاستنساخ النصي المشفر
                        QuadBackupItem(
                            title = stringResource(R.string.settings_backup_base64_title),
                            description = stringResource(R.string.settings_backup_base64_desc),
                            accentColor = Color(0xFF6366F1),
                            icon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp)) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.getBackupJsonForClipboard { jsonStr ->
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(jsonStr))
                                            Toast.makeText(context, context.getString(R.string.settings_toast_base64_copied), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text(stringResource(R.string.settings_copy_encrypted), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        pastedBackupText = ""
                                        showPasteDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text(stringResource(R.string.settings_restore_paste), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 3. الخيار الثالث: التمرير السحابي اليدوي
                        QuadBackupItem(
                            title = stringResource(R.string.settings_backup_gdrive_title),
                            description = stringResource(R.string.settings_backup_gdrive_desc),
                            accentColor = Color(0xFF0EA5E9),
                            icon = { Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(18.dp)) }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                Button(
                                    onClick = {
                                        val sdfName = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US)
                                        val dateStr = sdfName.format(java.util.Date())
                                        safExportLauncher.launch("Mzd_$dateStr.mzd")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Text(stringResource(R.string.settings_backup_gdrive_btn), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.settings_backup_gdrive_note),
                                    fontSize = 9.sp,
                                    color = Color.LightGray.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Right
                                )
                            }
                        }

                        // 4. الخيار الرابع: المزامنة السحابية المباشرة (REST Client with WebView Auth)
                        QuadBackupItem(
                            title = stringResource(R.string.settings_backup_cloud_title),
                            description = stringResource(R.string.settings_backup_cloud_desc),
                            accentColor = Color(0xFF10B981),
                            icon = { Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp)) }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                when (val state = googleCloudSyncState) {
                                    is CloudSyncState.Idle, is CloudSyncState.Error, is CloudSyncState.SessionExpired -> {
                                        var showWebFallback by remember { mutableStateOf(false) }
                                        var pastedWebCode by remember { mutableStateOf("") }
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val playServicesAvailable = run {
                                                            try {
                                                                val gmsClass = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
                                                                 val instanceMethod = gmsClass.getMethod("getInstance")
                                                                val gmsInstance = instanceMethod.invoke(null)
                                                                val isAvailableMethod = gmsClass.getMethod("isGooglePlayServicesAvailable", Context::class.java)
                                                                val status = isAvailableMethod.invoke(gmsInstance, context) as Int
                                                                status == 0
                                                            } catch (e: Exception) {
                                                                false
                                                            }
                                                        }

                                                        if (!playServicesAvailable) {
                                                            Toast.makeText(context, context.getString(R.string.settings_toast_google_missing), Toast.LENGTH_SHORT).show()
                                                            return@Button
                                                        }

                                                        googleSignInClient.signOut().addOnCompleteListener {
                                                            googleSignInClient.revokeAccess().addOnCompleteListener {
                                                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, context.getString(R.string.settings_toast_no_network), Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                            ) {
                                                Text(stringResource(R.string.settings_btn_gdrive_quick), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }

                                            TextButton(
                                                onClick = { showWebFallback = !showWebFallback },
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            ) {
                                                Text(
                                                    text = if (showWebFallback) stringResource(R.string.settings_btn_gdrive_fallback_hide) else stringResource(R.string.settings_btn_gdrive_fallback_show),
                                                    color = Color(0xFF10B981),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            if (showWebFallback) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.settings_gdrive_fallback_steps),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF334155),
                                                            textAlign = TextAlign.Right,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                        
                                                        Button(
                                                            onClick = {
                                                                try {
                                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(viewModel.googleDriveSyncHelper.getAuthUrl()))
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, context.getString(R.string.settings_toast_browser_failed), Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                                        ) {
                                                            Text(stringResource(R.string.settings_btn_gdrive_open_browser), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Text(
                                                            text = stringResource(R.string.settings_gdrive_fallback_desc),
                                                            fontSize = 10.sp,
                                                            color = Color(0xFF64748B),
                                                            lineHeight = 14.sp,
                                                            textAlign = TextAlign.Right,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )

                                                        OutlinedTextField(
                                                            value = pastedWebCode,
                                                            onValueChange = { pastedWebCode = it },
                                                            placeholder = { Text(stringResource(R.string.settings_placeholder_oauth_code), fontSize = 11.sp) },
                                                            singleLine = true,
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = Color(0xFF10B981),
                                                                unfocusedBorderColor = Color(0xFFCBD5E1)
                                                            )
                                                        )

                                                        Button(
                                                            onClick = {
                                                                val rawCode = pastedWebCode.trim()
                                                                if (rawCode.isNotEmpty()) {
                                                                    // Extract actual verification code from potential URL wrapper
                                                                    val finalCode = if (rawCode.startsWith("http://") || rawCode.startsWith("https://") || rawCode.contains("code=")) {
                                                                        var extracted = ""
                                                                        try {
                                                                            val parsedUri = android.net.Uri.parse(rawCode)
                                                                            extracted = parsedUri.getQueryParameter("code") ?: ""
                                                                        } catch (e: Exception) {}
                                                                        if (extracted.isEmpty()) {
                                                                            val idx = rawCode.indexOf("code=")
                                                                            if (idx != -1) {
                                                                                val start = idx + 5
                                                                                val end = rawCode.indexOf("&", start).let { if (it == -1) rawCode.length else it }
                                                                                extracted = rawCode.substring(start, end)
                                                                            }
                                                                        }
                                                                        extracted.takeIf { it.isNotEmpty() } ?: rawCode
                                                                    } else {
                                                                        rawCode
                                                                    }

                                                                    viewModel.handleGoogleOAuthCode(finalCode, null, "http://localhost/oauth2callback") { success ->
                                                                        if (success) {
                                                                            Toast.makeText(context, context.getString(R.string.settings_toast_oauth_success), Toast.LENGTH_LONG).show()
                                                                        } else {
                                                                            Toast.makeText(context, context.getString(R.string.settings_toast_oauth_failed), Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            enabled = pastedWebCode.trim().isNotEmpty(),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.fillMaxWidth().height(38.dp)
                                                        ) {
                                                            Text(stringResource(R.string.settings_btn_oauth_confirm), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                        }

                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        TextButton(
                                                            onClick = { com.example.ui.helper.openGoogleDriveApp(context) },
                                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.settings_btn_gdrive_open_app),
                                                                color = Color(0xFF3B82F6),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (state is CloudSyncState.Error) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stringResource(R.string.settings_state_prefix) + state.message,
                                                    color = SoftRed,
                                                    fontSize = 10.sp,
                                                    textAlign = TextAlign.Right
                                                )
                                            }
                                        }
                                    }
                                    is CloudSyncState.Authenticating -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF10B981))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = stringResource(R.string.settings_toast_cloud_auth), fontSize = 11.sp, color = Color(0xFF075E54))
                                        }
                                    }
                                    is CloudSyncState.Syncing -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF10B981))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = stringResource(R.string.settings_toast_cloud_syncing), fontSize = 11.sp, color = Color(0xFF075E54))
                                        }
                                    }
                                    is CloudSyncState.Success, is CloudSyncState.Authenticated -> {
                                        val storedEmail = viewModel.googleDriveSyncHelper.getStoredEmail()
                                        val email = if (state is CloudSyncState.Authenticated) state.email else (storedEmail ?: "حساب Google متصل")
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = stringResource(R.string.settings_gdrive_linked_pattern, email),
                                                    color = Color(0xFF15803D),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Text(
                                                text = stringResource(R.string.settings_gdrive_linked_warning),
                                                fontSize = 10.sp,
                                                color = Color(0xFF991B1B),
                                                lineHeight = 14.sp,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                                    .padding(10.dp)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        viewModel.uploadBackupToGoogleDrive { success -> }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f).height(40.dp)
                                                ) {
                                                    Text(stringResource(R.string.settings_btn_upload_backup), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.restoreFromGoogleDriveDirect(context) { success ->
                                                            if (success) {
                                                                Toast.makeText(context, context.getString(R.string.settings_toast_gdrive_restore_success), Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, context.getString(R.string.settings_toast_gdrive_restore_failed), Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f).height(40.dp)
                                                ) {
                                                    Text(stringResource(R.string.settings_btn_download_backup), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    showCloudBackupsSheet = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("open_cloud_backups_archive_button")
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.BackupTable, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                    Text(stringResource(R.string.settings_btn_browse_archives), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    isSyncLoggingOut = true
                                                    viewModel.googleDriveLogout {
                                                        isSyncLoggingOut = false
                                                        Toast.makeText(context, context.getString(R.string.settings_toast_gdrive_logout_success), Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                enabled = !isSyncLoggingOut,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), disabledContainerColor = Color(0xFFF1F5F9)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(36.dp)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isSyncLoggingOut) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFEF4444), strokeWidth = 2.dp)
                                                        Text(stringResource(R.string.settings_gdrive_logging_out), fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                                    } else {
                                                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                        Text(stringResource(R.string.settings_btn_gdrive_logout), fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }





                    if (showCloudBackupsSheet) {
                        CloudBackupsBottomSheet(
                            viewModel = viewModel,
                            onDismiss = { showCloudBackupsSheet = false }
                        )
                    }

                    // Reuse the existing Paste Text Dialog if needed
                    if (showPasteDialog) {
                        AlertDialog(
                            onDismissRequest = { showPasteDialog = false },
                            title = {
                                Text(stringResource(R.string.settings_dialog_restore_paste_title), fontWeight = FontWeight.Bold, color = Color(0xFF075E54))
                            },
                            text = {
                                Column {
                                    Text(stringResource(R.string.settings_dialog_restore_paste_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = pastedBackupText,
                                        onValueChange = { pastedBackupText = it },
                                        modifier = Modifier.fillMaxWidth().height(150.dp),
                                        placeholder = { Text(stringResource(R.string.settings_placeholder_paste_encoded)) }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (pastedBackupText.isNotBlank()) {
                                        viewModel.executeMasterRestore(pastedBackupText, context) { success, restoredSettings ->
                                            if (success && restoredSettings != null) {
                                                currencySymbol = restoredSettings.currencySymbol
                                                schoolExpenses = restoredSettings.schoolExpensesEnabled
                                                
                                                pastedBackupText = ""
                                                showPasteDialog = false
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.settings_toast_paste_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.settings_btn_restore_now), color = Color(0xFF075E54))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPasteDialog = false }) {
                                    Text(stringResource(R.string.settings_btn_cancel), color = Color.Gray)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.settings_discovered_backups_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF075E54)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (localBackups.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_no_local_backups),
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            localBackups.forEach { file ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.restoreFromLocalFile(file, context) { success, restoredSettings ->
                                                if (success && restoredSettings != null) {
                                                    currencySymbol = restoredSettings.currencySymbol
                                                    schoolExpenses = restoredSettings.schoolExpensesEnabled
                                                }
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.settings_desc_restore), tint = Color(0xFF075E54))
                                        Text(
                                            text = file.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Right,
                                            color = Color(0xFF075E54)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3.5 Auto Backup Background Card (النسخ الاحتياطي التلقائي)
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isAutoBackupEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val enableAutoBackup = {
                                        isAutoBackupEnabled = true
                                        saveAllSettings()
                                        com.example.AutoBackupWorker.scheduleDailyBackupWorker(context)
                                        Toast.makeText(context, context.getString(R.string.settings_toast_auto_backup_enabled), Toast.LENGTH_SHORT).show()
                                    }
                                    if (checkBackupPermissionsGranted()) {
                                        enableAutoBackup()
                                    } else {
                                        onPermissionGrantedCallback = enableAutoBackup
                                        showBackupPermissionExplanationDialog = true
                                    }
                                } else {
                                    isAutoBackupEnabled = false
                                    saveAllSettings()
                                    com.example.AutoBackupWorker.cancelDailyBackupWorker(context)
                                    Toast.makeText(context, context.getString(R.string.settings_toast_auto_backup_disabled), Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF075E54)
                            )
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_auto_backup_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF075E54)
                            )
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = stringResource(R.string.settings_desc_auto_backup),
                                tint = Color(0xFF075E54),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_auto_backup_desc),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 4. Danger Zone Card (منطقة الخطر - Outlined red button)
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = SoftRed.copy(alpha = 0.03f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DangerDeleteButton {
                        showTrapDialog = true
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.settings_danger_desc),
                        fontSize = 11.sp,
                        color = SoftRed.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 5. Developer Info Footer Card (تذييل المطور البسيط والأنيق) - Compact Centered Minimalist
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_app_version),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_developer_info),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF788282),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Subtle centered Circular social rows
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Call Button
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:774004399"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = stringResource(R.string.settings_desc_call_support),
                            tint = Color(0xFF788282),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // WhatsApp Direct Chat Button
                    IconButton(
                        onClick = {
                            val waUrl = "https://wa.me/967774004399"
                            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                            context.startActivity(waIntent)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = stringResource(R.string.settings_desc_whatsapp),
                            tint = Color(0xFF788282),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal Passcode Lock & Recovery Setup Sheet
    if (showPasscodeSetupSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPasscodeSetupSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "تعيين قفل التطبيق والأمان 🛡️",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "قم بإنشاء رمز حماية لخصوصية السجلات والتحويلات المعتمدة",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Passcode Outlined Text Field
                        OutlinedTextField(
                            value = tempPasscode,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    tempPasscode = it
                                }
                            },
                            label = { Text("رمز القفل المكون من 4 أرقام") },
                            placeholder = { Text("مثال: 1234") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                        )

                        // Confirm Passcode Outlined Text Field
                        OutlinedTextField(
                            value = tempConfirmPasscode,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    tempConfirmPasscode = it
                                }
                            },
                            label = { Text("تأكيد الرمز السري") },
                            placeholder = { Text("أعد كتابة الرمز نفسه للتحقق") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                        )

                        // Warnings Box (صندوق تحذيري جذاب بلون متباين دافئ)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "تنبيه أمني بالغ الأهمية ⚠️:\n\nالتطبيق يحمي خصوصيتك محلياً بالكامل. في حال نسيت رمز القفل وعبارة الاسترداد، لن يتمكن أحد -بما في ذلك المطور- من فك تشفير بياناتك أو استعادتها نهائياً، وستفقد بياناتك للأبد.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp
                            )
                        }

                        // Recovery Phrase Outlined Text Field
                        OutlinedTextField(
                            value = tempRecoveryPhrase,
                            onValueChange = { tempRecoveryPhrase = it },
                            label = { Text("مفتاح أمان الاسترداد") },
                            placeholder = { Text("اكتب مفتاح أمان الاسترداد (مثال: فاطمة أو أحمد)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                        )

                        Text(
                            text = "قم بحفظ هذه العبارة بدقة لأنها مفتاح أمانك الوحيد.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Recovery Hint Outlined Text Field
                        OutlinedTextField(
                            value = tempRecoveryHint,
                            onValueChange = { tempRecoveryHint = it },
                            label = { Text("تلميح الذاكرة") },
                            placeholder = { Text("مثال: اسم الزوجة أو صديق الطفولة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                        )

                        // Acknowledge Checkbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempCheckAcknowledged = !tempCheckAcknowledged }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "أؤكد حفظي الآمن لمفتاح الاسترداد والرمز السري خارج الهاتف وأتحمل المسؤولية كاملة.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Right,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Checkbox(
                                checked = tempCheckAcknowledged,
                                onCheckedChange = { tempCheckAcknowledged = it },
                                colors = CheckboxDefaults.colors(checkedColor = CoralAccent)
                            )
                        }
                    }
                }

                item {
                    val isValid = tempPasscode.length == 4 && 
                                  tempConfirmPasscode == tempPasscode && 
                                  tempRecoveryPhrase.isNotBlank() && 
                                  tempCheckAcknowledged

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel Button
                        OutlinedButton(
                            onClick = { showPasscodeSetupSheet = false },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("إلغاء", fontWeight = FontWeight.Bold)
                        }

                        // Save & Enable Passcode
                        Button(
                            onClick = {
                                if (isValid) {
                                    val pHash = com.example.domain.HashUtils.hashString(tempPasscode)
                                    val rHash = com.example.domain.HashUtils.hashString(tempRecoveryPhrase.trim())
                                    val updated = settings.copy(
                                        isPasscodeEnabled = true,
                                        passcodeHash = pHash,
                                        recoveryPhraseHash = rHash,
                                        recoveryHint = tempRecoveryHint.trim().takeIf { it.isNotBlank() }
                                    )
                                    viewModel.saveSettings(updated)
                                    showPasscodeSetupSheet = false
                                    Toast.makeText(context, "تم تفعيل قفل التطبيق والأمان بنجاح 🛡️", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            enabled = isValid
                        ) {
                            Text("حفظ وتغليق القفل 💾", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showBackupPermissionExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showBackupPermissionExplanationDialog = false },
            title = {
                Text(
                    "طلب صلاحيات النسخ الاحتياطي ⚙️💾",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "لتفعيل نظام التخزين والمزامنة التلقائي وحفظ ملفات (.mzd) في ذاكرة الهاتف العامة (Documents/Mizan_Backups)، يتطلب التطبيق الحصول صراحة على الصلاحيات التالية لتفادي فشل الحفظ أو الحظر:",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("صلاحية الوصول ومساحة التخزين لإنشاء الأرشيف", fontSize = 11.sp, color = Color(0xFF334155), textAlign = TextAlign.Right)
                        Icon(Icons.Default.Folder, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إدارة ملفات الجهاز (لتخطي حماية الأندرويد 11+)", fontSize = 11.sp, color = Color(0xFF334155), textAlign = TextAlign.Right)
                            Icon(Icons.Default.SettingsSuggest, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("صلاحية الإشعارات لتلقي تنبيهات نجاح/فشل المزامنة", fontSize = 11.sp, color = Color(0xFF334155), textAlign = TextAlign.Right)
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "ملاحظة: في حال لم تمنح هذه الصلاحيات، سيظل التطبيق قادراً على حفظ البيانات مؤقتاً داخل التخزين الداخلي الآمن للبرنامج.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackupPermissionExplanationDialog = false
                        val permissions = mutableListOf<String>()
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        multiplePermissionsLauncher.launch(permissions.toTypedArray())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("منح الصلاحيات 🔓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackupPermissionExplanationDialog = false
                        onPermissionGrantedCallback?.invoke()
                    }
                ) {
                    Text("استخدام التخزين المؤقت", fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
        )
    }

    // Visual Deception Reset Double Confirmation Dialog Box
    if (showTrapDialog) {
        Dialog(
            onDismissRequest = { showTrapDialog = false }
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "إجراء بالغ الخطورة! ⚠️",
                        color = SoftRed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "هل أنت متأكد تماماً من مسح كافة السجلات؟ هذا الإجراء سيمحي كل شيء نهائياً من الصندوق واليوميات والالتزامات ولا يمكن تدارك أو استرجاع المعلومات مجدداً.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Soft Primary Safety Button
                    Button(
                        onClick = { showTrapDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "تراجع، حافظ على بياناتي 🛡️",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tiny Unshaded Reset Button
                    TextButton(
                        onClick = {
                            viewModel.deleteAllData()
                            showTrapDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "نعم، امسح كل سجلاتي نهائياً",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // Identity Verification Gate Dialog Box
    if (showSecurityGateDialog) {
        Dialog(
            onDismissRequest = { showSecurityGateDialog = false }
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "التحقق الأمني",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "بوابة التحقق الأمني 🛡️",
                        color = EmeraldPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "لتأكيد هويتك وحماية خصوصية التطبيق، الرجاء إدخال الرمز السري الحالي المكون من 4 أرقام أو عبارة الاسترداد الخاصة بك للمتابعة.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = securityGateInput,
                        onValueChange = { 
                            securityGateInput = it
                            securityGateError = ""
                        },
                        label = { Text("رمز القفل أو عبارة الاسترداد") },
                        placeholder = { Text("أدخل بيانات التحقق...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                    )

                    if (securityGateError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = securityGateError,
                            color = SoftRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel button
                        OutlinedButton(
                            onClick = { showSecurityGateDialog = false },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("إلغاء الإجراء", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Confirm button
                        Button(
                            onClick = {
                                if (viewModel.verifyCredentials(securityGateInput)) {
                                    showSecurityGateDialog = false
                                    when (securityGateAction) {
                                        "TOGGLE_OFF" -> {
                                            val updated = settings.copy(
                                                isPasscodeEnabled = false,
                                                passcodeHash = null,
                                                recoveryPhraseHash = null
                                            )
                                            viewModel.saveSettings(updated)
                                            Toast.makeText(context, "تم إيقاف ميزة قفل التطبيق والأمن 🔓", Toast.LENGTH_SHORT).show()
                                        }
                                        "CHANGE_PASSCODE", "CHANGE_RECOVERY" -> {
                                            tempPasscode = ""
                                            tempConfirmPasscode = ""
                                            tempRecoveryPhrase = ""
                                            tempRecoveryHint = ""
                                            tempCheckAcknowledged = false
                                            showPasscodeSetupSheet = true
                                        }
                                    }
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    securityGateError = "الرمز أو العبارة غير متطابقة مسبقاً! ❌"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            enabled = securityGateInput.isNotBlank()
                        ) {
                            Text("تحقق ومتابعة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }


}

// Redesigned continuous progress-driven long-press delete button: Styled beautifully as a soft Red Outlined Box
@Composable
fun DangerDeleteButton(onDeleteConfirmed: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var isPressing by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (isPressing) 1f else 0f,
        animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        label = "DeleteProgress"
    )

    LaunchedEffect(isPressing) {
        if (isPressing) {
            while (isPressing) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(120)
            }
        }
    }

    LaunchedEffect(progress) {
        if (progress == 1f) {
            isPressing = false
            onDeleteConfirmed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SoftRed, RoundedCornerShape(12.dp))
            .background(SoftRed.copy(alpha = 0.04f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressing = true
                        tryAwaitRelease()
                        isPressing = false
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Internal slide filler on hold
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .align(Alignment.CenterStart)
                .background(SoftRed.copy(alpha = 0.16f))
        )

        Text(
            text = if (isPressing) "جاري التحقق من الحذف..." else "حذف جميع بيانات السجل (ضغط مطول ⏳)",
            color = SoftRed,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun QuadBackupItem(
    title: String,
    description: String,
    accentColor: Color,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F7F6))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF075E54)
                    )
                }
            }
            Text(
                text = description,
                fontSize = 10.sp,
                color = Color(0xFF5A625E),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 14.sp
            )
            content()
        }
    }
}
