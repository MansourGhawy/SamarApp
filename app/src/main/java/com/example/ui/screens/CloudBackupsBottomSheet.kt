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
import com.example.ui.screens.cloud.components.*
import kotlinx.coroutines.launch

import com.example.ui.viewmodel.SyncSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupsBottomSheet(
    viewModel: FinanceViewModel,
    syncViewModel: SyncSettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Collect flows safely using safe lifecycle-aware state collectors
    val cloudBackups by syncViewModel.cloudBackupsList.collectAsStateWithLifecycle()
    val isFetching by syncViewModel.isFetchingCloudBackups.collectAsStateWithLifecycle()
    val syncState by syncViewModel.googleDriveSyncState.collectAsStateWithLifecycle()
    
    val storedEmail = syncViewModel.googleDriveSyncHelper.getStoredEmail()
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
            syncViewModel.fetchCloudBackupsList()
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
                        onRefresh = { syncViewModel.fetchCloudBackupsList() },
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
                                syncViewModel.uploadBackupToGoogleDrive { success ->
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
        com.example.ui.screens.cloud.components.CloudOngoingActionDialog(ongoingActionMessage!!)
    }

    // Restore Backup Confirmation Dialog
    if (showRestoreConfirmId != null) {
        val targetId = showRestoreConfirmId!!
        com.example.ui.screens.cloud.components.CloudRestoreConfirmDialog(
            context = context,
            targetId = targetId,
            cloudBackups = cloudBackups,
            syncViewModel = syncViewModel,
            onDismiss = { showRestoreConfirmId = null },
            onStartAction = { msg -> ongoingActionMessage = msg },
            onCompleteAction = { ongoingActionMessage = null },
            onSheetDismiss = onDismiss
        )
    }

    // Delete Backup Confirmation Dialog
    if (showDeleteConfirmId != null) {
        val targetId = showDeleteConfirmId!!
        com.example.ui.screens.cloud.components.CloudDeleteConfirmDialog(
            context = context,
            targetId = targetId,
            cloudBackups = cloudBackups,
            syncViewModel = syncViewModel,
            onDismiss = { showDeleteConfirmId = null },
            onStartAction = { msg -> ongoingActionMessage = msg },
            onCompleteAction = { ongoingActionMessage = null }
        )
    }

    // Multi-Delete Selected Confirmation Dialog
    if (showMultiDeleteConfirm) {
        com.example.ui.screens.cloud.components.CloudMultiDeleteConfirmDialog(
            context = context,
            selectedFileIds = selectedFileIds.toList(),
            syncViewModel = syncViewModel,
            onDismiss = { showMultiDeleteConfirm = false },
            onStartAction = { msg -> ongoingActionMessage = msg },
            onCompleteAction = { success -> 
                ongoingActionMessage = null
                if (success) {
                    selectedFileIds.clear()
                    isSelectionMode = false
                }
            }
        )
    }
}






