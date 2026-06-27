package com.example.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SettingsSuggest
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
import com.example.R
import com.example.ui.theme.EmeraldPrimary

@Composable
fun BackupPermissionExplanationDialog(
    onDismiss: () -> Unit,
    onGrantPermissions: () -> Unit,
    onUseInternalStorage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.settings_permissions_dialog_title),
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
                    stringResource(R.string.settings_permissions_dialog_desc),
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
                    Text(stringResource(R.string.settings_permissions_storage_label), fontSize = 11.sp, color = Color(0xFF334155), textAlign = TextAlign.Right)
                    Icon(Icons.Default.Folder, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_permissions_manage_files_label), fontSize = 11.sp, color = Color(0xFF334155), textAlign = TextAlign.Right)
                        Icon(Icons.Default.SettingsSuggest, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(16.dp))
                    }
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_permissions_notifications_label), fontSize = 11.sp, color = Color(0xFF334155), textAlign = TextAlign.Right)
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_permissions_fallback_note),
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
                onClick = onGrantPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(stringResource(R.string.settings_permissions_grant_btn), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onUseInternalStorage
            ) {
                Text(stringResource(R.string.settings_permissions_internal_storage_btn), fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }
    )
}
