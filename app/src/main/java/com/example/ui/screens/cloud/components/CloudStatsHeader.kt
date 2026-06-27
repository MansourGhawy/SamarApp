package com.example.ui.screens.cloud.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.EmeraldPrimary

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
