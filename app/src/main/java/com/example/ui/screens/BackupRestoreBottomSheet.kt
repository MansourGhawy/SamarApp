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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.data.CloudSyncState
import com.example.data.local.AppSettings
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftRed
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val googleSyncState by viewModel.googleDriveSyncState.collectAsStateWithLifecycle()
    val storedEmail = remember(googleSyncState) { viewModel.googleDriveSyncHelper.getStoredEmail() }
    val isConnected = !storedEmail.isNullOrEmpty() || googleSyncState is CloudSyncState.Authenticated || googleSyncState is CloudSyncState.Success

    var showExportOptions by remember { mutableStateOf(false) }
    var showImportOptions by remember { mutableStateOf(false) }
    var showCloudBackupsSheet by remember { mutableStateOf(false) }
    var isSyncLoggingOut by remember { mutableStateOf(false) }
    
    // Paste Base64 Dialog
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    
    // Reset confirmation Dialogs (Double confirmation modal)
    var showResetConfirm1 by remember { mutableStateOf(false) }
    var showResetConfirm2 by remember { mutableStateOf(false) }

    // Restore confirmation dialog states
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var onConfirmRestoreAction by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                            Toast.makeText(context, context.getString(R.string.backup_toast_linked_success, email), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.backup_toast_connect_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.backup_toast_invalid_code), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BackupRestoreBottomSheet", "Google sign in failed", e)
                Toast.makeText(context, context.getString(R.string.backup_toast_connect_error, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
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
                    Toast.makeText(context, context.getString(R.string.backup_toast_config_error, sc), Toast.LENGTH_LONG).show()
                    handledError = true
                } catch (e: Exception) {
                    Log.e("BackupRestoreBottomSheet", "Sign in task exception", e)
                }
            }
            if (!handledError) {
                Toast.makeText(context, context.getString(R.string.backup_toast_cancelled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(googleSyncState) {
        if (googleSyncState is CloudSyncState.SessionExpired) {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInClient.revokeAccess().addOnCompleteListener {
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
            }
        } else if (googleSyncState is CloudSyncState.Success) {
            Toast.makeText(context, context.getString(R.string.backup_toast_sync_success), Toast.LENGTH_SHORT).show()
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
                        text = stringResource(R.string.backup_sheet_title),
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
                            text = if (isConnected) stringResource(R.string.backup_status_connected) else stringResource(R.string.backup_status_local),
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
                            text = stringResource(R.string.backup_cloud_linking_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }

                    when (val state = googleSyncState) {
                        is CloudSyncState.Idle, is CloudSyncState.Error, is CloudSyncState.SessionExpired -> {
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
                                            googleSignInClient.revokeAccess().addOnCompleteListener {
                                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                            }
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
                                        Text(stringResource(R.string.backup_btn_gdrive_quick), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = { showWebFallback = !showWebFallback },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text(
                                        text = if (showWebFallback) stringResource(R.string.backup_btn_gdrive_fallback_hide) else stringResource(R.string.backup_btn_gdrive_fallback_show),
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
                                                text = stringResource(R.string.backup_gdrive_fallback_steps),
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
                                                        Toast.makeText(context, context.getString(R.string.backup_toast_browser_failed), Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().height(36.dp)
                                            ) {
                                                Text(stringResource(R.string.backup_btn_gdrive_open_browser), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Text(
                                                text = stringResource(R.string.backup_gdrive_fallback_desc),
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B),
                                                lineHeight = 14.sp,
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            OutlinedTextField(
                                                value = pastedWebCode,
                                                onValueChange = { pastedWebCode = it },
                                                placeholder = { Text(stringResource(R.string.backup_placeholder_oauth_code), fontSize = 11.sp) },
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
                                                                Toast.makeText(context, context.getString(R.string.backup_toast_oauth_success), Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, context.getString(R.string.backup_toast_oauth_failed), Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = pastedWebCode.trim().isNotEmpty(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().height(38.dp)
                                            ) {
                                                Text(stringResource(R.string.backup_btn_oauth_confirm), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextButton(
                                                onClick = { com.example.ui.viewmodel.openGoogleDriveApp(context) },
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.backup_btn_gdrive_open_app),
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
                                Text(stringResource(R.string.backup_toast_cloud_auth), fontSize = 11.sp, color = Color(0xFF075E54))
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
                                Text(stringResource(R.string.backup_toast_cloud_syncing), fontSize = 11.sp, color = Color(0xFF075E54))
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
                                        text = stringResource(R.string.backup_gdrive_linked_pattern, email),
                                        color = Color(0xFF15803D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.backup_gdrive_linked_warning),
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
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Text(stringResource(R.string.backup_btn_upload_backup), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            onConfirmRestoreAction = {
                                                viewModel.restoreFromGoogleDriveDirect(context) { success ->
                                                    if (success) {
                                                        Toast.makeText(context, context.getString(R.string.toast_cloud_restore_success), Toast.LENGTH_SHORT).show()
                                                        onDismiss()
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.toast_cloud_restore_failed_or_missing), Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            showRestoreConfirmDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Text(stringResource(id = R.string.btn_cloud_restore), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        showCloudBackupsSheet = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("open_cloud_backups_archive_sheet")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.BackupTable, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Text(stringResource(R.string.backup_btn_browse_archives), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        isSyncLoggingOut = true
                                        viewModel.googleDriveLogout {
                                            isSyncLoggingOut = false
                                            Toast.makeText(context, context.getString(R.string.backup_toast_gdrive_logout_success), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isSyncLoggingOut,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), disabledContainerColor = Color(0xFFF1F5F9)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSyncLoggingOut) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFEF4444), strokeWidth = 2.dp)
                                            Text(stringResource(R.string.backup_gdrive_logging_out), fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            Text(stringResource(R.string.backup_btn_gdrive_logout), fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                        }
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
                        Text(stringResource(R.string.backup_btn_main_backup), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.backup_btn_local_mzd), color = Color(0xFF075E54), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Copy Encoded Base64
                        OutlinedButton(
                            onClick = {
                                viewModel.getBackupJsonForClipboard { json ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val base64 = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
                                            withContext(Dispatchers.Main) {
                                                clipboardManager.setText(AnnotatedString(base64))
                                                Toast.makeText(context, context.getString(R.string.backup_toast_copied_success), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("BackupRestoreBottomSheet", "Failed to encode copy to clipboard", e)
                                        }
                                    }
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
                                Text(stringResource(R.string.backup_btn_fast_encoded), color = Color(0xFF075E54), fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
                        Text(stringResource(R.string.backup_btn_main_import), color = Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                onConfirmRestoreAction = {
                                    onImportMzd()
                                }
                                showRestoreConfirmDialog = true
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
                                Text(stringResource(R.string.backup_btn_local_mzd), color = Color.White, fontSize = 11.sp)
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
                                Text(stringResource(R.string.backup_btn_paste_encoded), color = Color.White, fontSize = 10.sp)
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
                    Text(stringResource(R.string.backup_btn_delete_all), color = SoftRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            title = { Text(stringResource(R.string.backup_dialog_restore_paste_title), color = Color(0xFF1E293B), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    placeholder = { Text(stringResource(R.string.backup_dialog_restore_paste_desc)) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left, fontSize = 11.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val decodedBytes = android.util.Base64.decode(pasteText.trim(), android.util.Base64.DEFAULT)
                                val decodedJson = String(decodedBytes)
                                withContext(Dispatchers.Main) {
                                    onConfirmRestoreAction = {
                                        onImportBase64(decodedJson)
                                    }
                                    showRestoreConfirmDialog = true
                                    showPasteDialog = false
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.backup_toast_paste_failed), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.backup_btn_restore_now), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text(stringResource(R.string.backup_btn_cancel), color = Color.Gray)
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
            title = { Text(stringResource(R.string.backup_reset1_title), color = SoftRed, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    stringResource(R.string.backup_reset1_desc),
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
                    Text(stringResource(R.string.backup_reset_confirm_btn), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm1 = false }) {
                    Text(stringResource(R.string.backup_reset_cancel_btn), color = Color.Gray)
                }
            }
        )
    }

    if (showCloudBackupsSheet) {
        CloudBackupsBottomSheet(
            viewModel = viewModel,
            onDismiss = { showCloudBackupsSheet = false }
        )
    }

    if (showResetConfirm2) {
        AlertDialog(
            onDismissRequest = { showResetConfirm2 = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.backup_reset2_title), color = SoftRed, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    stringResource(R.string.backup_reset2_desc),
                    color = Color.DarkGray, fontSize = 12.sp, lineHeight = 20.sp, textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLocalCopyAndWipeMemory(context)
                        Toast.makeText(context, context.getString(R.string.backup_toast_reset_success), Toast.LENGTH_LONG).show()
                        showResetConfirm2 = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.backup_reset_final_btn), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm2 = false }) {
                    Text(stringResource(R.string.backup_reset_final_cancel_btn), color = Color.Gray)
                }
            }
        )
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRestoreConfirmDialog = false
                onConfirmRestoreAction = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = Color(0xFFDC2626), // لون أحمر تحذيري وقور
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { 
                Text(
                    text = stringResource(R.string.backup_restore_warn_title), 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 16.sp,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                ) 
            },
            text = { 
                Text(
                    text = stringResource(R.string.backup_restore_warn_desc),
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Right
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        // تنفيذ الإجراء المحفوظ (سواء سحابي أو محلي) مغلقاً الحوار آلياً
                        onConfirmRestoreAction?.invoke()
                        showRestoreConfirmDialog = false
                        onConfirmRestoreAction = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626) // أحمر لخطورة العملية
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.backup_restore_confirm_btn), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { 
                        showRestoreConfirmDialog = false
                        onConfirmRestoreAction = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.backup_reset_cancel_btn), color = Color.Gray)
                }
            }
        )
    }
}
