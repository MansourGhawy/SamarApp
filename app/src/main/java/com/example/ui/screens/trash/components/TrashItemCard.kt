package com.example.ui.screens.trash.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entities.DeletedItemEntity
import com.example.data.local.entities.HabayebCustomer
import com.example.domain.FormatUtils
import org.json.JSONObject
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashItemCard(
    item: DeletedItemEntity,
    customersList: List<HabayebCustomer>,
    isSelected: Boolean,
    currencySymbol: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Human-friendly table categorization labels
    val typeLabel = when (item.originalTableName) {
        "transactions" -> "حركة مالية (الدفتر)"
        "habayeb_transactions" -> "معاملة دين (العملاء)"
        "fixed_commitments" -> "التزام مالي"
        "habayeb_customers" -> "حساب عميل"
        "habayeb_bundle" -> "حساب عميل بجميع معاملاته"
        "dar_bundle" -> "حزمة الدفتر الذكي"
        else -> item.originalTableName
    }

    val typeIcon: ImageVector = when (item.originalTableName) {
        "transactions" -> Icons.Default.ReceiptLong
        "habayeb_transactions" -> Icons.Default.Handshake
        "fixed_commitments" -> Icons.Default.TaskAlt
        "habayeb_customers" -> Icons.Default.Person
        "habayeb_bundle" -> Icons.Default.People
        "dar_bundle" -> Icons.Default.Inventory
        else -> Icons.Default.InsertDriveFile
    }

    // System theme styling (No Olive green!)
    val systemColor = when (item.sourceSystem) {
        "حبايب" -> MaterialTheme.colorScheme.secondary // Neon Cyan Accent
        else -> MaterialTheme.colorScheme.primary       // Glowing Violet/Purple
    }

    val systemLabel = when (item.sourceSystem) {
        "حبايب" -> "الديون 👥"
        else -> "الدفتر الذكي 🏠"
    }

    var indicatorColor = systemColor
    val jsonObj = remember(item.jsonData) {
        try {
            JSONObject(item.jsonData)
        } catch (e: Exception) {
            null
        }
    }

    // Dynamic extraction of info
    var titleText = stringResource(id = R.string.trash_item_unknown)
    var amountText = ""
    var isExpense = false
    var subText = ""
    var exchangeInfoText = ""

    if (jsonObj != null) {
        when (item.originalTableName) {
            "transactions" -> {
                titleText = jsonObj.optString("description", "").ifEmpty { jsonObj.optString("category", "معاملة") }
                val amountVal = jsonObj.optDouble("amount", 0.0)
                isExpense = jsonObj.optString("type") == "EXPENSE"
                indicatorColor = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                val prefix = if (isExpense) "-" else "+"
                amountText = "$prefix${FormatUtils.formatDoubleCurrency(amountVal, currencySymbol)}"
                subText = "التصنيف: " + jsonObj.optString("category", "")
            }

            "habayeb_transactions" -> {
                val customerId = jsonObj.optString("customerId", "")
                val resolvedName = customersList.find { it.id == customerId }?.name ?: "عميل غير معروف"
                titleText = resolvedName

                val amountVal = jsonObj.optDouble("amount", 0.0)
                val type = jsonObj.optString("type", "")
                
                // Debt types: OWED_BY_THEM / PAYMENT_BY_THEM / OWED_TO_THEM / PAYMENT_TO_THEM
                val isNegative = type == "OWED_BY_THEM" || type == "PAYMENT_TO_THEM"
                isExpense = isNegative
                indicatorColor = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

                val typeAr = when (type) {
                    "OWED_BY_THEM" -> "دين عليه ↗️"
                    "PAYMENT_BY_THEM" -> "سداد منه ↙️"
                    "OWED_TO_THEM" -> "دين له ↙️"
                    "PAYMENT_TO_THEM" -> "سداد له ↗️"
                    else -> type
                }
                subText = "نوع المعاملة: $typeAr" + if (jsonObj.optString("description", "").isNotEmpty()) " | " + jsonObj.optString("description", "") else ""

                val isForeign = jsonObj.optBoolean("is_foreign", false)
                if (isForeign) {
                    val foreignAmount = jsonObj.optDouble("foreign_amount", 0.0)
                    val currencyCode = jsonObj.optString("currency_code", "DEFAULT")
                    amountText = FormatUtils.formatDoubleCurrency(foreignAmount, currencyCode)
                    
                    val isRateCalculated = jsonObj.optBoolean("is_rate_calculated", false)
                    if (isRateCalculated) {
                        val equivalentVal = jsonObj.optDouble("equivalent_amount", 0.0)
                        val rate = jsonObj.optDouble("exchange_rate", 1.0)
                        exchangeInfoText = "يعادل ${FormatUtils.formatDoubleCurrency(equivalentVal, currencySymbol)} (صرف: $rate)"
                    }
                } else {
                    amountText = FormatUtils.formatDoubleCurrency(amountVal, currencySymbol)
                }
            }

            "fixed_commitments" -> {
                titleText = jsonObj.optString("name", "التزام مالي")
                val targetVal = jsonObj.optDouble("targetAmount", 0.0)
                amountText = FormatUtils.formatDoubleCurrency(targetVal, currencySymbol)
                val progressVal = jsonObj.optDouble("currentProgress", 0.0)
                val formattedProgress = try { String.format(Locale.getDefault(), "%.1f", progressVal) } catch (e: Exception) { progressVal.toString() }
                subText = "نسبة الإنجاز الحالي: %$formattedProgress"
                indicatorColor = MaterialTheme.colorScheme.primary
            }

            "habayeb_customers" -> {
                titleText = jsonObj.optString("name", "عميل")
                val phoneStr = jsonObj.optString("phone", "").ifEmpty { "لا يوجد هاتف" }
                amountText = phoneStr
                subText = jsonObj.optString("notes", "").ifEmpty { "لا توجد ملاحظات" }
                indicatorColor = MaterialTheme.colorScheme.secondary
            }

            "habayeb_bundle" -> {
                val cust = jsonObj.optJSONObject("customer")
                if (cust != null) {
                    titleText = cust.optString("name", "عميل")
                    val phoneStr = cust.optString("phone", "").ifEmpty { "لا يوجد هاتف" }
                    amountText = phoneStr
                }
                val txCount = jsonObj.optInt("totalTransactions", 0)
                subText = "حزمة عميل: تشمل $txCount معاملات تابعة"
                indicatorColor = MaterialTheme.colorScheme.secondary
            }

            "dar_bundle" -> {
                val count = jsonObj.optJSONArray("transactions")?.length() ?: 0
                titleText = jsonObj.optString("name", "حزمة ميزان الدار")
                val totalNet = jsonObj.optDouble("totalNet", 0.0)
                amountText = FormatUtils.formatDoubleCurrency(totalNet, currencySymbol)
                subText = "تشمل $count حركات مالية محذوفة معاً"
                indicatorColor = MaterialTheme.colorScheme.primary
            }

            else -> {
                titleText = jsonObj.optString("name", jsonObj.optString("description", "سجل مجهول"))
                indicatorColor = MaterialTheme.colorScheme.outline
            }
        }
    }

    val cardBorder = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 2.dp),
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Type Badge, System Tag, Delete Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Icon + Label Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(systemColor.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = null,
                                tint = systemColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = typeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = systemColor
                            )
                        }
                    }

                    // System Source Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = systemLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    val parsedDate = try {
                        java.text.SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar")).format(Date(item.deletedAt))
                    } catch (e: Exception) {
                        ""
                    }
                    Text(
                        text = parsedDate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Light
                    )
                }
            }

            // Main Content Row: Icon / Indicator Strip, Info Column, Amount Column
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sleek minimalist rounded indicator strip
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(indicatorColor)
                    )

                    // Title and Description
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = titleText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subText.isNotEmpty()) {
                            Text(
                                text = subText,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Amount Section
                if (amountText.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = amountText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.originalTableName == "transactions" || item.originalTableName == "habayeb_transactions") {
                                if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (exchangeInfoText.isNotEmpty()) {
                            Text(
                                text = exchangeInfoText,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Action Buttons (Restore / Permanent Delete) - Only if NOT in multi-selection mode
            if (!isSelected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.trash_delete_permanently),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = onRestore,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color.White
                            )
                            Text(
                                text = "استعادة السجل",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.trash_delete_permanently),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        text = stringResource(id = R.string.trash_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(id = R.string.trash_delete_warning_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.trash_delete_warning_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
