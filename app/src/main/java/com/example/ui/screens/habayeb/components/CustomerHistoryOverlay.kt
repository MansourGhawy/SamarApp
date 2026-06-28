package com.example.ui.screens.habayeb.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    // Intercept back presses to dismiss this full-screen overlay beautifully
    BackHandler(onBack = onDismiss)

    val customers by viewModel.habayebCustomersState.collectAsStateWithLifecycle()
    val activeCustomer = customers.find { it.id == customer.id } ?: customer

    val transactions by viewModel.habayebTransactionsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Search state
    var txSearchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Customer transactions list filtered by client ID
    val allCustomerTxs = remember(transactions, activeCustomer) {
        transactions.filter { it.customerId == activeCustomer.id }.sortedByDescending { it.timestamp }
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

    // Selected Transaction for bottom sheet actions
    var selectedTxForActions by remember { mutableStateOf<HabayebTransaction?>(null) }

    // Dialogs States
    var editingTransactionForDialog by remember { mutableStateOf<HabayebTransaction?>(null) }
    var showAddTransactionDialogFromHistory by remember { mutableStateOf<HabayebCustomer?>(null) }
    var defaultTransactionTypeFromHistory by remember { mutableStateOf("OWED_BY_THEM") }

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
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var showShareMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(64.dp)
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
                                    .height(44.dp)
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
                                                text = "بحث عن معاملة...",
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
                            // Standard Mode Header details
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Micro Avatar
                                val avatarColor = getInitialColor(activeCustomer.name)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(avatarColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = activeCustomer.name.trim().firstOrNull()?.toString()?.uppercase() ?: "؟",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = avatarColor
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = activeCustomer.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = activeCustomer.phone.ifEmpty { "لا يوجد هاتف مسجل" },
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Actions
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "بحث",
                                    tint = Color(0xFF1E293B)
                                )
                            }

                            Box {
                                IconButton(onClick = { showShareMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "خيارات المشاركة",
                                        tint = Color(0xFF1E293B)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showShareMenu,
                                    onDismissRequest = { showShareMenu = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("مشاركة كشف PDF") },
                                        onClick = {
                                            showShareMenu = false
                                            PdfReportGenerator.generateAndHandleCustomerPdfReport(
                                                context = context,
                                                customer = activeCustomer,
                                                netDebt = netDebt,
                                                transactions = allCustomerTxs,
                                                action = "SHARE"
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = activeThemeColor) }
                                    )
                                    if (activeCustomer.phone.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text("إرسال عبر واتساب") },
                                            onClick = {
                                                showShareMenu = false
                                                triggerWhatsAppStatement(activeCustomer, netDebt)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF16A34A)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("إرسال رسالة نصية SMS") },
                                            onClick = {
                                                showShareMenu = false
                                                triggerSmsStatement(activeCustomer, netDebt)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Sms, contentDescription = null, tint = Color(0xFF0F766E)) }
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { showEditNameDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل الحساب",
                                    tint = Color(0xFF1E293B)
                                )
                            }

                            IconButton(onClick = { confirmDeleteCust = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف الحساب",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }

                // 2. FINANCIAL STATUS SUMMARY CARD (COMPACT AND SPACIOUS)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("مجموع ديونك (له)", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = formatCurrency(owedByThem, currencySymbol),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("مجموع سدادك (عليه)", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = formatCurrency(paymentByThem + owedToThem, currencySymbol),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
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

                            Column(horizontalAlignment = Alignment.End) {
                                Text("الرصيد الإجمالي", fontSize = 11.sp, color = textBalanceColor, fontWeight = FontWeight.Bold)
                                Text(
                                    text = formatCurrency(kotlin.math.abs(netDebt), currencySymbol),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textBalanceColor
                                )
                                Text(
                                    text = stateLabel,
                                    fontSize = 11.sp,
                                    color = textBalanceColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

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
                            modifier = Modifier.weight(1.0f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Right
                        )
                        Text(
                            text = "التفاصيل",
                            modifier = Modifier.weight(1.4f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "المبلغ",
                            modifier = Modifier.weight(1.2f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "الرصيد",
                            modifier = Modifier.weight(1.0f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Left
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
                        verticalArrangement = Arrangement.Top
                    ) {
                        items(displayedTxs, key = { it.id }) { tx ->
                            val formattedDate = remember(tx.timestamp) {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                                sdf.format(Date(tx.timestamp * 1000))
                            }
                            val formattedTime = remember(tx.timestamp) {
                                val sdf = SimpleDateFormat("hh:mm a", Locale("ar"))
                                sdf.format(Date(tx.timestamp * 1000))
                            }

                            val isPositive = tx.type == "PAYMENT_BY_THEM" || tx.type == "OWED_TO_THEM"
                            val indicatorColor = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626)
                            val rowBgColor = if (selectedTxForActions?.id == tx.id) Color(0xFF0F766E).copy(alpha = 0.08f) else Color.White

                            // Historical running balance at this exact transaction
                            val currentHistBalance = runningBalances[tx.id] ?: 0.0
                            val formattedHistBal = try { String.format(Locale.ENGLISH, "%,.0f", currentHistBalance) } catch (e: Exception) { currentHistBalance.toString() }
                            val formattedAmount = try { String.format(Locale.ENGLISH, "%,.0f", tx.amount) } catch (e: Exception) { tx.amount.toString() }

                            // Clean table-row layout
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable {
                                        selectedTxForActions = if (selectedTxForActions?.id == tx.id) null else tx
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = rowBgColor),
                                border = if (selectedTxForActions?.id == tx.id) BorderStroke(1.dp, activeThemeColor) else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Date/Time (Rightmost)
                                    Column(
                                        modifier = Modifier.weight(1.0f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = formattedDate,
                                            fontSize = 11.sp,
                                            color = Color(0xFF334155),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = formattedTime,
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    // 2. Details (Middle-Right)
                                    Text(
                                        text = tx.description.ifEmpty { if (isPositive) "سداد" else "دين" },
                                        modifier = Modifier.weight(1.4f),
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E293B),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // 3. Amount with colorful indicator arrow (Middle-Left)
                                    Row(
                                        modifier = Modifier.weight(1.2f),
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
                                        modifier = Modifier.weight(1.0f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentHistBalance > 0) Color(0xFFDC2626) else if (currentHistBalance < 0) Color(0xFF16A34A) else Color(0xFF334155),
                                        textAlign = TextAlign.Left
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. STUNNING BOTTOM TRANSACTION ACTIONS PANEL (SAVES SCREEN REAL ESTATE, HIGH ACCESSIBILITY)
            if (selectedTxForActions != null) {
                val currentTx = selectedTxForActions!!
                val isPositive = currentTx.type == "PAYMENT_BY_THEM" || currentTx.type == "OWED_TO_THEM"
                val typeName = when (currentTx.type) {
                    "OWED_BY_THEM" -> "دين عليه"
                    "PAYMENT_BY_THEM" -> "سداد منه"
                    "OWED_TO_THEM" -> "دين له"
                    "PAYMENT_TO_THEM" -> "سداد له"
                    else -> "حركة حساب"
                }
                val indicatorColor = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 76.dp, start = 8.dp, end = 8.dp) // Offset above standard floating adding buttons
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Row Header: Status indicator and text description
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(indicatorColor)
                                    )
                                    Text(
                                        text = "$typeName: ${formatCurrency(currentTx.amount, currencySymbol)} ${if (currentTx.description.isNotEmpty()) "- " + currentTx.description else ""}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    )
                                }

                                Text(
                                    text = "إخفاء",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable { selectedTxForActions = null }
                                )
                            }

                            Divider(color = Color.White.copy(alpha = 0.1f), thickness = 0.8.dp)

                            // Control buttons row: Edit, Delete, Send SMS, Send WhatsApp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. SMS Alert button
                                Button(
                                    onClick = { triggerSingleTxSms(currentTx, activeCustomer) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp).weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Sms, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        Text("إشعار SMS", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // 2. WhatsApp Alert button
                                Button(
                                    onClick = { triggerSingleTxWhatsApp(currentTx, activeCustomer) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp).weight(1.1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        Text("إشعار WhatsApp", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // 3. Edit transaction
                                Button(
                                    onClick = {
                                        editingTransactionForDialog = currentTx
                                        defaultTransactionTypeFromHistory = currentTx.type
                                        showAddTransactionDialogFromHistory = activeCustomer
                                        selectedTxForActions = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp).weight(0.8f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        Text("تعديل", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // 4. Delete transaction
                                Button(
                                    onClick = {
                                        viewModel.deleteHabayebTransaction(currentTx.id)
                                        Toast.makeText(context, "تم حذف المعاملة بنجاح", Toast.LENGTH_SHORT).show()
                                        selectedTxForActions = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp).weight(0.8f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        Text("حذف", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. DELUXE PERSISTENT FLOATING ADDING ACTION BUTTON
            FloatingActionButton(
                onClick = {
                    val defaultType = if (netDebt >= 0.0) "OWED_BY_THEM" else "OWED_TO_THEM"
                    onAddTransaction(activeCustomer, defaultType)
                },
                containerColor = activeThemeColor,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart) // BottomStart = Right side in RTL
                    .padding(20.dp)
                    .size(56.dp),
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
