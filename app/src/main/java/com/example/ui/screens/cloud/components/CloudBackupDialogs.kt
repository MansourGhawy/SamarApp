package com.example.ui.screens.cloud.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.CloudBackupFile
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftRed
import com.example.ui.viewmodel.FinanceViewModel

@Composable
fun CloudOngoingActionDialog(ongoingActionMessage: String) {
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
                    text = ongoingActionMessage,
                    fontSize = 13.sp,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CloudRestoreConfirmDialog(
    context: Context,
    targetId: String,
    cloudBackups: List<CloudBackupFile>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onStartAction: (String) -> Unit,
    onCompleteAction: () -> Unit,
    onSheetDismiss: () -> Unit
) {
    val fileItem = cloudBackups.find { it.id == targetId }
    val displayName = fileItem?.name ?: stringResource(R.string.cloud_default_selected_backup)
    
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onStartAction(context.getString(R.string.cloud_progress_restoring))
                    viewModel.restoreFromGoogleDriveById(context, targetId) { success ->
                        onCompleteAction()
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.cloud_toast_restore_success), Toast.LENGTH_LONG).show()
                            onSheetDismiss()
                        } else {
                            Toast.makeText(context, context.getString(R.string.cloud_toast_restore_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                    onDismiss()
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
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cloud_btn_cancel_action),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    )
}

@Composable
fun CloudDeleteConfirmDialog(
    context: Context,
    targetId: String,
    cloudBackups: List<CloudBackupFile>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onStartAction: (String) -> Unit,
    onCompleteAction: () -> Unit
) {
    val fileItem = cloudBackups.find { it.id == targetId }
    val displayName = fileItem?.name ?: stringResource(R.string.cloud_default_selected_backup)

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onStartAction(context.getString(R.string.cloud_progress_deleting))
                    viewModel.deleteCloudBackupById(targetId) { success ->
                        onCompleteAction()
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.cloud_toast_delete_success), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.cloud_toast_delete_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                    onDismiss()
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
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cloud_btn_generic_cancel),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    )
}

@Composable
fun CloudMultiDeleteConfirmDialog(
    context: Context,
    selectedFileIds: List<String>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onStartAction: (String) -> Unit,
    onCompleteAction: (Boolean) -> Unit
) {
    val selectedCount = selectedFileIds.size
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onStartAction(context.getString(R.string.cloud_progress_multi_deleting))
                    viewModel.deleteMultipleCloudBackupsByIds(selectedFileIds.toList()) { success ->
                        onCompleteAction(success)
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.cloud_toast_multi_delete_success), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.cloud_toast_multi_delete_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                    onDismiss()
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
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cloud_btn_generic_cancel),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    )
}
