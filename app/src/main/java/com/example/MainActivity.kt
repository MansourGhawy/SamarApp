package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppSettings
import com.example.ui.screens.*
import com.example.ui.theme.MizanTheme
import com.example.ui.viewmodel.FinanceViewModel
import java.io.File

import com.example.ui.screens.TheMasterSplashScreen
import android.content.pm.PackageManager
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logAppSignatureSHA1(this)
        enableEdgeToEdge()

        // Schedule daily automatic backup at end of day
        AutoBackupReceiver.scheduleDailyBackupAlarm(this)

        setContent {
            val viewModel: FinanceViewModel = viewModel()
            val settings by viewModel.settingsState.collectAsState()
            
            var showSplash by remember { mutableStateOf(true) }
            var isUnlocked by remember { mutableStateOf(false) }

            var permissionRequested by remember { mutableStateOf(false) }
            var showOnboardingDialog by remember { mutableStateOf(true) }

            // Smooth crossfade dynamic transitions (400ms duration)
            MizanTheme {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    if (showSplash) {
                        TheMasterSplashScreen(onSplashFinished = { showSplash = false })
                    } else {
                        if (settings.isFirstLaunch && showOnboardingDialog) {
                            WelcomeOnboardingDialog(
                                onDismiss = {
                                    val updated = settings.copy(isFirstLaunch = false)
                                    viewModel.saveSettings(updated)
                                    showOnboardingDialog = false
                                }
                            )
                        }

                        if (settings.isPasscodeEnabled && !isUnlocked) {
                            AppLockScreen(
                                settings = settings,
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
                            MainAppLayout(viewModel = viewModel, settings = settings, onExit = { finish() })
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

enum class Screen {
    LEDGER, REPORTS, SETTINGS, TRASH
}

@Composable
fun MainAppLayout(
    viewModel: FinanceViewModel,
    settings: AppSettings,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.LEDGER) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    var showSecuritySheet by remember { mutableStateOf(false) }
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
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(jsonStr.toByteArray())
                            scope.launch {
                                android.widget.Toast.makeText(context, "تم تصدير النسخة بنجاح في المستندات 📁", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        scope.launch {
                            android.widget.Toast.makeText(context, "فشل في الحفظ", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    val safRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (jsonText.isNotBlank()) {
                    viewModel.executeMasterRestore(jsonText, context) { success, _ ->
                        if (success) {
                            android.widget.Toast.makeText(context, "تمت استعادة السجل بنجاح 🎉", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "تعذر استيراد البيانات. يرجى التأكد من اختيار ملف (.mzd) صحيح.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Observe DB state
    val totalCash by viewModel.totalCashState.collectAsState()
    val commitments by viewModel.commitmentsState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val monthlyLedger by viewModel.monthlyLedgerState.collectAsState()

    // Back handler: prevents frozen apps, provides clean intercepts flow
    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentScreen != Screen.LEDGER) {
            currentScreen = Screen.LEDGER
        } else {
            // If on Ledger & no selection active, handle exit double-check
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
                    .width(310.dp)
                    .fillMaxHeight(),
                windowInsets = WindowInsets.systemBars
            ) {
                // Header of Drawer (كرت علوي باللون الزيتي الفاخر المعتمد)
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.EmeraldPrimary),
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
                                    color = Color(0xFFB2DFDB),
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
                            text = "ميزان الدار",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "ميزان الدار.. راحةٌ واستقرار",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB2DFDB),
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
                        selected = false,
                        icon = Icons.Default.Lock,
                        label = "🛡️ الأمان وقفل التطبيق",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showSecuritySheet = true
                        }
                    )
                    
                    DrawerItem(
                        selected = currentScreen == Screen.REPORTS,
                        icon = Icons.Default.List,
                        label = "📊 التقارير",
                        onClick = {
                            currentScreen = Screen.REPORTS
                            scope.launch { drawerState.close() }
                        }
                    )
                    
                    DrawerItem(
                        selected = false,
                        icon = Icons.Default.Refresh,
                        label = "☁️ النسخ الاحتياطي والاسترداد",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showBackupRestoreSheet = true
                        }
                    )

                    DrawerItem(
                        selected = currentScreen == Screen.TRASH,
                        icon = Icons.Default.Delete,
                        label = "🗑️ سلة المحذوفات",
                        onClick = {
                            currentScreen = Screen.TRASH
                            scope.launch { drawerState.close() }
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
                        text = "الإصدار v1.2",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "تصميم وتطوير م/ منصور قطينه للبرمجيات",
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
                                    val msg = "مرحباً م/ منصور قطينه، أود التواصل بخصوص تطبيق ميزان الدار"
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
                // Seamless toggles using smooth crossfade animations (400ms)
                Crossfade(
                    targetState = currentScreen,
                    animationSpec = tween(durationMillis = 400),
                    label = "ScreenSwitch"
                ) { screen ->
                    when (screen) {
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
                                transactions = transactions,
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
                                onBack = { currentScreen = Screen.LEDGER }
                            )
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
                        text = "إغلاق التطبيق",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.EmeraldPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "هل تود إغلاق التطبيق ومغادرته الآن؟",
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
                            text = "عدم إظهار هذه الرسالة مجدداً",
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
                                text = "إلغاء",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // YES Exit - Filled Brand Emerald color button
                        Button(
                            onClick = {
                                if (dontShowAgain) {
                                    // Disable exit confirmation in settings
                                    viewModel.saveSettings(settings.copy(doubleCheckExit = false))
                                }
                                showExitConfirmDialog = false
                                onExit()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.EmeraldPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "إغلاق التطبيق",
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

    if (showSecuritySheet) {
        SecurityBottomSheet(
            settings = settings,
            viewModel = viewModel,
            onDismiss = { showSecuritySheet = false }
        )
    }

    if (showBackupRestoreSheet) {
        BackupRestoreBottomSheet(
            settings = settings,
            viewModel = viewModel,
            onExportMzd = {
                safExportLauncher.launch("Mizan_Backup_${System.currentTimeMillis() / 1000}.mzd")
            },
            onImportMzd = {
                safRestoreLauncher.launch(arrayOf("application/*"))
            },
            onImportBase64 = { base64JsonText ->
                viewModel.executeMasterRestore(base64JsonText, context) { success, _ ->
                    if (success) {
                        android.widget.Toast.makeText(context, "تمت المزامنة واستعادة السجل بنجاح 🎉", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "فشل فك الرمز المشفر أو الاستيراد", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showBackupRestoreSheet = false }
        )
    }
}

@Composable
fun DrawerItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val containerColor = if (selected) com.example.ui.theme.EmeraldPrimary.copy(alpha = 0.12f) else Color.Transparent
    val contentColor = if (selected) com.example.ui.theme.EmeraldPrimary else Color.DarkGray
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = fontWeight,
                color = contentColor
            )
        }
    }
}

@Composable
fun ContactIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun WelcomeOnboardingDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismissing by clicking outside to force onboarding action */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        tonalElevation = 6.dp,
        confirmButton = {},
        dismissButton = {},
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // الشعار المضيء - Intersecting glowing circles logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(com.example.ui.theme.EmeraldPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
                        val radius = size.minDimension / 3.5f
                        drawCircle(
                            color = com.example.ui.theme.EmeraldPrimary.copy(alpha = 0.15f),
                            radius = radius * 1.3f,
                            center = center.copy(x = center.x - 12f)
                        )
                        drawCircle(
                            color = com.example.ui.theme.EmeraldPrimary.copy(alpha = 0.25f),
                            radius = radius * 1.3f,
                            center = center.copy(x = center.x + 12f)
                        )
                        drawCircle(
                            color = com.example.ui.theme.EmeraldPrimary,
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = com.example.ui.theme.EmeraldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "مقدمة تعريفية 🏠",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.EmeraldPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "ميزان الدار.. راحةٌ واستقرار",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = com.example.ui.theme.EmeraldPrimary.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Features list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OnboardingFeatureItem(
                        iconEmoji = "🏠",
                        title = "1. ميزان الدار:",
                        description = "للتحكم الفوري بميزانيتك ومنصرفاتك والالتزامات اليومية لبيت مبروك."
                    )
                    OnboardingFeatureItem(
                        iconEmoji = "📊",
                        title = "2. حسابات حبايب:",
                        description = "لإدارة ديونك ومبالغك الدائنة والمدينة للزبائن والأصدقاء بكل ثقة وسهولة."
                    )
                    OnboardingFeatureItem(
                        iconEmoji = "📦",
                        title = "3. المخزن الذكي:",
                        description = "لجرد بضائعك وتدقيق الوارد والصادر بدقة الوزن أو العدد."
                    )
                    OnboardingFeatureItem(
                        iconEmoji = "🛡️",
                        title = "4. التأمين والنسخ الاحتياطي السحابي:",
                        description = "حماية برمز سري ومزامنة تلقائية صامتة حقيقية لحفظ أصولك وبياناتك."
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.EmeraldPrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = "أهلاً بك، ابدأ الاستخدام 🏠",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    )
}

@Composable
fun OnboardingFeatureItem(
    iconEmoji: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.End
    ) {
        // Text portion
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                textAlign = TextAlign.Right
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 15.sp,
                textAlign = TextAlign.Right
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Icon circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.Gray.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconEmoji, fontSize = 16.sp)
        }
    }
}

