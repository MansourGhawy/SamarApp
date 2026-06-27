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
import com.example.ui.screens.reports.components.*
import com.example.ui.screens.reports.utils.*
import com.example.ui.screens.reports.ReportsViewModel
import com.example.ui.state.*
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsView(
    viewModel: FinanceViewModel,
    settings: AppSettings,
    currencySymbol: String,
    reportsViewModel: ReportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val systemInDarkMode = isSystemInDarkTheme()
    val view = LocalView.current

    // Observe data sources from screen viewmodel
    val owedByThemTotal by viewModel.habayebOwedByThemTotalState.collectAsStateWithLifecycle()
    val owedToThemTotal by viewModel.habayebOwedToThemTotalState.collectAsStateWithLifecycle()

    // Observe state from ReportsViewModel
    val activeReportTab by reportsViewModel.activeReportTab.collectAsStateWithLifecycle()
    val selectedPeriod by reportsViewModel.selectedPeriod.collectAsStateWithLifecycle()
    val customStartDateMs by reportsViewModel.customStartDateMs.collectAsStateWithLifecycle()
    val customEndDateMs by reportsViewModel.customEndDateMs.collectAsStateWithLifecycle()
    val customDays by reportsViewModel.customDays.collectAsStateWithLifecycle()
    val isChartExpanded by reportsViewModel.isChartExpanded.collectAsStateWithLifecycle()
    val habayebSearchQuery by reportsViewModel.habayebSearchQuery.collectAsStateWithLifecycle()

    val mizanResult by reportsViewModel.mizanComputationState.collectAsStateWithLifecycle()
    val habayebResult by reportsViewModel.habayebComputationState.collectAsStateWithLifecycle()

    val totalIncome = mizanResult.totalIncome
    val totalExpense = mizanResult.totalExpense
    val netSavings = mizanResult.netSavings
    val categoryTotals = mizanResult.categoryTotals

    val customerDebtProfiles = habayebResult.profiles
    val filteredCustomerProfiles = habayebResult.filtered

    // Dialog state
    var showCustomDialog by remember { mutableStateOf(false) }
    var selectedCustomerForDetails by remember { mutableStateOf<HabayebCustomer?>(null) }

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
                                val startMs = datePickerState.selectedStartDateMillis
                                val endMs = datePickerState.selectedEndDateMillis?.let { it + (24 * 60 * 60 * 1000L) - 1 }
                                reportsViewModel.setCustomDateRange(startMs, endMs)
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
                            reportsViewModel.setCustomDays(d)
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

    selectedCustomerForDetails?.let { customer ->
        CustomerDetailsDialog(
            customer = customer,
            viewModel = viewModel,
            currencySymbol = currencySymbol,
            onDismiss = { selectedCustomerForDetails = null }
        )
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
                    ) { reportsViewModel.setActiveReportTab(0) }

                    ReportTypeTab(
                        title = stringResource(R.string.reports_tab_habayeb),
                        selected = activeReportTab == 1,
                        modifier = Modifier.weight(1f)
                    ) { reportsViewModel.setActiveReportTab(1) }
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
                        mizanReportContent(
                            selectedPeriod = selectedPeriod,
                            onPeriodSelected = { reportsViewModel.selectPeriod(it) },
                            onShowCustomDialog = { showCustomDialog = true },
                            totalIncome = totalIncome,
                            totalExpense = totalExpense,
                            netSavings = netSavings,
                            isChartExpanded = isChartExpanded,
                            onToggleChartExpanded = { reportsViewModel.toggleChartExpanded() },
                            categoryTotals = categoryTotals,
                            segmentColors = segmentColors,
                            formatVal = ::formatVal
                        )
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
                        habayebReportContent(
                            habayebSearchQuery = habayebSearchQuery,
                            onSearchQueryChanged = { reportsViewModel.updateHabayebSearchQuery(it) },
                            owedByThemTotal = owedByThemTotal,
                            owedToThemTotal = owedToThemTotal,
                            filteredCustomerProfiles = filteredCustomerProfiles,
                            onCustomerSelected = { selectedCustomerForDetails = it },
                            formatDouble = ::formatDouble
                        )
                    }
                }
            }
        }
    }
}
