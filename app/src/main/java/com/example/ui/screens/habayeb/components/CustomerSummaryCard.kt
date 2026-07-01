package com.example.ui.screens.habayeb.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.HabayebCustomer
import com.example.ui.helper.formatCurrency
import com.example.ui.helper.getInitialColor
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerSummaryCard(
    activeCustomer: HabayebCustomer,
    currencyGroups: Map<String, List<Any>>,
    netDebtMap: Map<String, Double>,
    owedByThemMap: Map<String, Double>,
    paymentByThemMap: Map<String, Double>,
    owedToThemMap: Map<String, Double>,
    paymentToThemMap: Map<String, Double>,
    activeThemeColor: Color,
    isPdfExporting: Boolean,
    onPdfExportClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(6.dp)
    ) {
        // Top row: Avatar, Name/Phone, and Elegant Balance Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarColor = getInitialColor(activeCustomer.name)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeCustomer.name.trim().firstOrNull()?.toString()?.uppercase() ?: "؟",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = avatarColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = activeCustomer.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activeCustomer.phone.ifEmpty { "لا يوجد هاتف مسجل" },
                    fontSize = 9.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                for (curr in currencyGroups.keys.sorted()) {
                    val cNetDebt = netDebtMap[curr] ?: 0.0
                    val textBalanceColor = when {
                        cNetDebt > 0.0 -> Color(0xFFDC2626) // Red (They owe you)
                        cNetDebt < 0.0 -> Color(0xFF16A34A) // Green (You owe them)
                        else -> Color(0xFF334155)
                    }
                    val stateLabel = when {
                        cNetDebt > 0.0 -> "مطلوب منه"
                        cNetDebt < 0.0 -> "مطلوب له"
                        else -> "متعادل"
                    }
                    
                    val signedNetDebtStr = if (cNetDebt < 0.0) {
                        "-" + formatCurrency(kotlin.math.abs(cNetDebt), curr)
                    } else {
                        formatCurrency(cNetDebt, curr)
                    }

                    // Compact colored balance badge
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(textBalanceColor.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = signedNetDebtStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = textBalanceColor
                            )
                            Text(
                                text = stateLabel,
                                fontSize = 7.sp,
                                color = textBalanceColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
        Spacer(modifier = Modifier.height(4.dp))

        // Bottom row: Actions & Sub-totals
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isPhoneAvailable = activeCustomer.phone.isNotBlank()
                val iconModifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))

                // PDF
                Box(
                    modifier = iconModifier
                        .background(activeThemeColor.copy(alpha = 0.1f))
                        .clickable(enabled = !isPdfExporting) { onPdfExportClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPdfExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = activeThemeColor,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(Icons.Default.Description, contentDescription = "مشاركة PDF", tint = activeThemeColor, modifier = Modifier.size(14.dp))
                    }
                }
                
                // WhatsApp
                Box(
                    modifier = iconModifier
                        .background(if (isPhoneAvailable) Color(0xFF16A34A).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f))
                        .clickable(enabled = isPhoneAvailable) { onWhatsAppClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = if (isPhoneAvailable) Color(0xFF16A34A) else Color.Gray.copy(alpha = 0.35f), modifier = Modifier.size(14.dp))
                }

                // Edit
                Box(
                    modifier = iconModifier
                        .background(Color(0xFFF1F5F9))
                        .clickable { onEditClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                }

                // Delete
                Box(
                    modifier = iconModifier
                        .background(Color(0xFFEF4444).copy(alpha = 0.1f))
                        .clickable { onDeleteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                }
            }

            // Small sub-totals
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (curr in currencyGroups.keys.sorted()) {
                    val cOwed = owedByThemMap[curr] ?: 0.0
                    val cPaid = paymentByThemMap[curr] ?: 0.0
                    val cOwedTo = owedToThemMap[curr] ?: 0.0
                    val cPaidTo = paymentToThemMap[curr] ?: 0.0
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("ديون:", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = formatCurrency(cOwed, curr),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("سداد:", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = formatCurrency(cPaid + cOwedTo, curr),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }
                }
            }
        }
    }
}
