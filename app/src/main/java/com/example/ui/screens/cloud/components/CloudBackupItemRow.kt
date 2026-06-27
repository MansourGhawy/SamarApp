package com.example.ui.screens.cloud.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CloudBackupFile

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
                                tint = Color(0xFF94A3B8)
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
                                        Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
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
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        Text(
                                            text = stringResource(R.string.cloud_menu_delete_this),
                                            fontSize = 12.sp,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                },
                                onClick = onDeleteClick
                            )
                        }
                    }
                } else {
                    // Checkbox indicator for selection mode
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFF3B82F6) else Color(0xFFCBD5E1),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(start = 4.dp)
                    )
                }
            }

            // Right: Date & Time details
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = dateStr,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = timeStr,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
