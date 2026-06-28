package com.example.ui.screens.habayeb.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.HabayebTransaction
import com.example.data.serialization.PdfReportGenerator
import com.example.ui.helper.formatCurrency
import com.example.ui.helper.getInitialColor
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerHistoryOverlay(
    customer: HabayebCustomer,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onAddTransaction: (HabayebCustomer, String) -> Unit,
    activeThemeColor: Color,
    activeSubColor: Color,
    currencySymbol: String
) {
    val customers by viewModel.habayebCustomersState.collectAsStateWithLifecycle()
    val activeCustomer = customers.find { it.id == customer.id } ?: customer

    val transactions by viewModel.habayebTransactionsState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val customerTxs = remember(transactions, customer) {
        transactions.filter { it.customerId == customer.id }.sortedByDescending { it.timestamp }
    }

    val owedByThem = customerTxs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
    val paymentByThem = customerTxs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
    val owedToThem = customerTxs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
    val paymentToThem = customerTxs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
    val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)

    var editingTransactionForDialog by remember { mutableStateOf<HabayebTransaction?>(null) }
    var showAddTransactionDialogFromHistory by remember { mutableStateOf<HabayebCustomer?>(null) }
    var defaultTransactionTypeFromHistory by remember { mutableStateOf("OWED_BY_THEM") }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredTxs = remember(customerTxs, searchQuery) {
        customerTxs.filter { it.description.contains(searchQuery, ignoreCase = true) }
    }
    val isPrivacyModeState by viewModel.isPrivacyModeEnabled.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                // States
                var confirmDeleteCust by remember { mutableStateOf(false) }
                var showEditNameDialog by remember { mutableStateOf(false) }
                var editedNameStr by remember(activeCustomer.name) { mutableStateOf(activeCustomer.name) }

                if (confirmDeleteCust) {
                    AlertDialog(
                        onDismissRequest = { confirmDeleteCust = false },
                        title = { Text(stringResource(id = R.string.habayeb_delete_account_title), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                        text = { Text(stringResource(id = R.string.habayeb_delete_account_confirm, activeCustomer.name)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteHabayebCustomer(activeCustomer.id)
                                    Toast.makeText(context, context.getString(R.string.habayeb_toast_delete_success), Toast.LENGTH_SHORT).show()
                                    confirmDeleteCust = false
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                            ) {
                                Text(stringResource(id = R.string.habayeb_delete_yes), color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDeleteCust = false }) {
                                Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                            }
                        }
                    )
                }

                if (showEditNameDialog) {
                    val editNameFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        editNameFocusRequester.requestFocus()
                    }
                    AlertDialog(
                        onDismissRequest = { showEditNameDialog = false },
                        title = {
                            Text(stringResource(id = R.string.habayeb_edit_name_title), fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .imePadding()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                OutlinedTextField(
                                    value = editedNameStr,
                                    onValueChange = { editedNameStr = it },
                                    label = { Text(stringResource(id = R.string.habayeb_account_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().focusRequester(editNameFocusRequester),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = activeThemeColor,
                                        focusedLabelColor = activeThemeColor,
                                        cursorColor = activeThemeColor
                                    )
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (editedNameStr.isNotBlank()) {
                                        viewModel.updateHabayebCustomerName(activeCustomer.id, editedNameStr.trim())
                                    }
                                    showEditNameDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor)
                            ) {
                                Text(stringResource(id = R.string.habayeb_save_edit))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEditNameDialog = false }) {
                                Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                            }
                        }
                    )
                }

                // Ultra-Compact Single Row Header (max-height 56.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Right extreme: Return indicator (Borderless Back Button)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.habayeb_go_back),
                            tint = Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (isSearchActive) {
                        // Thin Inline Search Input (34.dp height)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث في الحركات...", fontSize = 11.sp, color = Color.Gray) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF1F5F9),
                                unfocusedContainerColor = Color(0xFFF1F5F9),
                                focusedBorderColor = activeThemeColor,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = activeThemeColor
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.Gray
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        )
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Search",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Center (Identity & Data)
                        Row(
                            modifier = Modifier
                                .weight(1.5f)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mini Avatar Circle
                            val avatarColor = getInitialColor(activeCustomer.name)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeCustomer.name.trim().firstOrNull()?.toString()?.uppercase() ?: "؟",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = avatarColor
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = activeCustomer.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            editedNameStr = activeCustomer.name
                                            showEditNameDialog = true
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(id = R.string.habayeb_edit_name_desc),
                                            modifier = Modifier.size(12.dp),
                                            tint = activeThemeColor
                                        )
                                    }
                                }

                                if (activeCustomer.phone.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = activeCustomer.phone,
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                        // Phone Call Button
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(activeThemeColor.copy(alpha = 0.12f))
                                                .clickable {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${activeCustomer.phone}"))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "لا يمكن الاتصال", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Phone,
                                                contentDescription = "Call",
                                                tint = activeThemeColor,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                        // WhatsApp Reminder Button
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(activeThemeColor.copy(alpha = 0.12f))
                                                .clickable {
                                                    try {
                                                        val reminderMsg = "السلام عليكم ورحمة الله وبركاته، أخي العزيز ${activeCustomer.name}. نود تذكيركم بأن الرصيد المتبقي في حسابكم لدينا هو: ${formatCurrency(kotlin.math.abs(netDebt), currencySymbol)}."
                                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                                            data = Uri.parse("https://api.whatsapp.com/send?phone=${activeCustomer.phone}&text=${Uri.encode(reminderMsg)}")
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "لا يمكن فتح واتساب", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "WhatsApp",
                                                tint = activeThemeColor,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = stringResource(id = R.string.habayeb_no_phone),
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        // Left Center (Financial Status)
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            val textBalanceColor = when {
                                netDebt > 0.0 -> Color(0xFFDC2626) // Red
                                netDebt < 0.0 -> Color(0xFF16A34A) // Green
                                else -> Color.DarkGray
                            }
                            val balanceLabelText = when {
                                netDebt > 0.0 -> "إجمالي الصافي لك"
                                netDebt < 0.0 -> "إجمالي المبلغ عليك"
                                else -> "الرصيد متساوي"
                            }

                            Text(
                                text = balanceLabelText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = textBalanceColor
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isPrivacyModeState) "****" else formatCurrency(kotlin.math.abs(netDebt), currencySymbol),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textBalanceColor
                                )
                                IconButton(
                                    onClick = { viewModel.togglePrivacyMode() },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPrivacyModeState) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle privacy",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // Left extreme: Delete Customer (Soft Red without large background)
                        IconButton(
                            onClick = { confirmDeleteCust = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(id = R.string.habayeb_delete_account_title),
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Action strip: Share, Print, Search
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search toggle icon button next to PDF Export
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Toggle Search",
                            tint = activeThemeColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 1. PDF Export button
                    AssistChip(
                        onClick = {
                            PdfReportGenerator.generateAndHandleCustomerPdfReport(
                                context = context,
                                customer = activeCustomer,
                                netDebt = netDebt,
                                transactions = customerTxs,
                                action = "SHARE"
                            )
                        },
                        label = { Text(stringResource(id = R.string.habayeb_export_pdf), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = activeThemeColor.copy(alpha = 0.08f),
                            labelColor = activeThemeColor,
                            leadingIconContentColor = activeThemeColor
                        ),
                        border = BorderStroke(1.dp, activeThemeColor.copy(alpha = 0.2f)),
                        modifier = Modifier.height(36.dp)
                    )

                    // 2. Share text button
                    AssistChip(
                        onClick = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                val textBody = "كشف حساب ${activeCustomer.name}:\n" + customerTxs.joinToString("\n") { tx ->
                                    val txDate = try {
                                        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
                                        sdf.format(Date(tx.timestamp * 1000))
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    "$txDate: ${formatCurrency(tx.amount, currencySymbol)}"
                                }
                                putExtra(android.content.Intent.EXTRA_TEXT, textBody)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "مشاركة نصية سريعة"))
                        },
                        label = { Text(stringResource(id = R.string.habayeb_share_text), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.Gray.copy(alpha = 0.08f),
                            labelColor = Color.DarkGray,
                            leadingIconContentColor = Color.DarkGray
                        ),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
                        modifier = Modifier.height(36.dp)
                    )
                }

                // Dynamic previous transactions list
                if (filteredTxs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (searchQuery.isNotEmpty()) "لا توجد حركات تطابق البحث" else stringResource(id = R.string.habayeb_no_txs), fontSize = 12.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredTxs, key = { it.id }) { tx ->
                            val formattedDate = remember(tx.timestamp) {
                                val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.ENGLISH)
                                val formatted = sdf.format(Date(tx.timestamp * 1000))
                                formatted.replace("AM", "ص").replace("PM", "م")
                                    .replace("am", "ص").replace("pm", "م")
                            }

                            val isPositive = tx.type == "PAYMENT_BY_THEM" || tx.type == "OWED_TO_THEM"
                            val indicatorColor = if (isPositive) Color(0xFF16A34A) else Color(0xFFEF4444)
                            val isDebt = tx.type == "OWED_BY_THEM" || tx.type == "OWED_TO_THEM"
                            val sign = if (isDebt) "+" else "-"

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. التاريخ والوقت (أقصى اليمين): weight(1.2f)
                                    Column(
                                        modifier = Modifier.weight(1.2f),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val parts = formattedDate.split(" ")
                                        val dateStr = parts.getOrNull(0) ?: ""
                                        val timeStr = parts.drop(1).joinToString(" ")

                                        Text(
                                            text = dateStr,
                                            fontSize = 9.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (timeStr.isNotEmpty()) {
                                            Text(
                                                text = timeStr,
                                                fontSize = 9.sp,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // 2. التفاصيل والبيان (الوسط): weight(1.5f)
                                    Column(
                                        modifier = Modifier.weight(1.5f),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val descriptionText = if (tx.description.isNotEmpty()) {
                                            tx.description
                                        } else {
                                            when (tx.type) {
                                                "OWED_BY_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_owed_by)
                                                "PAYMENT_BY_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_payment_by)
                                                "OWED_TO_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_owed_to)
                                                "PAYMENT_TO_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_payment_to)
                                                else -> stringResource(id = R.string.habayeb_pdf_tx_generic)
                                            }
                                        }
                                        Text(
                                            text = descriptionText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF1E293B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // 3. المبلغ (الوسط الأيسر): weight(1.0f)
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "$sign${formatCurrency(tx.amount, currencySymbol)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = indicatorColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // 4. الإجراءات السريعة (أقصى اليسار)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                editingTransactionForDialog = tx
                                                defaultTransactionTypeFromHistory = tx.type
                                                showAddTransactionDialogFromHistory = activeCustomer
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = stringResource(id = R.string.detail_btn_edit),
                                                tint = Color.Gray.copy(alpha = 0.4f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.deleteHabayebTransaction(tx.id)
                                                Toast.makeText(context, context.getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.detail_btn_delete),
                                                tint = Color(0xFFEF4444).copy(alpha = 0.4f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            // Beautiful custom floating button at the bottom extreme
            FloatingActionButton(
                onClick = {
                    val defaultType = if (netDebt >= 0.0) "OWED_BY_THEM" else "OWED_TO_THEM"
                    onAddTransaction(activeCustomer, defaultType)
                },
                containerColor = activeThemeColor,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 90.dp, end = 20.dp, start = 20.dp), // Elevated to float over bottom Pill Navigation Dock
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(id = R.string.habayeb_add_tx_button), modifier = Modifier.size(18.dp))
                    Text(stringResource(id = R.string.habayeb_add_tx_button).replace(" ➕",""), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

                }
        }
        }
    }

    // Secondary editing dialog if triggered inside customer details lists
    if (showAddTransactionDialogFromHistory != null) {
        AddTransactionPopup(
            customer = showAddTransactionDialogFromHistory!!,
            viewModel = viewModel,
            initialSelectedType = defaultTransactionTypeFromHistory,
            editingTransaction = editingTransactionForDialog,
            onDismiss = {
                showAddTransactionDialogFromHistory = null
                editingTransactionForDialog = null
            },
            activeThemeColor = activeThemeColor,
            activeSubColor = activeSubColor
        )
    }
}
