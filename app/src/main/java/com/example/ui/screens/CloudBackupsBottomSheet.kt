package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.CloudBackupFile
import com.example.data.CloudSyncState
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftRed
import com.example.ui.viewmodel.FinanceViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupsBottomSheet(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Collect flows safely using safe lifecycle-aware state collectors
    val cloudBackups by viewModel.cloudBackupsList.collectAsStateWithLifecycle()
    val isFetching by viewModel.isFetchingCloudBackups.collectAsStateWithLifecycle()
    val syncState by viewModel.googleDriveSyncState.collectAsStateWithLifecycle()
    
    val storedEmail = viewModel.googleDriveSyncHelper.getStoredEmail()
    val isConnected = !storedEmail.isNullOrEmpty() || syncState is CloudSyncState.Authenticated || syncState is CloudSyncState.Success
    
    // UI Local States
    var showRestoreConfirmId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmId by remember { mutableStateOf<String?>(null) }
    var menuExpandedFileId by remember { mutableStateOf<String?>(null) }
    var ongoingActionMessage by remember { mutableStateOf<String?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedFileIds = remember { mutableStateListOf<String>() }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }

    // Fetch cloud backups list when bottom sheet opens
    LaunchedEffect(Unit) {
        if (isConnected) {
            viewModel.fetchCloudBackupsList()
        }
    }

    LaunchedEffect(syncState) {
        if (syncState is CloudSyncState.Success) {
            Toast.makeText(context, context.getString(R.string.cloud_toast_new_backup_success), Toast.LENGTH_LONG).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 80.dp), // space for bottom button
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("dismiss_cloud_backups_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cloud_desc_close),
                                tint = Color(0xFF64748B)
                            )
                        }

                        if (isConnected && cloudBackups.isNotEmpty()) {
                            // Selection Mode toggle button in the header
                            TextButton(
                                onClick = {
                                    isSelectionMode = !isSelectionMode
                                    if (!isSelectionMode) {
                                        selectedFileIds.clear()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelectionMode) Icons.Default.EditOff else Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.cloud_desc_select),
                                    tint = if (isSelectionMode) Color(0xFFEF4444) else Color(0xFF3B82F6),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSelectionMode) stringResource(R.string.cloud_btn_cancel_back) else stringResource(R.string.cloud_btn_multi_select),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelectionMode) Color(0xFFEF4444) else Color(0xFF3B82F6)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.cloud_sheet_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }
                }

                Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                if (!isConnected) {
                    // Not connected state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = stringResource(R.string.cloud_not_linked_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = stringResource(R.string.cloud_not_linked_desc),
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    // Connected: Show Dashboard Header
                    val isAllSelected = cloudBackups.isNotEmpty() && selectedFileIds.size == cloudBackups.size
                    CloudStatsHeader(
                        email = storedEmail ?: stringResource(R.string.cloud_default_connected_acc),
                        backupsCount = cloudBackups.size,
                        isFetching = isFetching,
                        onRefresh = { viewModel.fetchCloudBackupsList() },
                        isSelectionMode = isSelectionMode,
                        isAllSelected = isAllSelected,
                        onToggleSelectAll = {
                            if (isAllSelected) {
                                selectedFileIds.clear()
                            } else {
                                selectedFileIds.clear()
                                selectedFileIds.addAll(cloudBackups.map { it.id })
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (syncState is CloudSyncState.Error) {
                        val errMsg = (syncState as CloudSyncState.Error).message
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.cloud_warn_perm_conn),
                                    color = Color(0xFF991B1B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(R.string.cloud_warn_perm_conn_desc, errMsg),
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // List views
                    if (isFetching && cloudBackups.isEmpty()) {
                        // Loading state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = EmeraldPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.cloud_fetching_list),
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    } else if (cloudBackups.isEmpty()) {
                        // Empty State
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BackupTable,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = stringResource(R.string.cloud_empty_backups),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = stringResource(R.string.cloud_empty_backups_desc),
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Cloud Backups List View
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_backups_lazy_list")
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                             items(cloudBackups) { backupFile ->
                                 CloudBackupItemRow(
                                     backup = backupFile,
                                     menuExpanded = menuExpandedFileId == backupFile.id,
                                     onMenuToggle = { expanded ->
                                         menuExpandedFileId = if (expanded) backupFile.id else null
                                     },
                                     onRestoreClick = {
                                         menuExpandedFileId = null
                                         showRestoreConfirmId = backupFile.id
                                     },
                                     onDeleteClick = {
                                         menuExpandedFileId = null
                                         showDeleteConfirmId = backupFile.id
                                     },
                                     isSelectionMode = isSelectionMode,
                                     isSelected = selectedFileIds.contains(backupFile.id),
                                     onSelectedChange = { selected ->
                                         if (selected) {
                                             selectedFileIds.add(backupFile.id)
                                         } else {
                                             selectedFileIds.remove(backupFile.id)
                                         }
                                     },
                                     onLongClick = {
                                         if (!isSelectionMode) {
                                             isSelectionMode = true
                                             selectedFileIds.clear()
                                             selectedFileIds.add(backupFile.id)
                                         }
                                     }
                                 )
                             }
                        }
                    }
                }
            }

            // Bottom Floating Action bar
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            color = Color.White.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(16.dp)
                ) {
                    if (isSelectionMode && selectedFileIds.isNotEmpty()) {
                        Button(
                            onClick = {
                                showMultiDeleteConfirm = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("multi_delete_cloud_backups_button")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(
                                    text = stringResource(R.string.cloud_btn_delete_count, selectedFileIds.size),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                ongoingActionMessage = context.getString(R.string.cloud_progress_uploading_instant)
                                viewModel.uploadBackupToGoogleDrive { success ->
                                    ongoingActionMessage = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("backup_to_cloud_now_button")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(
                                    text = stringResource(R.string.cloud_btn_backup_now),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Action Overlay Dialogs ---

    // Ongoing Progress overlay
    if (ongoingActionMessage != null) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(40.dp))
                    Text(
                        text = ongoingActionMessage ?: "",
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Restore Backup Confirmation Dialog
    if (showRestoreConfirmId != null) {
        val targetId = showRestoreConfirmId!!
        val fileItem = cloudBackups.find { it.id == targetId }
        val displayName = fileItem?.name ?: stringResource(R.string.cloud_default_selected_backup)
        
        AlertDialog(
            onDismissRequest = { showRestoreConfirmId = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.cloud_restore_confirm_title),
                    color = SoftRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.cloud_restore_confirm_desc, displayName),
                    color = Color(0xFF334155),
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmId = null
                        ongoingActionMessage = context.getString(R.string.cloud_progress_restoring)
                        viewModel.restoreFromGoogleDriveById(context, targetId) { success ->
                            ongoingActionMessage = null
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.cloud_toast_restore_success), Toast.LENGTH_LONG).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, context.getString(R.string.cloud_toast_restore_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cloud_btn_restore_confirm),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmId = null }) {
                    Text(
                        text = stringResource(R.string.cloud_btn_cancel_action),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        )
    }

    // Delete Backup Confirmation Dialog
    if (showDeleteConfirmId != null) {
        val targetId = showDeleteConfirmId!!
        val fileItem = cloudBackups.find { it.id == targetId }
        val displayName = fileItem?.name ?: stringResource(R.string.cloud_default_selected_backup)

        AlertDialog(
            onDismissRequest = { showDeleteConfirmId = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.cloud_delete_confirm_title),
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.cloud_delete_confirm_desc, displayName),
                    color = Color(0xFF334155),
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmId = null
                        ongoingActionMessage = context.getString(R.string.cloud_progress_deleting)
                        viewModel.deleteCloudBackupById(targetId) { success ->
                            ongoingActionMessage = null
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.cloud_toast_delete_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.cloud_toast_delete_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cloud_btn_delete_confirm),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmId = null }) {
                    Text(
                        text = stringResource(R.string.cloud_btn_generic_cancel),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        )
    }

    // Multi-Delete Selected Confirmation Dialog
    if (showMultiDeleteConfirm) {
        val selectedCount = selectedFileIds.size
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirm = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.cloud_multi_delete_confirm_title),
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.cloud_multi_delete_confirm_desc, selectedCount),
                    color = Color(0xFF334155),
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMultiDeleteConfirm = false
                        ongoingActionMessage = context.getString(R.string.cloud_progress_multi_deleting)
                        viewModel.deleteMultipleCloudBackupsByIds(selectedFileIds.toList()) { success ->
                            ongoingActionMessage = null
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.cloud_toast_multi_delete_success), Toast.LENGTH_SHORT).show()
                                selectedFileIds.clear()
                                isSelectionMode = false
                            } else {
                                Toast.makeText(context, context.getString(R.string.cloud_toast_multi_delete_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cloud_btn_multi_delete_confirm),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirm = false }) {
                    Text(
                        text = stringResource(R.string.cloud_btn_generic_cancel),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        )
    }
}

@Composable
fun CloudStatsHeader(
    email: String,
    backupsCount: Int,
    isFetching: Boolean,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean = false,
    isAllSelected: Boolean = false,
    onToggleSelectAll: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Connection Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refresh icon/button on far left
                IconButton(
                    onClick = onRefresh,
                    enabled = !isFetching,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("refresh_cloud_backups_stats_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.cloud_desc_refresh),
                        tint = if (isFetching) Color.Gray else EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Cloud Icon and text
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isSelectionMode) stringResource(R.string.cloud_status_selection_mode) else stringResource(R.string.cloud_status_secured_connected),
                            color = if (isSelectionMode) Color(0xFF3B82F6) else Color(0xFF15803D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = email,
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelectionMode) Color(0xFFDBEAFE) else Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.Checklist else Icons.Default.Backup,
                            contentDescription = null,
                            tint = if (isSelectionMode) Color(0xFF3B82F6) else Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Divider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)

            // Dynamic Stats Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cloud_stat_count_pattern, backupsCount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                    textAlign = TextAlign.Left
                )

                if (isSelectionMode) {
                    TextButton(
                        onClick = onToggleSelectAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF3B82F6)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (isAllSelected) stringResource(R.string.cloud_btn_cancel_selection) else stringResource(R.string.cloud_btn_select_all),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.cloud_stat_taken_space),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Right
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudBackupItemRow(
    backup: CloudBackupFile,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val (dateStr, timeStr) = remember(backup.name, backup.createdTime) {
        formatBackupDateTime(context, backup.name, backup.createdTime)
    }

    val displaySize = remember(backup.size) {
        if (backup.size <= 0L) {
            "-- KB"
        } else {
            String.format(java.util.Locale.US, "%.1f KB", backup.size / 1024.0)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color(0xFF3B82F6) else Color(0xFFF1F5F9)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectedChange(!isSelected)
                    } else {
                        onMenuToggle(true)
                    }
                },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Far Left: Size and 3-dots Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displaySize,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )

                if (!isSelectionMode) {
                    Box {
                        IconButton(
                            onClick = { onMenuToggle(true) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("backup_menu_${backup.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cloud_desc_file_options),
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { onMenuToggle(false) },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = stringResource(R.string.cloud_menu_restore_this),
                                            fontSize = 12.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                },
                                onClick = onRestoreClick
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = SoftRed, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = stringResource(R.string.cloud_menu_delete_this),
                                            fontSize = 12.sp,
                                            color = SoftRed
                                        )
                                    }
                                },
                                onClick = onDeleteClick
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }
            }

            // Middle: Name, Date and Time
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = dateStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            // Far Right: Cloud Icon OR Checkbox
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF3B82F6),
                        uncheckedColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Utility Function to format Date & Time elegantly
 */
fun formatBackupDateTime(context: Context, filename: String, createdTimeIso: String): Pair<String, String> {
    var dateString = context.getString(R.string.cloud_date_unknown)
    var timeString = "--:--"
    
    // First, try from filename which starts with Mzd_
    if (filename.startsWith("Mzd_") && filename.length >= 18) {
        try {
            val clean = filename.replace("Mzd_", "").replace(".mzd", "")
            val segments = clean.split("_")
            if (segments.isNotEmpty()) {
                val datePart = segments[0]
                val dateSplit = datePart.split("-")
                if (dateSplit.size == 3) {
                    dateString = "${dateSplit[2]}-${dateSplit[1]}-${dateSplit[0]}"
                }
                
                if (segments.size > 1) {
                    val timePart = segments[1]
                    val timeSplit = timePart.split("-")
                    if (timeSplit.size >= 2) {
                        val hour = timeSplit[0].toIntOrNull() ?: 12
                        val min = timeSplit[1].toIntOrNull() ?: 0
                        val amPm = if (hour >= 12) context.getString(R.string.cloud_time_pm) else context.getString(R.string.cloud_time_am)
                        val hour12 = when {
                            hour == 0 -> 12
                            hour > 12 -> hour - 12
                            else -> hour
                        }
                        timeString = String.format("%d:%02d %s", hour12, min, amPm)
                        return Pair(dateString, timeString)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Fallback: Parse ISO timestamp createdTimeIso (e.g. 2026-06-15T22:30:15.000Z)
    if (createdTimeIso.isNotEmpty()) {
        try {
            // Split Date and Time
            val parts = createdTimeIso.split("T")
            if (parts.size >= 2) {
                // Parse Date: 2026-06-15
                val datePart = parts[0]
                val dateSplit = datePart.split("-")
                if (dateSplit.size == 3) {
                    dateString = "${dateSplit[2]}-${dateSplit[1]}-${dateSplit[0]}"
                }
                // Parse Time: 22:30:15...
                val timePart = parts[1]
                val timeSplit = timePart.split(":")
                if (timeSplit.size >= 2) {
                    val hour = timeSplit[0].toIntOrNull() ?: 12
                    val min = timeSplit[1].toIntOrNull() ?: 0
                    val amPm = if (hour >= 12) context.getString(R.string.cloud_time_pm) else context.getString(R.string.cloud_time_am)
                    val hour12 = when {
                        hour == 0 -> 12
                        hour > 12 -> hour - 12
                        else -> hour
                    }
                    timeString = String.format("%d:%02d %s", hour12, min, amPm)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    return Pair(dateString, timeString)
}
