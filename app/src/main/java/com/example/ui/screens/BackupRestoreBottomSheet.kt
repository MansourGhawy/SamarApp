package com.example.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CloudSyncState
import com.example.data.local.AppSettings
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftRed
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreBottomSheet(
    settings: AppSettings,
    viewModel: FinanceViewModel,
    onExportMzd: () -> Unit,
    onImportMzd: () -> Unit,
    onImportBase64: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val googleSyncState by viewModel.googleDriveSyncState.collectAsState()
    val storedEmail = remember(googleSyncState) { viewModel.googleDriveSyncHelper.getStoredEmail() }
    val isConnected = !storedEmail.isNullOrEmpty() || googleSyncState is CloudSyncState.Authenticated || googleSyncState is CloudSyncState.Success

    var showExportOptions by remember { mutableStateOf(false) }
    var showImportOptions by remember { mutableStateOf(false) }
    
    // Paste Base64 Dialog
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    
    // Reset confirmation Dialogs (Double confirmation modal)
    var showResetConfirm1 by remember { mutableStateOf(false) }
    var showResetConfirm2 by remember { mutableStateOf(false) }

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
                            Toast.makeText(context, "تم ربط الحساب $email بنجاح! ☁️🎉 يمكنك الآن مزامنة أو استعادة بياناتك يدوياً.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "تعذر الاتصال بخوادم Google، يرجى التحقق من جودة الشبكة وإعادة المحاولة.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "لم نتمكن من الحصول على كود ربط صالح من Google.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BackupRestoreBottomSheet", "Google sign in failed", e)
                Toast.makeText(context, "تعذر التوصيل بخدمات Google: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            var handledError = false
            if (intent != null) {
                try {
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(intent)
                    task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    val sc = e.statusCode
                    Log.e("BackupRestoreBottomSheet", "Sign in failed with code $sc", e)
                    Toast.makeText(context, "تعذر الاتصال: يرجى التحقق من تهيئة الحساب السحابي (كود $sc)", Toast.LENGTH_LONG).show()
                    handledError = true
                } catch (e: Exception) {
                    Log.e("BackupRestoreBottomSheet", "Sign in task exception", e)
                }
            }
            if (!handledError) {
                Toast.makeText(context, "تم إلغاء عملية الربط من قبل المستخدم.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "النسخ الاحتياطي والاسترداد",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                }
                
                // Silent state connection indicator (Smart network exception indicator)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isConnected) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF10B981) else Color(0xFF94A3B8))
                        )
                        Text(
                            text = if (isConnected) "متصل سحابياً ☁️" else "محلي دائماً 📁",
                            fontSize = 9.sp,
                            color = if (isConnected) Color(0xFF15803D) else Color(0xFF64748B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Cloud Sync Section (Google Drive Integration)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
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
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "السحابة والربط التلقائي ☁️",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }

                    when (val state = googleSyncState) {
                        is CloudSyncState.Idle, is CloudSyncState.Error -> {
                            var showWebFallback by remember { mutableStateOf(false) }
                            var pastedWebCode by remember { mutableStateOf("") }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state is CloudSyncState.Error) {
                                    Text(
                                        text = "⚠️ " + state.message,
                                        fontSize = 11.sp,
                                        color = SoftRed,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Button(
                                    onClick = {
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Text("ربط حساب Google Drive (سريع) ⚡", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = { showWebFallback = !showWebFallback },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text(
                                        text = if (showWebFallback) "إخفاء الربط البديل 🔓" else "هل فشل الربط السريع؟ اضغط للربط اليدوي الآمن 🌐",
                                        color = EmeraldPrimary,
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
                                                text = "خطوات الربط البديل الآمن:",
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
                                                        Toast.makeText(context, "تعذر فتح المتصفح.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().height(36.dp)
                                            ) {
                                                Text("1. فتح صفحة Google في المتصفح 🌐", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Text(
                                                text = "سجل دخولك ثم وافق على الصلاحية. عند ظهور صفحة فارغة، انسخ الرابط بالكامل من شريط عنوان المتصفح والصقه هنا:",
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B),
                                                lineHeight = 14.sp,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            OutlinedTextField(
                                                value = pastedWebCode,
                                                onValueChange = { pastedWebCode = it },
                                                placeholder = { Text("الصق الرابط المنسوخ أو الرمز هنا...", fontSize = 11.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = EmeraldPrimary,
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
                                                                Toast.makeText(context, "تم ربط حساب Google بنجاح! ☁️🎉 يمكنك الآن نسخ أو استعادة بياناتك يدوياً.", Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, "فشل ربط وتفعيل الرمز. تأكت من صحة نسخه بالكامل من شريط عنوان المتصفح.", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = pastedWebCode.trim().isNotEmpty(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().height(38.dp)
                                            ) {
                                                Text("2. تأكيد وتفعيل المزامنة السحابية 🔗", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextButton(
                                                onClick = { com.example.ui.viewmodel.openGoogleDriveApp(context) },
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            ) {
                                                Text(
                                                    text = "📥 تنزيل أو فتح تطبيق Google Drive لإدارة ملفاتك",
                                                    color = Color(0xFF3B82F6),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is CloudSyncState.Authenticating -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري تسجيل الدخول وربط الحساب الآمن...", fontSize = 11.sp, color = Color(0xFF075E54))
                            }
                        }
                        is CloudSyncState.Syncing -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري مزامنة السحابة وحفظ السجل...", fontSize = 11.sp, color = Color(0xFF075E54))
                            }
                        }
                        is CloudSyncState.Success, is CloudSyncState.Authenticated -> {
                            val email = if (state is CloudSyncState.Authenticated) state.email else (storedEmail ?: "حساب Google متصل")
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFDCFCE7), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "مرتبط ومفتوح: $email ✅",
                                        color = Color(0xFF15803D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                Text(
                                    text = "⚠️ تنبيه: التطبيق مرتبط حالياً بهذا البريد. إذا كنت ترغب في تفعيل حساب آخر أو واجهت أي تعارض، يرجى الضغط على زر تسجيل الخروج والتبديل أدناه.",
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
                                            viewModel.backupToGoogleDriveDirect { success ->
                                                if (success) {
                                                    Toast.makeText(context, "تم رفع ومزامنة السحاب بنجاح ☁️", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "تعذر الاتصال بخوادم Google، يرجى التحقق من الشبكة وإعادة المحاولة.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Text("رفع نسخة احتياطية ☁️", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.restoreFromGoogleDriveDirect(context) { success ->
                                                if (success) {
                                                    Toast.makeText(context, "تم استيراد واستعادة النسخة السحابية بنجاح بنسبة ١٠٠٪ ☁️🎉", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                } else {
                                                    Toast.makeText(context, "تعذر الاتصال بخوادم Google أو لم يتم العثور على ملف نسخ احتياطي Mizan_Backup.mzd على حسابك.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Text("استعادة نسخة السحاب 📥", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.googleDriveLogout()
                                        Toast.makeText(context, "تم تسجيل الخروج وقطع الاتصال بالحساب 🚪", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        Text("تسجيل الخروج وتبديل الحساب 🚪", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ACTION ONE: CREATE BACKUP BUTTON (Green / Olive Primary)
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        showExportOptions = !showExportOptions
                        showImportOptions = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)), // Elegant deep zayti/olive
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("نسخ احتياطي للبيانات", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(
                    visible = showExportOptions,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // SAF Local File Export
                        OutlinedButton(
                            onClick = {
                                onExportMzd()
                                showExportOptions = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF075E54)),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(40.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFF075E54), modifier = Modifier.size(14.dp))
                                Text("ملف محلي (.mzd)", color = Color(0xFF075E54), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Copy Encoded Base64
                        OutlinedButton(
                            onClick = {
                                viewModel.getBackupJsonForClipboard { json ->
                                    val base64 = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
                                    clipboardManager.setText(AnnotatedString(base64))
                                    Toast.makeText(context, "تم حفظ النسخة السريعة (نص مشفر) في الحافظة 📋", Toast.LENGTH_SHORT).show()
                                }
                                showExportOptions = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF075E54)),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(40.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF075E54), modifier = Modifier.size(14.dp))
                                Text("نسخ سريع (نص مشفر)", color = Color(0xFF075E54), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // ACTION TWO: RESTORE DATABASE BUTTON (Bordered Secondary Navy/Slate)
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        showImportOptions = !showImportOptions
                        showExportOptions = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(18.dp))
                        Text("استيراد البيانات", color = Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(
                    visible = showImportOptions,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // SAF Local File Import
                        Button(
                            onClick = {
                                onImportMzd()
                                showImportOptions = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(40.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("ملف محلي (.mzd)", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        // Base64 Paste Import
                        Button(
                            onClick = {
                                showPasteDialog = true
                                showImportOptions = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(40.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("لصق سريع (نص مشفر)", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ACTION THREE: RESET DATABASE BUTTON (Thin, red dashed border at bottom)
            OutlinedButton(
                onClick = { showResetConfirm1 = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SoftRed.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = SoftRed, modifier = Modifier.size(16.dp))
                    Text("حذف السجل كاملاً وبدء صفحة جديدة", color = SoftRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // BASE64 PASTE DIALOG
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("استيراد من نص مشفر (Base64)", color = Color(0xFF1E293B), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    placeholder = { Text("قم بلصق محتوى النسخة النصية المشفرة هنا...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left, fontSize = 11.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val decodedBytes = android.util.Base64.decode(pasteText.trim(), android.util.Base64.DEFAULT)
                            val decodedJson = String(decodedBytes)
                            onImportBase64(decodedJson)
                            showPasteDialog = false
                            onDismiss()
                        } catch (e: Exception) {
                            Toast.makeText(context, "فشل فك الرمز المشفر! تأكد من نسخ النص بالكامل.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("استيراد فوري", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }

    // RESET DATABASE DOUBLE-CONFIRMATION MODALS
    if (showResetConfirm1) {
        AlertDialog(
            onDismissRequest = { showResetConfirm1 = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("⚠️ تنبيه أمني خطير جداً!", color = SoftRed, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "إجراء غير قابل للتراجع! سيتم تهيئة التطبيق ومسح كافة السجلات نهائياً بما يشمل الحسابات الرئيسية والعملاء والمخزون.",
                    color = Color.DarkGray, fontSize = 12.sp, lineHeight = 20.sp, textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm1 = false
                        showResetConfirm2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("نعم، أريد المتابعة", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm1 = false }) {
                    Text("إلغاء التراجع", color = Color.Gray)
                }
            }
        )
    }

    if (showResetConfirm2) {
        AlertDialog(
            onDismissRequest = { showResetConfirm2 = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("🛡️ التحقق التأكيدي النهائي", color = SoftRed, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "هذه العملية لا يمكن الرجوع عنها نهائياً وسوف تفقد كل شيء بلا استثناء. هل أنت متأكد تماماً بنسبة ١٠٠٪؟",
                    color = Color.DarkGray, fontSize = 12.sp, lineHeight = 20.sp, textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLocalCopyAndWipeMemory(context)
                        Toast.makeText(context, "اكتملت تهيئة التطبيق ومسح كافة السجلات 🗑️", Toast.LENGTH_LONG).show()
                        showResetConfirm2 = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تأكيد المسح الشامل", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm2 = false }) {
                    Text("تراجع الآن 🏠", color = Color.Gray)
                }
            }
        )
    }
}



