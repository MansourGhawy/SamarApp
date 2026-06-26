package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.*
import com.example.domain.DateUtils
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

data class PdfTransaction(
    val date: String,
    val description: String,
    val amount: Long,
    val type: String, // "OWED_BY_TEM", "PAYMENT_BY_THEM", "OWED_TO_THEM", "PAYMENT_TO_THEM"
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsView(
    viewModel: FinanceViewModel,
    settings: AppSettings,
    currencySymbol: String
) {
    val context = LocalContext.current
    val systemInDarkMode = isSystemInDarkTheme()
    val view = LocalView.current

    // Observe data sources from screen viewmodel
    val transactions by viewModel.transactionsState.collectAsStateWithLifecycle()
    val habayebCustomers by viewModel.habayebCustomersState.collectAsStateWithLifecycle()
    val habayebTransactions by viewModel.habayebTransactionsState.collectAsStateWithLifecycle()
    val totalCash by viewModel.totalCashState.collectAsStateWithLifecycle()
    val owedByThemTotal by viewModel.habayebOwedByThemTotalState.collectAsStateWithLifecycle()
    val owedToThemTotal by viewModel.habayebOwedToThemTotalState.collectAsStateWithLifecycle()

    // Tab control
    var activeReportTab by remember { mutableStateOf(0) } // 0 = ميزان الدار, 1 = ديون حبايب, 2 = المخزن والبضائع

    // Mizan filtering period state
    var selectedPeriod by remember { mutableStateOf("MONTHLY") } // DAILY, WEEKLY, MONTHLY, YEARLY, ALL, CUSTOM
    var customStartDateMs by remember { mutableStateOf<Long?>(null) }
    var customEndDateMs by remember { mutableStateOf<Long?>(null) }
    var customDays by remember { mutableStateOf<Int?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var isChartExpanded by remember { mutableStateOf(true) }

    // Habayeb customer search state
    var habayebSearchQuery by remember { mutableStateOf("") }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = !systemInDarkMode
        }
    }

    // Asynchronous state holders for highly responsive UI rendering
    var filteredTransactions by remember { mutableStateOf(emptyList<TransactionDb>()) }
    var incomes by remember { mutableStateOf(emptyList<TransactionDb>()) }
    var expenses by remember { mutableStateOf(emptyList<TransactionDb>()) }
    var totalIncome by remember { mutableStateOf(BigDecimal.ZERO) }
    var totalExpense by remember { mutableStateOf(BigDecimal.ZERO) }
    var netSavings by remember { mutableStateOf(BigDecimal.ZERO) }
    var categoryTotals by remember { mutableStateOf(emptyList<Pair<String, BigDecimal>>()) }

    var customerDebtProfiles by remember { mutableStateOf(emptyList<Pair<HabayebCustomer, Double>>()) }
    var filteredCustomerProfiles by remember { mutableStateOf(emptyList<Pair<HabayebCustomer, Double>>()) }

    // Tab 0: Asynchronous transaction filtering & categorizations
    LaunchedEffect(transactions, selectedPeriod, customStartDateMs, customEndDateMs, customDays) {
        val computation = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val currentMs = System.currentTimeMillis()
            val filtered = transactions.filter { tx ->
                val txMs = tx.timestamp * 1000L
                when (selectedPeriod) {
                    "DAILY" -> {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = currentMs
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        txMs >= cal.timeInMillis
                    }
                    "WEEKLY" -> {
                        val oneWeekAgo = currentMs - (7L * 24 * 60 * 60 * 1000L)
                        txMs >= oneWeekAgo
                    }
                    "MONTHLY" -> {
                        val oneMonthAgo = currentMs - (30L * 24 * 60 * 60 * 1000L)
                        txMs >= oneMonthAgo
                    }
                    "YEARLY" -> {
                        val oneYearAgo = currentMs - (365L * 24 * 60 * 60 * 1000L)
                        txMs >= oneYearAgo
                    }
                    "CUSTOM" -> {
                        if (customDays != null) {
                            val dMs = currentMs - (customDays!! * 24L * 60 * 60 * 1000L)
                            txMs >= dMs
                        } else if (customStartDateMs != null || customEndDateMs != null) {
                            val start = customStartDateMs ?: 0L
                            val end = customEndDateMs ?: Long.MAX_VALUE
                            txMs in start..end
                        } else true
                    }
                    else -> true // ALL
                }
            }
            val incs = filtered.filter { it.type == "INCOME" }
            val exps = filtered.filter { it.type == "EXPENSE" }

            val totalInc = incs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(BigDecimal.valueOf(tx.amount)) }
            val totalExp = exps.fold(BigDecimal.ZERO) { acc, tx -> acc.add(BigDecimal.valueOf(tx.amount)) }
            val netSav = totalInc.subtract(totalExp)

            val map = mutableMapOf<String, BigDecimal>()
            for (tx in exps) {
                val key = tx.category
                val amount = BigDecimal.valueOf(tx.amount)
                map[key] = (map[key] ?: BigDecimal.ZERO).add(amount)
            }
            val sortedCatTotals = map.toList().sortedByDescending { it.second }

            MizanComputationResult(
                filtered = filtered,
                incomes = incs,
                expenses = exps,
                totalIncome = totalInc,
                totalExpense = totalExp,
                netSavings = netSav,
                categoryTotals = sortedCatTotals
            )
        }
        filteredTransactions = computation.filtered
        incomes = computation.incomes
        expenses = computation.expenses
        totalIncome = computation.totalIncome
        totalExpense = computation.totalExpense
        netSavings = computation.netSavings
        categoryTotals = computation.categoryTotals
    }

    // Tab 1: Asynchronous high-performance O(N + M) customer profiles construction
    LaunchedEffect(habayebCustomers, habayebTransactions, habayebSearchQuery) {
        val computation = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val txsByCustomer = habayebTransactions.groupBy { it.customerId }
            val profiles = habayebCustomers.map { customer ->
                val customerTxs = txsByCustomer[customer.id] ?: emptyList()
                var owedByThem = 0.0
                var paymentByThem = 0.0
                var owedToThem = 0.0
                var paymentToThem = 0.0
                for (tx in customerTxs) {
                    when (tx.type) {
                        "OWED_BY_THEM" -> owedByThem += tx.amount
                        "PAYMENT_BY_THEM" -> paymentByThem += tx.amount
                        "OWED_TO_THEM" -> owedToThem += tx.amount
                        "PAYMENT_TO_THEM" -> paymentToThem += tx.amount
                    }
                }
                val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)
                customer to netDebt
            }

            val filtered = if (habayebSearchQuery.isBlank()) {
                profiles
            } else {
                profiles.filter {
                    it.first.name.contains(habayebSearchQuery, ignoreCase = true) ||
                    it.first.phone.contains(habayebSearchQuery, ignoreCase = true)
                }
            }

            HabayebComputationResult(
                profiles = profiles,
                filtered = filtered
            )
        }
        customerDebtProfiles = computation.profiles
        filteredCustomerProfiles = computation.filtered
    }

    // Colors for the donut chart segments
    val segmentColors = listOf(
        CoralAccent,
        Color(0xFFFFB17A),
        Color(0xFF4A90E2),
        Color(0xFF5CB85C),
        Color(0xFF8E44AD),
        Color(0xFFF1C40F),
        Color(0xFF1ABC9C),
        Color(0xFFE67E22),
        Color(0xFF34495E)
    )

    // Formatter
    fun formatVal(bd: BigDecimal): String {
        return try {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
            val formatter = java.text.DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(bd)
            "$formatted $currencySymbol"
        } catch (e: Exception) {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
            val formatter = java.text.DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(bd)
            "$formatted $currencySymbol"
        }
    }

    fun formatDouble(v: Double): String {
        return formatVal(BigDecimal.valueOf(v))
    }

    // UNIVERSAL PDF GENERATOR WITH PERFECT ARABIC-COMPATIBLE RIGHT-TO-LEFT ALIGNMENT
    // Moved to top-level for global reuse.

    // EXPORT HANDLER TO TRIGGER PDF OR TEXT FOR RESPECTIVE TABS
    fun exportReport(type: String) { // "PDF" or "TEXT"
        when (activeReportTab) {
            0 -> {
                // Tab 0: ميزان الدار
                val periodName = when(selectedPeriod) {
                    "DAILY" -> "اليومي"
                    "WEEKLY" -> "الأسبوعي"
                    "MONTHLY" -> "الشهري"
                    "YEARLY" -> "السنوي"
                    "CUSTOM" -> "المخصص"
                    else -> "الشامل"
                }
                val reportTitle = "تقرير ميزان الدار المالي ($periodName)"
                val summaryHeaders = listOf(
                    "الوارد الكلي (الإيرادات الإجمالية): ${formatVal(totalIncome)}",
                    "المصروفات المنصرفة: ${formatVal(totalExpense)}",
                    "صافي المحصول والمتبقي في الدار: ${formatVal(netSavings)}"
                )
                val detailedData = categoryTotals.map { (cat, amt) ->
                    val prc = if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                        amt.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 1, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                    "$cat ($prc%)" to formatVal(amt)
                }

                if (type == "PDF") {
                    generateModernPdfReport(context, reportTitle, summaryHeaders, detailedData)
                } else {
                    // Share Text
                    val builder = StringBuilder()
                    builder.append("📊 *تقرير ميزان الدار المالي ($periodName)*\n\n")
                    builder.append("📥 *إجمالي الوارد:* ${formatVal(totalIncome)}\n")
                    builder.append("💸 *إجمالي المصروفات:* ${formatVal(totalExpense)}\n")
                    builder.append("⚖️ *صافي المحصول المتبقي:* ${formatVal(netSavings)}\n\n")
                    if (categoryTotals.isNotEmpty()) {
                        builder.append("📂 *النفقات المصنفة بالأبواب:*\n")
                        categoryTotals.forEach { (cat, amt) ->
                            val prc = if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                                amt.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 1, RoundingMode.HALF_UP)
                            } else BigDecimal.ZERO
                            builder.append("- *$cat:* ${formatVal(amt)} ($prc%)\n")
                        }
                    } else {
                        builder.append("بفضل الله ورعايته لا توجد مصروفات منسقة مضافة للدار 🌸\n")
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        setType("text/plain")
                        putExtra(Intent.EXTRA_TEXT, builder.toString())
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة تقرير ميزان الدار"))
                }
            }
            1 -> {
                // Tab 1: ديون حبايب
                val reportTitle = "تقرير ديون وأمانات حبايب الدار"
                val summaryHeaders = listOf(
                    "إجمالي حقوقنا عند الناس (مطلوب لنا): ${formatDouble(owedByThemTotal)}",
                    "إجمالي ديون الناس علينا (مطلوب للغير): ${formatDouble(owedToThemTotal)}",
                    "صافي موقف الميزان الإئتماني: ${formatDouble(owedByThemTotal - owedToThemTotal)}"
                )
                val detailedData = customerDebtProfiles.map { (customer, balance) ->
                    val statusText = if (balance > 0) {
                        "مطلوب منه (لنا عنده)"
                    } else if (balance < 0) {
                        "مطلوب له (له عندنا)"
                    } else {
                        "مخلص / متساوي"
                    }
                    "${customer.name} [$statusText]" to formatDouble(kotlin.math.abs(balance))
                }

                if (type == "PDF") {
                    generateModernPdfReport(context, reportTitle, summaryHeaders, detailedData)
                } else {
                    val builder = StringBuilder()
                    builder.append("🤝 *تقرير حبايب للديون والالتزامات والأمانات*\n\n")
                    builder.append("🟢 *حقوق الدار عند الحبايب:* ${formatDouble(owedByThemTotal)}\n")
                    builder.append("🔴 *التزامات الدار للغير:* ${formatDouble(owedToThemTotal)}\n")
                    builder.append("⚖️ *نسبة الميزان وصافي التدابير:* ${formatDouble(owedByThemTotal - owedToThemTotal)}\n\n")
                    builder.append("📋 *بيان رصيد الحبايب المفتوح:*\n")
                    customerDebtProfiles.forEach { (customer, balance) ->
                        val status = if (balance > 0) "لنا عنده" else if (balance < 0) "له عندنا" else "مخلص"
                        builder.append("- ${customer.name} (${customer.phone}): ${formatDouble(kotlin.math.abs(balance))} [$status]\n")
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        setType("text/plain")
                        putExtra(Intent.EXTRA_TEXT, builder.toString())
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة ديون حبايب"))
                }
            }
        }
    }

    if (showCustomDialog) {
        var daysInput by remember { mutableStateOf("") }
        val datePickerState = rememberDateRangePickerState()
        var dateMode by remember { mutableStateOf(false) }

        if (dateMode) {
            Dialog(onDismissRequest = { showCustomDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxHeight(0.8f)
                ) {
                    Column {
                        DateRangePicker(
                            state = datePickerState,
                            modifier = Modifier.weight(1f),
                            title = { Text("اختر الفترة", modifier = Modifier.padding(16.dp)) },
                            headline = { Text("تاريخ البداية والنهاية", modifier = Modifier.padding(horizontal = 16.dp)) },
                            showModeToggle = false
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { dateMode = false }) { Text("رجوع") }
                            TextButton(onClick = {
                                customStartDateMs = datePickerState.selectedStartDateMillis
                                val endMs = datePickerState.selectedEndDateMillis
                                customEndDateMs = if (endMs != null) endMs + (24 * 60 * 60 * 1000L) - 1 else null
                                customDays = null
                                showCustomDialog = false
                            }) { Text("تطبيق") }
                        }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showCustomDialog = false },
                containerColor = Color.White,
                title = { Text("فترة مخصصة", color = EmeraldPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("أدخل عدد الأيام لحسابها:", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = daysInput,
                            onValueChange = { daysInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("أو إختر تاريخ محدد:", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedButton(onClick = { dateMode = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("تحديد التواريخ من - إلى")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val d = daysInput.toIntOrNull()
                        if (d != null) {
                            customDays = d
                            customStartDateMs = null
                            customEndDateMs = null
                            showCustomDialog = false
                        }
                    }) {
                        Text("تطبيق الأيام")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomDialog = false }) {
                        Text("الغاء")
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
            ) {
                // Main Header Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val subtitle = when(activeReportTab) {
                        0 -> stringResource(R.string.reports_mizan_subtitle)
                        else -> stringResource(R.string.reports_habayeb_subtitle)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reports_main_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EmeraldPrimary
                        )
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    // Luxury Badge indication
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoralAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reports_live_data),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoralAccent
                        )
                    }
                }

                // HIGHLY LUXURIOUS REPORT TAB SELECTOR BOX (Brilliant visual representation)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF8F9FA))
                        .border(1.dp, Color(0xFFE9ECEF), RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReportTypeTab(
                        title = stringResource(R.string.reports_tab_mizan),
                        selected = activeReportTab == 0,
                        modifier = Modifier.weight(1f)
                    ) { activeReportTab = 0 }

                    ReportTypeTab(
                        title = stringResource(R.string.reports_tab_habayeb),
                        selected = activeReportTab == 1,
                        modifier = Modifier.weight(1f)
                    ) { activeReportTab = 1 }
                }
            }
        },
        bottomBar = {
            // Elegant persistence Action Dock at the bottom for quick sharing
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = Color.White,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Send PDF Button
                    Button(
                        onClick = { exportReport("PDF") },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF PDF")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.pdf_export_button), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Share Text Button
                    Button(
                        onClick = { exportReport("TEXT") },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralAccent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "نص")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.reports_share_text), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = activeReportTab,
            animationSpec = tween(durationMillis = 250),
            modifier = Modifier
                .fillMaxSize()
                .background(IvoryBackground)
                .padding(innerPadding)
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    // TAB 0: ميزان الدار
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        // Date period filtering selection row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFF1F1EF), RoundedCornerShape(16.dp))
                                    .horizontalScroll(rememberScrollState())
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                PeriodTab(
                                    title = stringResource(R.string.reports_period_daily),
                                    selected = selectedPeriod == "DAILY"
                                ) { selectedPeriod = "DAILY" }

                                PeriodTab(
                                    title = stringResource(R.string.reports_period_weekly),
                                    selected = selectedPeriod == "WEEKLY"
                                ) { selectedPeriod = "WEEKLY" }

                                PeriodTab(
                                    title = stringResource(R.string.reports_period_monthly),
                                    selected = selectedPeriod == "MONTHLY"
                                ) { selectedPeriod = "MONTHLY" }

                                PeriodTab(
                                    title = stringResource(R.string.reports_period_yearly),
                                    selected = selectedPeriod == "YEARLY"
                                ) { selectedPeriod = "YEARLY" }

                                PeriodTab(
                                    title = stringResource(R.string.reports_period_custom),
                                    selected = selectedPeriod == "CUSTOM"
                                ) {
                                    selectedPeriod = "CUSTOM"
                                    showCustomDialog = true
                                }

                                PeriodTab(
                                    title = stringResource(R.string.reports_period_all),
                                    selected = selectedPeriod == "ALL"
                                ) { selectedPeriod = "ALL" }
                            }
                        }

                        // Cash flow balance card
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.reports_total_income_label), fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatVal(totalIncome), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                    }

                                    Box(modifier = Modifier.width(1.dp).height(35.dp).background(Color(0xFFE9ECEF)))

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.reports_total_expense_label), fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatVal(totalExpense), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoftRed)
                                    }

                                    Box(modifier = Modifier.width(1.dp).height(35.dp).background(Color(0xFFE9ECEF)))

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.reports_net_savings_label), fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            formatVal(netSavings),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (netSavings.compareTo(BigDecimal.ZERO) >= 0) SoftGreen else SoftRed
                                        )
                                    }
                                }
                            }
                        }

                        // Donut Pie chart (Expansible)
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isChartExpanded = !isChartExpanded }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            stringResource(R.string.reports_expenses_chart_title),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isChartExpanded) "▲" else "▼",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isChartExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Spacer(modifier = Modifier.height(14.dp))
                                            if (categoryTotals.isEmpty()) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 24.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("🎂", fontSize = 38.sp)
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        stringResource(R.string.reports_no_expenses_message),
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.size(170.dp)
                                                ) {
                                                    Canvas(modifier = Modifier.size(140.dp)) {
                                                        val total = totalExpense.toDouble()
                                                        var currentStartAngle = -90f

                                                        categoryTotals.forEachIndexed { index, (_, amount) ->
                                                            val sweepAngle = (amount.toDouble() / total * 360f).toFloat()
                                                            val col = segmentColors[index % segmentColors.size]

                                                            drawArc(
                                                                color = col,
                                                                startAngle = currentStartAngle,
                                                                sweepAngle = sweepAngle,
                                                                useCenter = false,
                                                                size = Size(size.width, size.height),
                                                                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                                                            )
                                                            currentStartAngle += sweepAngle
                                                        }
                                                    }

                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(stringResource(R.string.reports_total_spent_label), fontSize = 10.sp, color = Color.Gray)
                                                        Text(formatVal(totalExpense), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Ledger Categories list breakdown
                        if (categoryTotals.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.reports_expenses_by_category_title),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }

                            items(categoryTotals) { (cat, amount) ->
                                val idx = categoryTotals.indexOfFirst { it.first == cat }
                                val col = segmentColors[idx % segmentColors.size]
                                val pct = if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                                    amount.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 1, RoundingMode.HALF_UP)
                                } else BigDecimal.ZERO

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFF1F1EF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(horizontalAlignment = Alignment.Start) {
                                            Text(formatVal(amount), fontWeight = FontWeight.Bold, color = EmeraldPrimary, fontSize = 13.sp)
                                            Text(stringResource(R.string.reports_expense_percentage_pattern, pct.toString()), fontSize = 10.sp, color = Color.Gray)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.padding(end = 10.dp))
                                            Box(modifier = Modifier.size(width = 20.dp, height = 10.dp).clip(RoundedCornerShape(4.dp)).background(col))
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
                1 -> {
                    // TAB 1: ديون حبايب
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        // Hubayeb Summary cards
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.reports_owed_by_them_label), fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatDouble(owedByThemTotal), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoftGreen)
                                    }

                                    Box(modifier = Modifier.width(1.dp).height(35.dp).background(Color(0xFFE9ECEF)))

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.reports_owed_to_them_label), fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatDouble(owedToThemTotal), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoftRed)
                                    }

                                    Box(modifier = Modifier.width(1.dp).height(35.dp).background(Color(0xFFE9ECEF)))

                                    val netDebt = owedByThemTotal - owedToThemTotal
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.reports_net_debt_label), fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            formatDouble(netDebt),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (netDebt >= 0) SoftGreen else SoftRed
                                        )
                                    }
                                }
                            }
                        }

                        // Search field
                        item {
                            OutlinedTextField(
                                value = habayebSearchQuery,
                                onValueChange = { habayebSearchQuery = it },
                                placeholder = { Text(stringResource(R.string.reports_habayeb_search_placeholder), fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = Color(0xFFDEE2E6),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            )
                        }

                        // Customer debt directory header
                        item {
                            Text(
                                stringResource(R.string.reports_habayeb_directory_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        if (filteredCustomerProfiles.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(R.string.reports_habayeb_search_empty), fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            items(filteredCustomerProfiles) { (customer, balance) ->
                                val statusString = if (balance > 0) {
                                    context.getString(R.string.reports_owed_by_them_status)
                                } else if (balance < 0) {
                                    context.getString(R.string.reports_owed_to_them_status)
                                } else {
                                    context.getString(R.string.reports_balanced_status)
                                }
                                val color = if (balance > 0) SoftGreen else if (balance < 0) SoftRed else Color.Gray

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFF1F1EF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Quick Action dial/whatsapp buttons
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Call button
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {}
                                                },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE2E8F0))
                                            ) {
                                                Icon(Icons.Default.Call, contentDescription = "اتصال", tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                                            }

                                            // WhatsApp button
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val cleanNum = customer.phone.replace(Regex("[^\\d+]"), "")
                                                        val msg = context.getString(
                                                            R.string.reports_whatsapp_message_pattern,
                                                            customer.name,
                                                            formatDouble(kotlin.math.abs(balance)),
                                                            statusString
                                                        )
                                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                                            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum&text=${Uri.encode(msg)}")
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {}
                                                },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFDCFCE7))
                                            ) {
                                                Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // Balance & labels
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(formatDouble(kotlin.math.abs(balance)), fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
                                                Text(statusString, fontSize = 9.sp, color = Color.Gray)
                                            }

                                            // Avatar badge
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFE2E8F0)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = customer.name.take(1).uppercase(),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF475569)
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(customer.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                                Text(customer.phone, fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

// STYLIZED TAB SELECTION PILES
@Composable
fun ReportTypeTab(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) EmeraldPrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFF475569),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PeriodTab(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) EmeraldPrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else Color(0xFF475569),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

// ==========================================
// UPGRADED DYNAMIC UNIVERSAL PDF GENERATOR (Overloaded for backwards compatibility)
// ==========================================
fun generateModernPdfReport(
    context: android.content.Context,
    title: String,
    headers: List<String>,
    data: List<Pair<String, String>>
) {
    val isLedger = title.contains("كشف حساب")
    if (isLedger) {
        val transactionsList = data.mapIndexed { index, (dateStr, rowVal) ->
            val parts = rowVal.split(" - ")
            val amountRaw = parts.getOrNull(0) ?: "0"
            val typeRaw = parts.getOrNull(1) ?: "OWED_BY_TEM"
            
            val cleanAmt = amountRaw.replace(",", "")
                .replace("٫", ".")
                .replace("ر.ي", "")
                .replace("ريال", "")
                .replace(" ", "")
                .filter { it.isDigit() }
            val amountLong = cleanAmt.toLongOrNull() ?: 0L
            
            val mappedType = when {
                typeRaw.contains("PAYMENT_BY_THEM") || typeRaw.contains("استلام دفعة") || typeRaw.contains("سداد دفعة") || typeRaw.contains("تم السداد") || typeRaw.contains("سداد") -> {
                    if (title.contains("عليّ") || title.contains("ديون الدار") || title.contains("له عندنا") || typeRaw.contains("PAYMENT_TO_THEM")) {
                        "PAYMENT_TO_THEM"
                    } else {
                        "PAYMENT_BY_THEM"
                    }
                }
                typeRaw.contains("OWED_BY_TEM") || typeRaw.contains("OWED_BY_THEM") || typeRaw == "دين عليه" || typeRaw.contains("عليه") -> {
                    "OWED_BY_THEM"
                }
                typeRaw.contains("OWED_TO_THEM") || typeRaw == "دين علي" || typeRaw == "دين عليّ" || typeRaw.contains("عليّ") || typeRaw == "علي" -> {
                    "OWED_TO_THEM"
                }
                else -> {
                    "OWED_BY_THEM"
                }
            }
            
            val parsedTimestamp = try {
                val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.ENGLISH)
                sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                try {
                    val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
                    sdf2.parse(dateStr)?.time ?: System.currentTimeMillis()
                } catch (e2: Exception) {
                    System.currentTimeMillis() - (data.size - index) * 1000 * 60 * 60 * 24L
                }
            }
            
            PdfTransaction(
                date = dateStr,
                description = "",
                amount = amountLong,
                type = mappedType,
                timestamp = parsedTimestamp
            )
        }
        generateModernPdfReport(context, title, transactionsList)
    } else {
        var totalAmountVal = ""
        var paidAmountVal = ""
        var remainingAmountVal = ""

        if (title.contains("المخزون") || title.contains("جرد")) {
            if (headers.size >= 5) {
                val h2 = headers[2]
                totalAmountVal = if (h2.contains(":")) h2.substringAfter(":").trim() else h2
                val h4 = headers[4]
                paidAmountVal = if (h4.contains(":")) h4.substringAfter(":").trim() else h4
                val h3 = headers[3]
                remainingAmountVal = if (h3.contains(":")) h3.substringAfter(":").trim() else h3
            } else if (headers.isNotEmpty()) {
                totalAmountVal = headers.getOrNull(0)?.substringAfter(":")?.trim() ?: ""
                paidAmountVal = headers.getOrNull(1)?.substringAfter(":")?.trim() ?: ""
                remainingAmountVal = headers.getOrNull(2)?.substringAfter(":")?.trim() ?: ""
            }
        } else {
            if (headers.size >= 1) {
                val h = headers[0]
                totalAmountVal = if (h.contains(":")) h.substringAfter(":").trim() else h
            }
            if (headers.size >= 2) {
                val h = headers[1]
                paidAmountVal = if (h.contains(":")) h.substringAfter(":").trim() else h
            }
            if (headers.size >= 3) {
                val h = headers[2]
                remainingAmountVal = if (h.contains(":")) h.substringAfter(":").trim() else h
            }
        }

        val tableHeaders = when {
            title.contains("المخزون") || title.contains("جرد") -> listOf("سعر الشراء / البيع", "البند / التصنيف والكمية")
            else -> listOf("القيمة الإجمالية", "البند / التصنيف")
        }

        val tableRows = mutableListOf<List<String>>()
        for ((rowTitle, rowVal) in data) {
            tableRows.add(listOf(rowVal, rowTitle))
        }

        generateGenericPdfReport(
            context = context,
            title = title,
            totalAmount = totalAmountVal,
            paidAmount = paidAmountVal,
            remainingAmount = remainingAmountVal,
            tableHeaders = tableHeaders,
            tableRows = tableRows
        )
    }
}

// ==========================================
// DYNAMIC PRIMARY PDF REPORT FOR LEDGERS
// ==========================================
fun generateModernPdfReport(
    context: android.content.Context,
    title: String,
    transactions: List<PdfTransaction>
) {
    // 1. حساب الـ Hash للبيانات لضبط رقم التقرير تلقائياً حسب المتغيرات
    val sharedPrefs = context.getSharedPreferences("report_counter_prefs", android.content.Context.MODE_PRIVATE)
    val customerKey = title.replace(" ", "_").replace(":", "_")
    val lastHash = sharedPrefs.getString("${customerKey}_hash", "") ?: ""
    val currentHash = transactions.joinToString { "${it.amount}_${it.type}_${it.timestamp}" }.hashCode().toString()
    
    var reportNumber = sharedPrefs.getInt("${customerKey}_number", 1)
    if (lastHash.isNotEmpty() && lastHash != currentHash) {
        reportNumber += 1 // زادت المعاملات أو تغيرت، يرتفع الرقم تلقائياً
        sharedPrefs.edit().putInt("${customerKey}_number", reportNumber).putString("${customerKey}_hash", currentHash).apply()
    } else if (lastHash.isEmpty()) {
        sharedPrefs.edit().putInt("${customerKey}_number", 1).putString("${customerKey}_hash", currentHash).apply()
    }

    // 2. قراءة بيانات النشاط التجاري الحالية ديناميكياً من كافة المصادر المتاحة
    val prefs = context.getSharedPreferences("business_profile", android.content.Context.MODE_PRIVATE)
    val altPrefs = context.getSharedPreferences("business_profile_prefs", android.content.Context.MODE_PRIVATE)

    val businessName = prefs.getString("biz_name", "").orEmpty()
        .ifBlank { altPrefs.getString("business_name", "").orEmpty() }
        .ifBlank { "الاسم التجاري" }

    val businessSlogan = prefs.getString("biz_desc", "").orEmpty()
        .ifBlank { altPrefs.getString("business_slogan", "").orEmpty() }

    var businessPhone = ""
    val phonesJson = prefs.getString("biz_phones", "[]") ?: "[]"
    try {
        val jsonArray = org.json.JSONArray(phonesJson)
        if (jsonArray.length() > 0) {
            businessPhone = jsonArray.getString(0)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (businessPhone.isBlank()) {
        businessPhone = altPrefs.getString("business_phone", "").orEmpty()
    }

    val logoPath = prefs.getString("biz_logo_path", "").orEmpty()
        .ifBlank { altPrefs.getString("logo_path", "").orEmpty() }
        .ifBlank { null }

    // 3. الفرز والترتيب الحسابي التراكمي (أقدم فـ أحدث) مع تصحيح الأنواع الرياضية
    val sortedTxs = transactions.sortedBy { it.timestamp }
    val isSupplierMode = transactions.any { it.type == "OWED_TO_THEM" || it.type == "PAYMENT_TO_THEM" }
    
    var totalOwed = 0L
    var totalPaid = 0L
    var runningBalance = 0L
    val processedRows = mutableListOf<List<String>>()

    for ((index, tx) in sortedTxs.withIndex()) {
        val isOwed = tx.type == "OWED_BY_TEM" || tx.type == "OWED_BY_THEM" || tx.type == "OWED_TO_THEM"
        val isPaid = tx.type == "PAYMENT_BY_THEM" || tx.type == "PAYMENT_TO_THEM"

        if (isOwed) {
            totalOwed += tx.amount
            runningBalance += tx.amount
        } else if (isPaid) {
            totalPaid += tx.amount
            runningBalance -= tx.amount
        }

        // إرفاق اسم اليوم مع التاريخ تلقائياً للوضوح العالي مع بوابة فحص أمان للأمواج الزمنية (تحويل الثواني لملي ثانية إذا لزم)
        val txTime = if (tx.timestamp < 10000000000L) tx.timestamp * 1000 else tx.timestamp
        val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale("ar")).format(java.util.Date(txTime))
        val dateNumbers = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.ENGLISH).format(java.util.Date(txTime))
        val formattedDateWithDay = "$dayName - $dateNumbers"

        val finalDesc = if (tx.description.isNotEmpty()) tx.description else {
            when (tx.type) {
                "OWED_BY_TEM", "OWED_BY_THEM" -> "دين عليه"
                "PAYMENT_BY_THEM" -> "سداد"
                "OWED_TO_THEM" -> "دين علي"
                "PAYMENT_TO_THEM" -> "تم السداد"
                else -> "معاملة"
            }
        }

        // إزالة النصوص الزائدة (له/عليه) من سطور الجدول وإظهار الأرقام فقط للتناسق البصري
        val balanceStr = "${java.lang.Math.abs(runningBalance)} ر.ي"

        processedRows.add(
            listOf(
                (index + 1).toString(),
                formattedDateWithDay,
                finalDesc,
                if (isOwed) "${tx.amount} ر.ي" else "-",
                if (isPaid) "${tx.amount} ر.ي" else "-",
                balanceStr
            )
        )
    }

    val finalNetBalance = java.lang.Math.abs(runningBalance)
    val netStatus = if (isSupplierMode) {
        if (runningBalance >= 0) "له" else "عليه"
    } else {
        if (runningBalance >= 0) "عليه" else "له"
    }

    // 4. إعداد مستند الـ PDF ومقاس الصفحة (A4)
    val pdfDocument = android.graphics.pdf.PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    // 5. أقلام وألوان الرسم البنكي
    val primaryColorHex = "#0F5257"
    val paintPrimaryBold = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 12f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    val paintTextRightRegular = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    val paintTextLeftBold = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 10f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val paintTextLeftRegular = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        textAlign = android.graphics.Paint.Align.LEFT
    }

    var currentY = 40f
    val rightMargin = (pageWidth - 40).toFloat()
    val leftMargin = 40f
    val centerWidth = (pageWidth / 2).toFloat()

    // 6. رسم الهوية البصرية (Logo) بالمنتصف تماماً وبحلقة دائرية فخمة (60x60) دون تداخل مطلقا
    if (logoPath != null) {
        try {
            val file = java.io.File(logoPath)
            if (file.exists()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 60, 60, true)
                    val logoX = centerWidth - 30f
                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val framePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#E2E8F0")
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.2f
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(logoX, currentY, logoX + 60f, currentY + 60f, 30f, 30f, bgPaint)
                    canvas.drawBitmap(scaledBitmap, logoX, currentY, null)
                    canvas.drawRoundRect(logoX, currentY, logoX + 60f, currentY + 60f, 30f, 30f, framePaint)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 7. رسم بيانات النشاط التجاري جهة اليمين (RTL ومتناسقة)
    canvas.drawText(businessName, rightMargin, currentY + 14f, paintPrimaryBold)
    if (businessSlogan.isNotEmpty()) {
        canvas.drawText(businessSlogan, rightMargin, currentY + 32f, paintTextRightRegular)
    }
    if (businessPhone.isNotEmpty()) {
        canvas.drawText("📞 هاتف: $businessPhone", rightMargin, currentY + 48f, paintTextRightRegular)
    }

    // 8. رسم رقم التقرير وتاريخ اصداره اللحظي باليوم والساعة جهة اليسار
    val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale("ar")).format(java.util.Date())
    val dateNumbers = java.text.SimpleDateFormat("yyyy/MM/dd hh:mm", java.util.Locale.ENGLISH).format(java.util.Date())
    val amPm = java.text.SimpleDateFormat("a", java.util.Locale("ar")).format(java.util.Date())
    val reportDateStr = "$dayName - $dateNumbers $amPm"
    canvas.drawText("سجل رقم: #$reportNumber", leftMargin, currentY + 14f, paintTextLeftBold)
    canvas.drawText("حُرر في: $reportDateStr", leftMargin, currentY + 32f, paintTextLeftRegular)

    currentY += 75f

    // 9. خط فاصل رقيق وناعم جداً أسفل الترويسة الموحدة
    val paintLine = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#E2E8F0")
        strokeWidth = 1f
    }
    canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)

    currentY += 25f

    // 10. عنوان كشف الحساب بالمنتصف
    val paintTitle = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 14f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText(title, centerWidth, currentY, paintTitle)

    currentY += 20f

    // 11. رسم كرت ملخص المؤشرات المالي الخالي من السوالب والمعاد صياغته محاسبياً وبطريقة ذكية تفرق بين المورد والعميل
    val paintSummaryBg = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#F8FAFC")
        style = android.graphics.Paint.Style.FILL
    }
    val paintSummaryBorder = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#E2E8F0")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1f
    }
    canvas.drawRoundRect(leftMargin, currentY, rightMargin, currentY + 55f, 10f, 10f, paintSummaryBg)
    canvas.drawRoundRect(leftMargin, currentY, rightMargin, currentY + 55f, 10f, 10f, paintSummaryBorder)

    val paintSummaryLabel = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }
    val paintSummaryValue = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 12f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    val (sumLabelRight, sumLabelCenter, sumLabelLeft) = if (isSupplierMode) {
        Triple("إجمالي له علينا (دين)", "إجمالي سددناه له (سداد)", "صافي المتبقي للمورد")
    } else {
        Triple("إجمالي عليه (دين)", "إجمالي سدده لنا (سداد)", "صافي المتبقي عليه")
    }

    // أ. اليمين (إجمالي عليه)
    paintSummaryLabel.textAlign = android.graphics.Paint.Align.RIGHT
    paintSummaryValue.textAlign = android.graphics.Paint.Align.RIGHT
    canvas.drawText(sumLabelRight, rightMargin - 20f, currentY + 22f, paintSummaryLabel)
    canvas.drawText("$totalOwed ر.ي", rightMargin - 20f, currentY + 41f, paintSummaryValue)

    // ب. المنتصف (إجمالي له)
    paintSummaryLabel.textAlign = android.graphics.Paint.Align.CENTER
    paintSummaryValue.textAlign = android.graphics.Paint.Align.CENTER
    canvas.drawText(sumLabelCenter, centerWidth, currentY + 22f, paintSummaryLabel)
    canvas.drawText("$totalPaid ر.ي", centerWidth, currentY + 41f, paintSummaryValue)

    // ج. اليسار (المبلغ المتبقي الحالي)
    paintSummaryLabel.textAlign = android.graphics.Paint.Align.LEFT
    paintSummaryValue.textAlign = android.graphics.Paint.Align.LEFT
    canvas.drawText(sumLabelLeft, leftMargin + 20f, currentY + 22f, paintSummaryLabel)
    canvas.drawText("$finalNetBalance ر.ي ($netStatus)", leftMargin + 20f, currentY + 41f, paintSummaryValue)

    currentY += 80f

    // 12. إحداثيات الأعمدة لتبدأ عربياً بالكامل (RTL من اليمين لليسار) وتجعل مسلسل مستقلاً
    val columnsX = listOf(
        rightMargin,        // مسلسل (أول عمود يمين) - مستقل بفراغ
        rightMargin - 40f,  // التاريخ
        rightMargin - 150f, // البيان / التفاصيل
        rightMargin - 290f, // عليه (دين)
        rightMargin - 370f, // له (سداد)
        leftMargin          // المبلغ المتبقي (أول عمود يسار)
    )

    val headers = listOf("مسلسل", "التاريخ", "البيان / التفاصيل", "عليه (دين)", "له (سداد)", "المبلغ المتبقي")
    
    val paintHeader = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val paintCell = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 8.5f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }

    // رسم ترويسة الجدول من اليمين لليسار
    headers.forEachIndexed { i, headerText ->
        paintHeader.textAlign = if (i == 5) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
        canvas.drawText(headerText, columnsX[i], currentY, paintHeader)
    }

    currentY += 12f
    canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)

    // رسم فاصل عمودي ناعم جداً لفصل المسلسل كلياً عن الجدول
    canvas.drawLine(rightMargin - 28f, currentY - 18f, rightMargin - 28f, pageHeight - 60f, paintLine)

    currentY += 20f

    // 13. رسم الخلايا والصفوف بدقة فائقة وبمستطيلات رشيقة ملونة خلف المبالغ لمنع التداخل
    val paintCellBg = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
    }

    for (row in processedRows) {
        if (currentY > pageHeight - 60f) {
            break 
        }

        row.forEachIndexed { i, cellText ->
            paintCell.textAlign = if (i == 5) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
            
            // تهيئة الخطوط والألوان والخلفيات الرشيقة للخلايا
            when (i) {
                3 -> { // عليه (دين) - مستطيل أحمر ناعم وخفيف جداً خلف المبلغ
                    if (cellText != "-") {
                        paintCellBg.color = android.graphics.Color.parseColor("#FFF5F5") // خلفية ناعمة جداً
                        canvas.drawRoundRect(columnsX[i] - 70f, currentY - 10f, columnsX[i] + 5f, currentY + 4f, 4f, 4f, paintCellBg)
                        paintCell.color = android.graphics.Color.parseColor("#DC2626")
                    } else {
                        paintCell.color = android.graphics.Color.BLACK
                    }
                }
                4 -> { // له (سداد) - مستطيل أخضر ناعم وخفيف جداً خلف المبلغ
                    if (cellText != "-") {
                        paintCellBg.color = android.graphics.Color.parseColor("#F0FDF4") // خلفية ناعمة جداً
                        canvas.drawRoundRect(columnsX[i] - 70f, currentY - 10f, columnsX[i] + 5f, currentY + 4f, 4f, 4f, paintCellBg)
                        paintCell.color = android.graphics.Color.parseColor("#16A34A")
                    } else {
                        paintCell.color = android.graphics.Color.BLACK
                    }
                }
                5 -> { // المبلغ المتبقي (الرصيد التراكمي بدون نصوص زائدة لتفادي التداخل)
                    paintCell.color = android.graphics.Color.parseColor(primaryColorHex)
                    paintCell.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                else -> {
                    paintCell.color = android.graphics.Color.BLACK
                    paintCell.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                }
            }
            canvas.drawText(cellText, columnsX[i], currentY, paintCell)
        }

        currentY += 12f
        canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)
        currentY += 16f
    }

    // 14. تذييل التقرير
    val paintFooter = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 8f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("كشف حساب آلي معتمد - صادر بواسطة تطبيق ميزان الدار", centerWidth, (pageHeight - 30).toFloat(), paintFooter)

    pdfDocument.finishPage(page)

    // 15. ميزة الحفظ التلقائي الآمن الخالي من الانهيار والمطابق لكافة الصلاحيات (في مجلد Downloads الخاص بالتطبيق)
    val fileName = "mizan_report_${System.currentTimeMillis() % 100000}.pdf"
    
    try {
        val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        val file = java.io.File(downloadDir, fileName)
        
        val outputStream = java.io.FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        pdfDocument.close()
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة كشف الحساب المطور"))
        
        android.widget.Toast.makeText(context, "تم توليد وحفظ كشف الحساب بنجاح في مجلد التنزيلات الخاص بالتطبيق 📂", android.widget.Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        pdfDocument.close()
        android.widget.Toast.makeText(context, "فشل حفظ التقرير: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

// ==========================================
// THE EXQUISITE NEW PRIMARY PDF GENERATION SIGNATURE
// ==========================================
fun generateGenericPdfReport(
    context: android.content.Context,
    title: String,
    totalAmount: String,       // قيمة "الإجمالي" الممررة ديناميكياً
    paidAmount: String,        // قيمة "تم سداد / تم دفع" الممررة ديناميكياً
    remainingAmount: String,   // قيمة "المتبقي" الممررة ديناميكياً
    tableHeaders: List<String>,
    tableRows: List<List<String>>
) {
    // 1. Fetch business details dynamically from multiple shared preference sources
    val prefs = context.getSharedPreferences("business_profile", android.content.Context.MODE_PRIVATE)
    val altPrefs = context.getSharedPreferences("business_profile_prefs", android.content.Context.MODE_PRIVATE)

    val businessName = prefs.getString("biz_name", "").orEmpty()
        .ifBlank { altPrefs.getString("business_name", "").orEmpty() }
        .ifBlank { "ميزان الدار" }

    val businessSlogan = prefs.getString("biz_desc", "").orEmpty()
        .ifBlank { altPrefs.getString("business_slogan", "").orEmpty() }
        .ifBlank { "التطبيق المالي للتدابير وتنسيق الميزانية" }

    var businessPhone = ""
    val phonesJson = prefs.getString("biz_phones", "[]") ?: "[]"
    try {
        val jsonArray = org.json.JSONArray(phonesJson)
        if (jsonArray.length() > 0) {
            businessPhone = jsonArray.getString(0)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (businessPhone.isBlank()) {
        businessPhone = altPrefs.getString("business_phone", "").orEmpty()
    }

    val logoPath = prefs.getString("biz_logo_path", null)
        ?: altPrefs.getString("logo_path", null)

    val pdfDocument = android.graphics.pdf.PdfDocument()
    val pageWidth = 595
    val pageHeight = 842

    var pageNum = 1
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
    var activePage = pdfDocument.startPage(pageInfo)
    var activeCanvas = activePage.canvas

    // Colour definitions from modern design choices
    val primaryColorHex = "#0F5257"
    val slateGrayHex = "#E2E8F0"
    val debitRedHex = "#DC2626"
    val creditGreenHex = "#16A34A"
    val headerBgHex = "#F8FAFC"
    val textDarkHex = "#1E293B"
    val textGrayHex = "#64748B"

    // Set up modern, responsive paint configurations
    val paintTextRightBold = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 12f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.RIGHT
        isAntiAlias = true
    }
    
    val paintTextRightRegular = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(textGrayHex)
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        textAlign = android.graphics.Paint.Align.RIGHT
        isAntiAlias = true
    }

    val paintTextLeftBold = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(textDarkHex)
        textSize = 10f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.LEFT
        isAntiAlias = true
    }

    val paintTextLeftRegular = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(textGrayHex)
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        textAlign = android.graphics.Paint.Align.LEFT
        isAntiAlias = true
    }

    // Starting drawing coordinates
    var currentY = 45f
    val rightMargin = (pageWidth - 40).toFloat()
    val leftMargin = 40f
    val centerWidth = (pageWidth / 2).toFloat()

    // 3. Draw Logo in the absolute center (circular / rounded frame)
    if (logoPath != null) {
        try {
            val file = java.io.File(logoPath)
            if (file.exists()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 50, 50, true)
                    val logoX = centerWidth - 25f
                    
                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val framePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor(slateGrayHex)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1f
                        isAntiAlias = true
                    }
                    
                    // Center circular/rounded frame
                    activeCanvas.drawRoundRect(logoX, currentY, logoX + 50f, currentY + 50f, 25f, 25f, bgPaint)
                    activeCanvas.drawBitmap(scaledBitmap, logoX, currentY, null)
                    activeCanvas.drawRoundRect(logoX, currentY, logoX + 50f, currentY + 50f, 25f, 25f, framePaint)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 4. Draw business details on the right top (RTL, close alignment)
    activeCanvas.drawText(businessName, rightMargin, currentY + 12f, paintTextRightBold)
    if (businessSlogan.isNotEmpty()) {
        activeCanvas.drawText(businessSlogan, rightMargin, currentY + 28f, paintTextRightRegular)
    }
    if (businessPhone.isNotEmpty()) {
        activeCanvas.drawText("📞 هاتف: $businessPhone", rightMargin, currentY + 42f, paintTextRightRegular)
    }

    // 5. Draw report number and stamp date on the left top (close alignment)
    val sdfDate = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.ENGLISH)
    val formattedDate = sdfDate.format(java.util.Date())
    activeCanvas.drawText("سجل رقم (1)", leftMargin, currentY + 12f, paintTextLeftBold)
    activeCanvas.drawText("التاريخ: $formattedDate", leftMargin, currentY + 28f, paintTextLeftRegular)

    currentY += 70f

    // 6. Draw clean soft modern separator line below header
    val paintLine = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(slateGrayHex)
        strokeWidth = 1.2f
    }
    activeCanvas.drawLine(leftMargin, currentY, rightMargin, currentY, paintLine)

    currentY += 25f

    // 7. Centered report title
    val paintTitle = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(textDarkHex)
        textSize = 13f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    activeCanvas.drawText(title, centerWidth, currentY, paintTitle)

    currentY += 20f

    // 8. Custom indicators and summary metrics styled block (Curved soft rectangle)
    val paintSummaryBg = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(headerBgHex)
        style = android.graphics.Paint.Style.FILL
    }
    val paintSummaryBorder = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(slateGrayHex)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1f
    }
    
    activeCanvas.drawRoundRect(leftMargin, currentY, rightMargin, currentY + 50f, 8f, 8f, paintSummaryBg)
    activeCanvas.drawRoundRect(leftMargin, currentY, rightMargin, currentY + 50f, 8f, 8f, paintSummaryBorder)

    val paintSummaryLabel = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(textGrayHex)
        textSize = 8.5f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }
    val paintSummaryValue = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 11f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }

    // Determine clean localized labels depending on context
    val (labelRight, labelCenter, labelLeft) = when {
        title.contains("كشف حساب") || title.contains("ديون") || title.contains("حبايب") -> {
            Triple("إجمالي عليه (مدين)", "إجمالي له (دائن)", "الصافي الرصيد الحالي")
        }
        title.contains("ميزان الدار المالي") || title.contains("تقرير ميزان") -> {
            Triple("إجمالي الوارد الكلي", "المصروفات المنصرفة", "صافي المحصول والمتبقي")
        }
        title.contains("المخزون") || title.contains("جرد") -> {
            Triple("تكلفة شراء البضاعة", "الأرباح المتوقعة", "القيمة البيعية المقدرة")
        }
        else -> {
            Triple("الإجمالي الإجمالي", "تم دفع / سداد", "المتبقي المستحق")
        }
    }

    // Right Column (الإجمالي / الوارد / عليه)
    paintSummaryLabel.textAlign = android.graphics.Paint.Align.RIGHT
    paintSummaryValue.textAlign = android.graphics.Paint.Align.RIGHT
    activeCanvas.drawText(labelRight, rightMargin - 20f, currentY + 20f, paintSummaryLabel)
    
    val colorRight = if (labelRight.contains("عليه")) debitRedHex else primaryColorHex
    activeCanvas.drawText(
        totalAmount, 
        rightMargin - 20f, 
        currentY + 38f, 
        paintSummaryValue.apply { color = android.graphics.Color.parseColor(colorRight) }
    )

    // Center Column (سداد / المنصرف / له)
    paintSummaryLabel.textAlign = android.graphics.Paint.Align.CENTER
    paintSummaryValue.textAlign = android.graphics.Paint.Align.CENTER
    activeCanvas.drawText(labelCenter, centerWidth, currentY + 20f, paintSummaryLabel)
    
    val colorCenter = if (labelCenter.contains("له") || labelCenter.contains("المصروفات")) creditGreenHex else primaryColorHex
    activeCanvas.drawText(
        paidAmount, 
        centerWidth, 
        currentY + 38f, 
        paintSummaryValue.apply { color = android.graphics.Color.parseColor(colorCenter) }
    )

    // Left Column (المتبقي / صافي / الصافي)
    paintSummaryLabel.textAlign = android.graphics.Paint.Align.LEFT
    paintSummaryValue.textAlign = android.graphics.Paint.Align.LEFT
    activeCanvas.drawText(labelLeft, leftMargin + 20f, currentY + 20f, paintSummaryLabel)
    
    activeCanvas.drawText(
        remainingAmount, 
        leftMargin + 20f, 
        currentY + 38f, 
        paintSummaryValue.apply { color = android.graphics.Color.parseColor(primaryColorHex) }
    )

    currentY += 75f

    // 9. Draw Table Headers and Content beautifully with ZERO black vertical lines
    if (tableHeaders.isNotEmpty() && tableRows.isNotEmpty()) {
        var tableY = currentY
        activeCanvas.drawRoundRect(leftMargin, tableY, rightMargin, tableY + 25f, 4f, 4f, paintSummaryBg)
        activeCanvas.drawRoundRect(leftMargin, tableY, rightMargin, tableY + 25f, 4f, 4f, paintSummaryBorder)

        val paintTableHeader = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(primaryColorHex)
            textSize = 9.5f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }

        val numCols = tableHeaders.size
        val colWidth = (pageWidth - 80f) / numCols

        // Draw headers (RTL oriented: right to left)
        for (i in 0 until numCols) {
            val headerText = tableHeaders[i]
            val xPos = rightMargin - (i * colWidth) - (colWidth / 2f)
            paintTableHeader.textAlign = android.graphics.Paint.Align.CENTER
            activeCanvas.drawText(headerText, xPos, tableY + 16f, paintTableHeader)
        }

        tableY += 25f

        val paintRowText = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(textDarkHex)
            textSize = 9f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            isAntiAlias = true
        }

        val paintRowSeparator = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F1F5F9")
            strokeWidth = 0.8f
        }

        for (rowIndex in tableRows.indices) {
            val row = tableRows[rowIndex]
            
            // Check page height space availability
            if (tableY > pageHeight - 80f) {
                pdfDocument.finishPage(activePage)
                pageNum++
                val newPageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                activePage = pdfDocument.startPage(newPageInfo)
                activeCanvas = activePage.canvas
                tableY = 50f
                
                // Redraw table headers on the new page
                activeCanvas.drawRoundRect(leftMargin, tableY, rightMargin, tableY + 25f, 4f, 4f, paintSummaryBg)
                activeCanvas.drawRoundRect(leftMargin, tableY, rightMargin, tableY + 25f, 4f, 4f, paintSummaryBorder)
                for (i in 0 until numCols) {
                    val headerText = tableHeaders[i]
                    val xPos = rightMargin - (i * colWidth) - (colWidth / 2f)
                    paintTableHeader.textAlign = android.graphics.Paint.Align.CENTER
                    activeCanvas.drawText(headerText, xPos, tableY + 16f, paintTableHeader)
                }
                tableY += 25f
            }

            // Draw clean subtle row separator line before writing
            activeCanvas.drawLine(leftMargin, tableY + 22f, rightMargin, tableY + 22f, paintRowSeparator)

            // Draw non-overlapping column text elements (RTL layout)
            for (colIndex in 0 until numCols) {
                val cellText = row.getOrNull(colIndex) ?: ""
                val xPos = rightMargin - (colIndex * colWidth) - (colWidth / 2f)
                
                // Formatting depending on content
                val paintCell = android.graphics.Paint(paintRowText).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    val isDebit = cellText.contains("عليه") || cellText.contains("مدين") || cellText.contains("دين")
                    val isCredit = cellText.contains("له") || cellText.contains("سداد") || cellText.contains("تم سداد") || cellText.contains("دائن")
                    
                    if (isDebit) {
                        color = android.graphics.Color.parseColor(debitRedHex)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    } else if (isCredit) {
                        color = android.graphics.Color.parseColor(creditGreenHex)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    } else {
                        color = android.graphics.Color.parseColor(textDarkHex)
                        typeface = android.graphics.Typeface.DEFAULT
                    }
                }
                activeCanvas.drawText(cellText, xPos, tableY + 15f, paintCell)
            }

            tableY += 26f
        }
    }

    // Modern professional footer at bottom center
    val paintFooter = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(textGrayHex)
        textSize = 8f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
        isAntiAlias = true
    }
    activeCanvas.drawText("ميزان الدار - التطبيق المالي المتكامل لتنسيق الحسابات والتدابير 🌸", centerWidth, (pageHeight - 30).toFloat(), paintFooter)

    pdfDocument.finishPage(activePage)

    // Save final rendered PDF structure and trigger sharing intents
    val fileName = "mizan_report_${System.currentTimeMillis() % 100000}.pdf"
    val file = java.io.File(context.getExternalFilesDir(null), fileName)
    try {
        pdfDocument.writeTo(java.io.FileOutputStream(file))
        pdfDocument.close()

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.pdf_chooser_title)))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, context.getString(R.string.pdf_export_failed, e.message), android.widget.Toast.LENGTH_LONG).show()
        pdfDocument.close()
    }
}

private data class MizanComputationResult(
    val filtered: List<TransactionDb>,
    val incomes: List<TransactionDb>,
    val expenses: List<TransactionDb>,
    val totalIncome: java.math.BigDecimal,
    val totalExpense: java.math.BigDecimal,
    val netSavings: java.math.BigDecimal,
    val categoryTotals: List<Pair<String, java.math.BigDecimal>>
)

private data class HabayebComputationResult(
    val profiles: List<Pair<HabayebCustomer, Double>>,
    val filtered: List<Pair<HabayebCustomer, Double>>
)
