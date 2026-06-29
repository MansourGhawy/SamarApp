package com.example.ui.screens.habayeb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entities.HabayebTransaction
import java.util.Locale

@Composable
fun TransactionOptionsDialog(
    transaction: HabayebTransaction,
    customerName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAutoRepeat: () -> Unit,
    activeThemeColor: Color,
    activeSubColor: Color,
    isRecurringOriginal: Boolean = false,
    onDeleteAutoRepeat: (() -> Unit)? = null,
    parentSeqNumber: Int? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ultra-compact details tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        val desc = if (transaction.description.isNotBlank()) transaction.description else customerName
                        val shortDesc = if (desc.length > 20) desc.take(20) + "..." else desc
                        Text(
                            text = shortDesc,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = " • ",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        val formattedAmount = try { String.format(Locale.ENGLISH, "%,.0f", transaction.amount) } catch (e: Exception) { transaction.amount.toString() }
                        val isPositive = transaction.type == "PAYMENT_BY_THEM" || transaction.type == "OWED_TO_THEM"
                        val amountColor = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626)
                        Text(
                            text = "$formattedAmount ريال",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = amountColor
                        )
                    }

                    // Warning / Status Banner for Recurring Relationships
                    if (isRecurringOriginal) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "🔄 هذه المعاملة هي مصدر مجدول لتكرار العمليات تلقائياً. أي تعديل أو حذف قد يؤثر على تكرار العمليات.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF92400E),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    } else if (parentSeqNumber != null) {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "⚙️ تم إنشاء هذه المعاملة تلقائياً تكراراً للمعامله الأصلية رقم #$parentSeqNumber.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E40AF),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // Compact Actions Grid/Row
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 1. Edit
                        ActionCircleItem(
                            title = "تعديل",
                            icon = Icons.Default.Edit,
                            containerColor = activeThemeColor.copy(alpha = 0.12f),
                            iconColor = activeThemeColor,
                            onClick = onEdit
                        )

                        // 2. Delete
                        ActionCircleItem(
                            title = "حذف",
                            icon = Icons.Default.Delete,
                            containerColor = Color(0xFFFEF2F2),
                            iconColor = Color(0xFFEF4444),
                            onClick = onDelete
                        )

                        if (isRecurringOriginal) {
                            // 3. Edit Auto-Repeat
                            ActionCircleItem(
                                title = "تعديل التكرار",
                                icon = Icons.Default.Sync,
                                containerColor = Color(0xFFFFFAF0),
                                iconColor = Color(0xFFD97706),
                                onClick = onAutoRepeat
                            )

                            // 4. Stop Auto-Repeat
                            ActionCircleItem(
                                title = "إلغاء التكرار",
                                icon = Icons.Default.Schedule,
                                containerColor = Color(0xFFFFF1F2),
                                iconColor = Color(0xFFE11D48),
                                onClick = { onDeleteAutoRepeat?.invoke() }
                            )
                        } else {
                            // 3. Setup Auto-Repeat
                            ActionCircleItem(
                                title = "جدولة تكرار",
                                icon = Icons.Default.Sync,
                                containerColor = Color(0xFFFFFAF0),
                                iconColor = Color(0xFFD97706),
                                onClick = onAutoRepeat
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionCircleItem(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(64.dp)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(containerColor, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569),
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 14.sp
        )
    }
}
