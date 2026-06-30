package com.example.ui.screens.habayeb.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.HabayebTransaction
import com.example.data.serialization.PdfReportGenerator
import com.example.ui.helper.formatCurrency
import com.example.ui.helper.getInitialColor
import com.example.ui.screens.habayeb.utils.HabayebRecurringManager
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CustomerHistoryOverlay(
    customer: HabayebCustomer,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onAddTransaction: (HabayebCustomer, String) -> Unit,
    activeThemeColor: Color,
    activeSubColor: Color,
    currencySymbol: String,
    contentPadding: PaddingValues = PaddingValues()
) {
    val customers by viewModel.habayebCustomersState.collectAsStateWithLifecycle()
    val activeCustomer = customers.find { it.id == customer.id } ?: customer

    val transactions by viewModel.habayebTransactionsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var isPdfExporting by remember { mutableStateOf(false) }

    // Search state
    var txSearchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Customer transactions list filtered by client ID
    val allCustomerTxs = remember(transactions, activeCustomer) {
        transactions.filter { it.customerId == activeCustomer.id }.sortedBy { it.timestamp }
    }

    // Filtered by Search query (supporting description and amount filtering)
    val displayedTxs = remember(allCustomerTxs, txSearchQuery) {
        if (txSearchQuery.isBlank()) {
            allCustomerTxs
        } else {
            allCustomerTxs.filter { tx ->
                tx.description.contains(txSearchQuery, ignoreCase = true) ||
                tx.amount.toString().contains(txSearchQuery) ||
                (if (tx.type == "OWED_BY_THEM") "دين" else "سداد").contains(txSearchQuery)
            }
        }
    }

    // Calculations
    val owedByThem = allCustomerTxs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
    val paymentByThem = allCustomerTxs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
    val owedToThem = allCustomerTxs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
    val paymentToThem = allCustomerTxs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
    val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)

    // Calculate sequential running balances (chronological order)
    val runningBalances = remember(allCustomerTxs) {
        val chronological = allCustomerTxs.sortedBy { it.timestamp }
        val balancesMap = mutableMapOf<String, Double>()
        var currentBal = 0.0
        for (tx in chronological) {
            when (tx.type) {
                "OWED_BY_THEM" -> currentBal += tx.amount
                "PAYMENT_BY_THEM" -> currentBal -= tx.amount
                "OWED_TO_THEM" -> currentBal -= tx.amount
                "PAYMENT_TO_THEM" -> currentBal += tx.amount
            }
            balancesMap[tx.id] = currentBal
        }
        balancesMap
    }

    // Calculate sequential sequence numbers (chronological order)
    val txSequenceNumbers = remember(allCustomerTxs) {
        val chronological = allCustomerTxs.sortedBy { it.timestamp }
        chronological.mapIndexed { idx, tx -> tx.id to (idx + 1) }.toMap()
    }

    // Dialogs States
    var editingTransactionForDialog by remember { mutableStateOf<HabayebTransaction?>(null) }
    var showAddTransactionDialogFromHistory by remember { mutableStateOf<HabayebCustomer?>(null) }
    var defaultTransactionTypeFromHistory by remember { mutableStateOf("OWED_BY_THEM") }

    var transactionForOptionsDialog by remember { mutableStateOf<HabayebTransaction?>(null) }
    var transactionForAutoRepeatDialog by remember { mutableStateOf<HabayebTransaction?>(null) }

    var isTxMultiSelectActive by remember { mutableStateOf(false) }
    val selectedTxIds = remember { mutableStateListOf<String>() }
    var showDeleteBulkTxConfirmDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (isTxMultiSelectActive) {
            isTxMultiSelectActive = false
            selectedTxIds.clear()
        } else if (isSearchActive) {
            isSearchActive = false
            txSearchQuery = ""
        } else {
            onDismiss()
        }
    }

    var refreshRecurringTrigger by remember { mutableStateOf(0) }
    val activeRecurringTxIds = remember(activeCustomer.id, refreshRecurringTrigger, allCustomerTxs) {
        HabayebRecurringManager.getAllConfigs(context)
            .filter { it.isActive && it.customerId == activeCustomer.id }
            .map { it.originalTxId }
            .toSet()
    }

    LaunchedEffect(activeCustomer.id) {
        HabayebRecurringManager.checkAndExecuteRecurring(context, viewModel) { count ->
            Toast.makeText(context, "تم تسجيل عدد $count معاملات مكررة تلقائياً لحساب ${activeCustomer.name} بنجاح! 🌸", Toast.LENGTH_LONG).show()
        }
    }

    var confirmDeleteCust by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editedNameStr by remember(activeCustomer.name) { mutableStateOf(activeCustomer.name) }
    var editedPhoneStr by remember(activeCustomer.phone) { mutableStateOf(activeCustomer.phone) }

    // Dialogs code
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(id = R.string.habayeb_delete_yes), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteCust = false }) {
                    Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
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
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editedNameStr,
                        onValueChange = { editedNameStr = it },
                        label = { Text(stringResource(id = R.string.habayeb_account_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(editNameFocusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor
                        )
                    )

                    OutlinedTextField(
                        value = editedPhoneStr,
                        onValueChange = { editedPhoneStr = it },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                            viewModel.updateHabayebCustomer(
                                activeCustomer.copy(
                                    name = editedNameStr.trim(),
                                    phone = editedPhoneStr.trim()
                                )
                            )
                            Toast.makeText(context, "تم تحديث البيانات بنجاح", Toast.LENGTH_SHORT).show()
                        }
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(id = R.string.habayeb_save_edit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Helper functions for sending SMS statement or transaction SMS/WhatsApp
    fun triggerSmsStatement(customer: HabayebCustomer, debt: Double) {
        val debtStatus = when {
            debt > 0.0 -> "عليكم مبلغ وقدره"
            debt < 0.0 -> "لكم مبلغ وقدره"
            else -> "رصيدكم متعادل"
        }
        val amt = formatCurrency(kotlin.math.abs(debt), currencySymbol)
        val body = "كشف حساب من ميزان الدار لـ ${customer.name}:\n" +
                "الحالة الحالية: $debtStatus $amt.\n" +
                "شكراً لتعاملكم الراقي معنا."
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${customer.phone}")
                putExtra("sms_body", body)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(shareIntent, "إرسال كشف حساب"))
        }
    }

    fun triggerWhatsAppStatement(customer: HabayebCustomer, debt: Double) {
        val debtStatus = when {
            debt > 0.0 -> "عليكم مبلغ وقدره"
            debt < 0.0 -> "لكم مبلغ وقدره"
            else -> "رصيدكم متعادل"
        }
        val amt = formatCurrency(kotlin.math.abs(debt), currencySymbol)
        val body = "كشف حساب من ميزان الدار لـ ${customer.name}:\n" +
                "الحالة الحالية: $debtStatus $amt.\n" +
                "شكراً لتعاملكم الراقي معنا."
        try {
            val waUrl = "https://wa.me/${customer.phone.replace("+", "").replace(" ", "")}?text=${Uri.encode(body)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(shareIntent, "إرسال عبر واتساب"))
        }
    }

    fun triggerSingleTxSms(tx: HabayebTransaction, customer: HabayebCustomer) {
        val txTypeAr = when (tx.type) {
            "OWED_BY_THEM" -> "دين جديد عليكم"
            "PAYMENT_BY_THEM" -> "تم استلام سداد منكم بقيمة"
            "OWED_TO_THEM" -> "دين جديد لكم"
            "PAYMENT_TO_THEM" -> "تم دفع سداد لكم بقيمة"
            else -> "حركة حساب"
        }
        val dateStr = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar")).format(Date(tx.timestamp * 1000))
        val body = "إشعار حركة حساب - ميزان الدار:\n" +
                "العميل: ${customer.name}\n" +
                "النوع: $txTypeAr\n" +
                "المبلغ: ${formatCurrency(tx.amount, currencySymbol)}\n" +
                "التفاصيل: ${tx.description.ifEmpty { "لا يوجد ملاحظات" }}\n" +
                "التاريخ: $dateStr\n" +
                "رصيدكم الإجمالي الحالي لدينا: ${formatCurrency(kotlin.math.abs(netDebt), currencySymbol)} (${if (netDebt > 0) "عليكم" else if (netDebt < 0) "لكم" else "متعادل"})"

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${customer.phone}")
                putExtra("sms_body", body)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(shareIntent, "إرسال إشعار"))
        }
    }

    fun triggerSingleTxWhatsApp(tx: HabayebTransaction, customer: HabayebCustomer) {
        val txTypeAr = when (tx.type) {
            "OWED_BY_THEM" -> "دين جديد عليكم"
            "PAYMENT_BY_THEM" -> "تم استلام سداد منكم بقيمة"
            "OWED_TO_THEM" -> "دين جديد لكم"
            "PAYMENT_TO_THEM" -> "تم دفع سداد لكم بقيمة"
            else -> "حركة حساب"
        }
        val dateStr = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar")).format(Date(tx.timestamp * 1000))
        val body = "*إشعار حركة حساب - ميزان الدار:*\n" +
                "👤 *العميل:* ${customer.name}\n" +
                "📌 *النوع:* $txTypeAr\n" +
                "💰 *المبلغ:* ${formatCurrency(tx.amount, currencySymbol)}\n" +
                "📝 *التفاصيل:* ${tx.description.ifEmpty { "لا يوجد ملاحظات" }}\n" +
                "📅 *التاريخ:* $dateStr\n" +
                "💼 *الرصيد الإجمالي الحالي:* ${formatCurrency(kotlin.math.abs(netDebt), currencySymbol)} (${if (netDebt > 0) "عليكم" else if (netDebt < 0) "لكم" else "متعادل"})"

        try {
            val waUrl = "https://wa.me/${customer.phone.replace("+", "").replace(" ", "")}?text=${Uri.encode(body)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(shareIntent, "إرسال عبر واتساب"))
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 1. FULL SCREEN CUSTOM APP BAR WITH INTEGRATED SEARCH
                Surface(
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var showShareMenu by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color(0xFF1E293B)
                                )
                            }

                            if (isSearchActive) {
                                // Search Mode View
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "بحث",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = txSearchQuery,
                                        onValueChange = { txSearchQuery = it },
                                        textStyle = TextStyle(
                                            color = Color(0xFF1E293B),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(activeThemeColor),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (txSearchQuery.isEmpty()) {
                                                Text(
                                                    text = "بحث عن حركة...",
                                                    color = Color.Gray.copy(alpha = 0.8f),
                                                    fontSize = 13.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (txSearchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { txSearchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "مسح",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    isSearchActive = false
                                    txSearchQuery = ""
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إلغاء البحث",
                                        tint = Color(0xFF1E293B)
                                    )
                                }
                            } else {
                                // Standard Mode: Centered / Start-aligned account full name written by the user
                                Text(
                                    text = "تفاصيل الحساب",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                // Action icons: Search
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "بحث",
                                            tint = Color(0xFF475569),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!isSearchActive) {
                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                            // Compact Account Details & Financial Summary Card
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

                                    val textBalanceColor = when {
                                        netDebt > 0.0 -> Color(0xFFDC2626) // Red (They owe you)
                                        netDebt < 0.0 -> Color(0xFF16A34A) // Green (You owe them)
                                        else -> Color(0xFF334155)
                                    }
                                    val stateLabel = when {
                                        netDebt > 0.0 -> "مطلوب منه"
                                        netDebt < 0.0 -> "مطلوب له"
                                        else -> "متعادل"
                                    }

                                    // Compact colored balance badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(textBalanceColor.copy(alpha = 0.08f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = formatCurrency(netDebt, currencySymbol),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = textBalanceColor
                                            )
                                            Text(
                                                text = stateLabel,
                                                fontSize = 8.sp,
                                                color = textBalanceColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
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
                                                .clickable(enabled = !isPdfExporting) {
                                                     isPdfExporting = true
                                                     PdfReportGenerator.generateAndHandleCustomerPdfReportAsync(
                                                         context,
                                                         coroutineScope,
                                                         activeCustomer,
                                                         netDebt,
                                                         allCustomerTxs,
                                                         "SHARE",
                                                         onFinished = { isPdfExporting = false }
                                                     )
                                                 },
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
                                                .clickable(enabled = isPhoneAvailable) { triggerWhatsAppStatement(activeCustomer, netDebt) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = if (isPhoneAvailable) Color(0xFF16A34A) else Color.Gray.copy(alpha = 0.35f), modifier = Modifier.size(14.dp))
                                        }

                                        // Edit
                                        Box(
                                            modifier = iconModifier
                                                .background(Color(0xFFF1F5F9))
                                                .clickable { showEditNameDialog = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                        }

                                        // Delete
                                        Box(
                                            modifier = iconModifier
                                                .background(Color(0xFFEF4444).copy(alpha = 0.1f))
                                                .clickable { confirmDeleteCust = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                        }
                                    }

                                    // Small sub-totals
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
                                                text = formatCurrency(owedByThem, currencySymbol),
                                                fontSize = 11.sp,
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
                                                text = formatCurrency(paymentByThem + owedToThem, currencySymbol),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF16A34A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } // Close Column(modifier = Modifier.fillMaxWidth())
                } // Close Surface

                // 3. TABLE GRID COLUMN HEADER STRIP
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "التاريخ",
                            modifier = Modifier.weight(1.1f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "التفاصيل",
                            modifier = Modifier.weight(1.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "المبلغ",
                            modifier = Modifier.weight(1.0f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "المتبقي",
                            modifier = Modifier.weight(0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 4. THE HIGH-DENSITY HIGH-FIDELITY TRANSACTION LIST
                if (displayedTxs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (txSearchQuery.isEmpty()) "لا توجد أي معاملات مسجلة للحساب" else "لا توجد معاملات تطابق البحث",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Top,
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = contentPadding.calculateBottomPadding() + 80.dp
                        )
                    ) {
                        items(displayedTxs, key = { it.id }) { tx ->
                            val formattedDate = remember(tx.timestamp) {
                                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
                                sdf.format(Date(tx.timestamp * 1000))
                            }

                            val formattedTime = remember(tx.timestamp) {
                                val sdf = SimpleDateFormat("hh:mm a", Locale("ar"))
                                sdf.format(Date(tx.timestamp * 1000))
                            }

                            val isPositive = tx.type == "PAYMENT_BY_THEM" || tx.type == "OWED_TO_THEM"
                            val indicatorColor = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626)
                            val isSelected = selectedTxIds.contains(tx.id)
                            val rowBgColor = if (isSelected) activeThemeColor.copy(alpha = 0.08f) else Color.White
                            val borderColor = if (isSelected) activeThemeColor else Color(0xFFE2E8F0)

                            // Historical running balance at this exact transaction
                            val currentHistBalance = runningBalances[tx.id] ?: 0.0
                            val formattedHistBal = try { String.format(Locale.ENGLISH, "%,.0f", currentHistBalance) } catch (e: Exception) { currentHistBalance.toString() }
                            val formattedAmount = try { String.format(Locale.ENGLISH, "%,.0f", tx.amount) } catch (e: Exception) { tx.amount.toString() }

                            val hasActiveRecurring = tx.id in activeRecurringTxIds
                            val txSeqNo = txSequenceNumbers[tx.id] ?: 0
                            val parentTxSeq = remember(tx.linkedMainTxId, txSequenceNumbers) {
                                if (tx.linkedMainTxId != null) {
                                    txSequenceNumbers[tx.linkedMainTxId]
                                } else null
                            }

                            // Clean table-row layout
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 3.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (isTxMultiSelectActive) {
                                                if (isSelected) selectedTxIds.remove(tx.id)
                                                else selectedTxIds.add(tx.id)
                                                
                                                if (selectedTxIds.isEmpty()) {
                                                    isTxMultiSelectActive = false
                                                }
                                            } else {
                                                transactionForOptionsDialog = tx
                                            }
                                        },
                                        onLongClick = {
                                            if (!isTxMultiSelectActive) {
                                                isTxMultiSelectActive = true
                                                selectedTxIds.add(tx.id)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = rowBgColor),
                                border = BorderStroke(1.dp, borderColor),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Professional indicator bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(4.dp)
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        indicatorColor,
                                                        indicatorColor.copy(alpha = 0.6f)
                                                    )
                                                )
                                            )
                                    )
                                    
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. Date/Time (Rightmost)
                                        Column(
                                            modifier = Modifier.weight(1.1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                // Chronological Sequence Badge
                                                Text(
                                                    text = "#$txSeqNo",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = activeThemeColor,
                                                    modifier = Modifier
                                                        .background(activeThemeColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                                if (hasActiveRecurring) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .background(activeThemeColor.copy(alpha = 0.12f), CircleShape)
                                                            .clickable {
                                                                // Clicking the clock button directly opens scheduler config
                                                                transactionForAutoRepeatDialog = tx
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Schedule,
                                                            contentDescription = "تعديل التكرار التلقائي",
                                                            tint = activeThemeColor,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(1.dp))

                                            Text(
                                                text = formattedDate,
                                                fontSize = 10.sp,
                                                color = Color(0xFF334155),
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }

                                        // 2. Details (Middle-Right)
                                        Column(
                                            modifier = Modifier.weight(1.7f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            val typeStr = when (tx.type) {
                                                "OWED_BY_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_owed_by) // دين عليه
                                                "PAYMENT_BY_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_payment_by) // استلام دفعة
                                                "OWED_TO_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_owed_to) // دين له
                                                "PAYMENT_TO_THEM" -> stringResource(id = R.string.habayeb_pdf_tx_payment_to) // سداد دفعة
                                                else -> stringResource(id = R.string.habayeb_pdf_tx_generic)
                                            }
                                            Text(
                                                text = typeStr,
                                                fontSize = 9.sp,
                                                color = indicatorColor.copy(alpha = 0.8f),
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = tx.description.ifEmpty { "لا يوجد ملاحظات" },
                                                fontSize = 12.sp,
                                                color = Color(0xFF1E293B),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            
                                            // Rich Badges explaining recurring status
                                            if (hasActiveRecurring) {
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                                                        .border(0.5.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "مصدر تكرار مجدول 🔄",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFB45309)
                                                    )
                                                }
                                            } else if (tx.linkedMainTxId != null) {
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                                                        .border(0.5.dp, Color(0xFF3B82F6), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "توليد تلقائي (تابع للمعامله #${parentTxSeq ?: "?"}) ⚙️",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1D4ED8)
                                                    )
                                                }
                                            }
                                        }

                                        // 3. Amount with colorful indicator arrow (Middle-Left)
                                        Row(
                                            modifier = Modifier.weight(1.0f),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = indicatorColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = formattedAmount,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                color = indicatorColor
                                            )
                                        }

                                        // 4. Running Balance (Leftmost)
                                        Text(
                                            text = formattedHistBal,
                                            modifier = Modifier.weight(0.8f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Multi-Select Floating Bar ---
            AnimatedVisibility(
                visible = isTxMultiSelectActive,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding() + 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .shadow(16.dp, RoundedCornerShape(30.dp), spotColor = Color.Black.copy(alpha = 0.1f))
                        .background(Color.White, RoundedCornerShape(30.dp))
                        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(30.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Cancel Button
                    IconButton(
                        onClick = {
                            isTxMultiSelectActive = false
                            selectedTxIds.clear()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إلغاء التحديد",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Selection Info & Select All
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                val allSelected = displayedTxs.isNotEmpty() && displayedTxs.all { selectedTxIds.contains(it.id) }
                                if (allSelected) {
                                    selectedTxIds.clear()
                                } else {
                                    displayedTxs.forEach { if (!selectedTxIds.contains(it.id)) selectedTxIds.add(it.id) }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val allSelected = displayedTxs.isNotEmpty() && displayedTxs.all { selectedTxIds.contains(it.id) }
                        Icon(
                            imageVector = if (allSelected) Icons.Default.Check else Icons.Default.List,
                            contentDescription = "تحديد الكل",
                            tint = if (allSelected) activeThemeColor else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (allSelected) "تم تحديد الكل" else "${selectedTxIds.size} محدد",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Delete Button
                    IconButton(
                        onClick = {
                            if (selectedTxIds.isNotEmpty()) {
                                showDeleteBulkTxConfirmDialog = true
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFEF2F2), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف المحدد",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 6. DELUXE PERSISTENT FLOATING ADDING ACTION BUTTON
            AnimatedVisibility(
                visible = !isTxMultiSelectActive,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart) // BottomStart = Right side in RTL
                    .padding(
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        start = 20.dp
                    )
            ) {
                FloatingActionButton(
                    onClick = {
                        val defaultType = if (netDebt >= 0.0) "OWED_BY_THEM" else "OWED_TO_THEM"
                        onAddTransaction(activeCustomer, defaultType)
                    },
                    containerColor = activeThemeColor,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة معاملة",
                        modifier = Modifier.size(28.dp)
                    )
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

    if (transactionForOptionsDialog != null) {
        val isRecurringOriginal = transactionForOptionsDialog!!.id in activeRecurringTxIds
        val optTx = transactionForOptionsDialog!!
        val parentSeq = if (optTx.linkedMainTxId != null) txSequenceNumbers[optTx.linkedMainTxId] else null
        TransactionOptionsDialog(
            transaction = optTx,
            customerName = activeCustomer.name,
            onDismiss = { transactionForOptionsDialog = null },
            onEdit = {
                editingTransactionForDialog = transactionForOptionsDialog
                defaultTransactionTypeFromHistory = transactionForOptionsDialog!!.type
                showAddTransactionDialogFromHistory = activeCustomer
                transactionForOptionsDialog = null
            },
            onDelete = {
                val txId = transactionForOptionsDialog!!.id
                viewModel.deleteHabayebTransaction(txId)
                HabayebRecurringManager.deleteConfigForTransaction(context, txId)
                Toast.makeText(context, "تم حذف المعاملة ونقلها لسلة المهملات بنجاح 🗑️", Toast.LENGTH_SHORT).show()
                refreshRecurringTrigger++
                transactionForOptionsDialog = null
            },
            onAutoRepeat = {
                transactionForAutoRepeatDialog = transactionForOptionsDialog
                transactionForOptionsDialog = null
            },
            activeThemeColor = activeThemeColor,
            activeSubColor = activeSubColor,
            isRecurringOriginal = isRecurringOriginal,
            onDeleteAutoRepeat = {
                val txId = transactionForOptionsDialog!!.id
                HabayebRecurringManager.deleteConfigForTransaction(context, txId)
                Toast.makeText(context, "تم إيقاف وحذف الجدولة التلقائية لهذه المعاملة بنجاح ⚙️", Toast.LENGTH_SHORT).show()
                refreshRecurringTrigger++
                transactionForOptionsDialog = null
            },
            parentSeqNumber = parentSeq
        )
    }

    if (transactionForAutoRepeatDialog != null) {
        RecurringTransactionPopup(
            transaction = transactionForAutoRepeatDialog!!,
            customerName = activeCustomer.name,
            onDismiss = { 
                transactionForAutoRepeatDialog = null 
                refreshRecurringTrigger++
            },
            activeThemeColor = activeThemeColor,
            activeSubColor = activeSubColor
        )
    }

    if (showDeleteBulkTxConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBulkTxConfirmDialog = false },
            title = { Text("تأكيد الحذف", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("هل أنت متأكد أنك تريد حذف ${selectedTxIds.size} معاملات نهائياً؟") },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedTxIds.toList()
                        idsToDelete.forEach { txId ->
                            viewModel.deleteHabayebTransaction(txId)
                            HabayebRecurringManager.deleteConfigForTransaction(context, txId)
                        }
                        Toast.makeText(context, "تم حذف المعاملات المحددة بنجاح", Toast.LENGTH_SHORT).show()
                        selectedTxIds.clear()
                        isTxMultiSelectActive = false
                        refreshRecurringTrigger++
                        showDeleteBulkTxConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBulkTxConfirmDialog = false }) {
                    Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
