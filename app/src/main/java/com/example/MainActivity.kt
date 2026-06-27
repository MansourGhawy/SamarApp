package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppSettings
import com.example.ui.screens.*
import com.example.ui.theme.MizanTheme
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.navigation.Screen
import com.example.ui.components.WelcomeOnboardingDialog
import com.example.ui.components.DrawerItem
import com.example.ui.components.ContactIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import android.content.pm.PackageManager
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logAppSignatureSHA1(this)
        enableEdgeToEdge()

        // Schedule daily automatic backup at end of day
        AutoBackupWorker.scheduleDailyBackupWorker(this)

        setContent {
            val syncViewModel: com.example.ui.viewmodel.SyncSettingsViewModel = viewModel()
            val viewModel: FinanceViewModel = viewModel()
            viewModel.setSyncSettingsViewModel(syncViewModel)

            val context = LocalContext.current
            LaunchedEffect(viewModel) {
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MainAppLayout(
    viewModel: FinanceViewModel,
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
    val tabOrderStr by viewModel.tabOrderState.collectAsStateWithLifecycle()

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
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.getBackupJsonForClipboard { jsonStr ->
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
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonText = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    if (jsonText.isNotBlank()) {
                        viewModel.executeMasterRestore(jsonText, context) { success, _ ->
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

    // Observe DB state
    val totalCash by viewModel.totalCashState.collectAsStateWithLifecycle()
    val commitments by viewModel.commitmentsState.collectAsStateWithLifecycle()
    val transactions by viewModel.transactionsState.collectAsStateWithLifecycle()
    val monthlyLedger by viewModel.monthlyLedgerState.collectAsStateWithLifecycle()

    // Back handler: prevents frozen apps, provides clean intercepts flow
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
            // If on default start screen & no selection active, handle exit double-check
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
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 310.dp)
                    .fillMaxHeight(),
                windowInsets = WindowInsets.systemBars
            ) {
                // Header of Drawer (كرت علوي باللون الكحلي الملكي المعتمد)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3F51B5)),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // شعار الدوائر المتداخلة المضيء
                        Box(
                            modifier = Modifier.size(70.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(60.dp)) {
                                val radius = size.minDimension / 3.2f
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.15f),
                                    radius = radius * 1.3f,
                                    center = center.copy(x = center.x - 10f)
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.25f),
                                    radius = radius * 1.3f,
                                    center = center.copy(x = center.x + 10f)
                                )
                                drawCircle(
                                    color = Color(0xFF93C5FD),
                                    radius = radius,
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Text(
                            text = stringResource(id = R.string.app_name_main),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = stringResource(id = R.string.app_slogan),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF93C5FD),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Drawer Items Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerItem(
                        selected = currentScreen == Screen.BUSINESS_PROFILE,
                        icon = Icons.Default.People,
                        label = stringResource(id = R.string.drawer_business_profile_label),
                        onClick = {
                            currentScreen = Screen.BUSINESS_PROFILE
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    DrawerItem(
                        selected = currentScreen == Screen.SECURITY,
                        icon = Icons.Default.Lock,
                        label = stringResource(id = R.string.drawer_security_label),
                        onClick = {
                            currentScreen = Screen.SECURITY
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    DrawerItem(
                        selected = currentScreen == Screen.REPORTS,
                        icon = Icons.Default.List,
                        label = stringResource(id = R.string.drawer_reports_label),
                        onClick = {
                            currentScreen = Screen.REPORTS
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    DrawerItem(
                        selected = currentScreen == Screen.TRASH,
                        icon = Icons.Default.Delete,
                        label = stringResource(id = R.string.drawer_trash_label),
                        onClick = {
                            currentScreen = Screen.TRASH
                            scope.launch { drawerState.close() }
                        }
                    )

                    DrawerItem(
                        selected = false,
                        icon = Icons.Default.Refresh,
                        label = stringResource(id = R.string.drawer_backup_label1),
                        onClick = {
                            scope.launch { drawerState.close() }
                            showBackupRestoreSheet = true
                        }
                    )
                }
                
                // Footer / Developer info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Divider(color = Color.LightGray.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = stringResource(id = R.string.drawer_app_version, versionName),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = stringResource(id = R.string.developer_credit),
                        fontSize = 10.sp,
                        color = Color.Gray.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Row of simple round contact/whatsapp options
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactIcon(
                            icon = Icons.Default.Call,
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:+967774004399"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                        
                        ContactIcon(
                            icon = Icons.Default.Share,
                            onClick = {
                                try {
                                    val msg = context.getString(R.string.whatsapp_contact_msg)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse("https://api.whatsapp.com/send?phone=967774004399&text=${android.net.Uri.encode(msg)}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) { innerPadding ->
            // Content screen with status/drawing safe boundaries
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Seamless toggles using smooth crossfade animations (200ms)
                Crossfade(
                    targetState = currentScreen,
                    animationSpec = tween(durationMillis = 200),
                    label = "ScreenSwitch"
                ) { screen ->
                    when (screen) {
                        Screen.HABAYEB -> {
                            HabayebScreen(
                                viewModel = viewModel,
                                onClose = {
                                    if (settings.doubleCheckExit) {
                                        showExitConfirmDialog = true
                                    } else {
                                        onExit()
                                    }
                                }
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
                                onMenuClick = {
                                    scope.launch { drawerState.open() }
                                }
                            )
                        }

                        Screen.REPORTS -> {
                            ReportsView(
                                viewModel = viewModel,
                                settings = settings,
                                currencySymbol = settings.currencySymbol
                            )
                        }
                        Screen.SETTINGS -> {
                            SettingsView(
                                viewModel = viewModel,
                                settings = settings
                            )
                        }
                        Screen.TRASH -> {
                            com.example.ui.screens.TrashScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = Screen.HABAYEB }
                            )
                        }
                        Screen.BUSINESS_PROFILE -> {
                            BusinessProfileScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = Screen.HABAYEB }
                            )
                        }
                        Screen.SECURITY -> {
                            com.example.ui.screens.SecurityScreen(
                                settings = settings,
                                viewModel = viewModel,
                                onBack = { currentScreen = Screen.LEDGER }
                            )
                        }
                    }
                }

                // --- FLOATING BOTTOM NAVIGATION DOCK ---
                val items = remember(context) {
                    listOf(
                        Triple(Screen.HABAYEB, Icons.Default.People, context.getString(R.string.nav_habayeb_plain)),
                        Triple(Screen.LEDGER, Icons.Default.AccountBalanceWallet, context.getString(R.string.nav_ledger_plain))
                    )
                }

                // Only visible on primary tabs: HABAYEB, LEDGER
                if (currentScreen == Screen.HABAYEB || currentScreen == Screen.LEDGER) {
                    Card(
                        shape = RoundedCornerShape(24.dp), // elegant modern pill shape
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)), // extremely thin elegant slate border
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .fillMaxWidth(0.85f) // slender elegant width
                            .height(50.dp) // sleek 50dp height
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items.forEach { (screen, icon, label) ->
                                val isSelected = currentScreen == screen
                                val activeColor = Color(0xFF1E3A8A)
                                val inactiveColor = Color(0xFF94A3B8)
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) Color(0xFFEFF6FF) else Color.Transparent)
                                        .clickable {
                                            currentScreen = screen
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (isSelected) activeColor else inactiveColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        if (isSelected) {
                                            Text(
                                                text = label,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = activeColor,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Blurred Background dim confirmation Dialog for app EXIT
    if (showExitConfirmDialog) {
        var dontShowAgain by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showExitConfirmDialog = false },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clickable(enabled = false) { } // prevent event bubbles
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_exit_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3F51B5), // Brand Consistency
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(id = R.string.dialog_exit_message),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Dont show again" Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dontShowAgain = !dontShowAgain }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.dialog_exit_dont_show_again),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                            colors = CheckboxDefaults.colors(checkedColor = com.example.ui.theme.EmeraldPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Buttons horizontally aligned
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // NO Keep app - Beautiful clean TextButton to maintain high aesthetic value
                        TextButton(
                            onClick = { showExitConfirmDialog = false },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.common_cancel),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // YES Exit - Filled Brand Royal Navy color button
                        Button(
                            onClick = {
                                if (dontShowAgain) {
                                    // Disable exit confirmation in settings
                                    viewModel.saveSettings(settings.copy(doubleCheckExit = false))
                                }
                                showExitConfirmDialog = false
                                onExit()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.dialog_exit_title),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBackupRestoreSheet) {
        BackupRestoreBottomSheet(
            settings = settings,
            viewModel = viewModel,
            onExportMzd = {
                val dateStr = sdfName.format(java.util.Date())
                safExportLauncher.launch("Mizan_$dateStr.mzd")
            },
            onImportMzd = {
                safRestoreLauncher.launch(arrayOf("application/*"))
            },
            onImportBase64 = { base64JsonText ->
                viewModel.executeMasterRestore(base64JsonText, context) { success, _ ->
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
