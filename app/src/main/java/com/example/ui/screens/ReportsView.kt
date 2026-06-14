package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import android.app.Activity
import com.example.data.local.AppSettings
import com.example.data.local.TransactionDb
import com.example.domain.DateUtils
import com.example.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsView(
    transactions: List<TransactionDb>,
    settings: AppSettings,
    currencySymbol: String
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf("MONTHLY") } // DAILY, WEEKLY, MONTHLY, YEARLY, ALL, CUSTOM

    var customStartDateMs by remember { mutableStateOf<Long?>(null) }
    var customEndDateMs by remember { mutableStateOf<Long?>(null) }
    var customDays by remember { mutableStateOf<Int?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var isChartExpanded by remember { mutableStateOf(false) }

    val systemInDarkMode = androidx.compose.foundation.isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = true // Dark texts for Ivory background
            insetsController.isAppearanceLightNavigationBars = !systemInDarkMode // Dark texts usually
        }
    }

    val calendar = Calendar.getInstance()
    val nowMs = System.currentTimeMillis()

    // Filter transactions based on selected period
    val filteredTransactions = remember(transactions, selectedPeriod, customStartDateMs, customEndDateMs, customDays) {
        val currentMs = System.currentTimeMillis()
        transactions.filter { tx ->
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
    }

    // Split income / expense lists
    val incomes = filteredTransactions.filter { it.type == "INCOME" }
    val expenses = filteredTransactions.filter { it.type == "EXPENSE" }

    val totalIncome = remember(incomes) {
        incomes.fold(BigDecimal.ZERO) { acc, tx -> acc.add(BigDecimal.valueOf(tx.amount)) }
    }
    val totalExpense = remember(expenses) {
        expenses.fold(BigDecimal.ZERO) { acc, tx -> acc.add(BigDecimal.valueOf(tx.amount)) }
    }
    val netSavings = totalIncome.subtract(totalExpense)

    // Calculate category percentages for Expenses
    val categoryTotals = remember(expenses) {
        val map = mutableMapOf<String, BigDecimal>()
        for (tx in expenses) {
            val key = tx.category
            val amount = BigDecimal.valueOf(tx.amount)
            map[key] = (map[key] ?: BigDecimal.ZERO).add(amount)
        }
        map.toList().sortedByDescending { it.second }
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

    // Text formatter helper
    fun formatVal(bd: BigDecimal): String {
        return try {
            val formatter = java.text.DecimalFormat("#,##0")
            val formatted = formatter.format(bd)
            "$formatted $currencySymbol"
        } catch (e: Exception) {
            "$bd $currencySymbol"
        }
    }

    // Share report as formatted text
    fun shareReportText() {
        val periodName = when(selectedPeriod) {
            "DAILY" -> "اليومي"
            "WEEKLY" -> "الأسبوعي"
            "MONTHLY" -> "الشهري"
            "YEARLY" -> "السنوي"
            "CUSTOM" -> "المخصص"
            else -> "الشامل"
        }
        val builder = StringBuilder()
        builder.append("📊 *تقرير السجل المالي $periodName*\n\n")
        builder.append("💰 *إجمالي الإيرادات (الوارد):* ${formatVal(totalIncome)}\n")
        builder.append("🛒 *إجمالي النفقات:* ${formatVal(totalExpense)}\n")
        builder.append("📉 *صافي المحصول المتبقي:* ${formatVal(netSavings)}\n\n")
        
        if (categoryTotals.isNotEmpty()) {
            builder.append("📂 *تفصيل المصروفات حسب الأبواب:*\n")
            categoryTotals.forEach { (cat, amt) ->
                val prc = if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                    amt.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 1, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                builder.append("- *$cat:* $amt $currencySymbol ($prc%)\n")
            }
        } else {
            builder.append("بفضل الله، لا يوجد منصرفات مسجلة خلال هذه الفترة 🌸\n")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, builder.toString())
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة تقرير الميزان عبر"))
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
                    modifier = Modifier.fillMaxHeight(0.8f) // limits height
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
                                // DatePicker gives UTC midnight. To include the full end day:
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(IvoryBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title Banner
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "كشف التدابير الإحصائي والتقارير 📊",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "متابعة تدفق بركة الميزانية بنظرة شاملة ونقية",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Period Filtering Tabs containing White backdrop with thin borders
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFF1F1EF), RoundedCornerShape(18.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Daily
                PeriodTab(
                    title = "يومي",
                    selected = selectedPeriod == "DAILY",
                    modifier = Modifier.width(60.dp)
                ) {
                    selectedPeriod = "DAILY"
                }
                // Weekly
                PeriodTab(
                    title = "أسبوعي",
                    selected = selectedPeriod == "WEEKLY",
                    modifier = Modifier.width(70.dp)
                ) {
                    selectedPeriod = "WEEKLY"
                }
                // Monthly
                PeriodTab(
                    title = "شهري",
                    selected = selectedPeriod == "MONTHLY",
                    modifier = Modifier.width(60.dp)
                ) {
                    selectedPeriod = "MONTHLY"
                }
                // Yearly
                PeriodTab(
                    title = "سنوي",
                    selected = selectedPeriod == "YEARLY",
                    modifier = Modifier.width(60.dp)
                ) {
                    selectedPeriod = "YEARLY"
                }
                // Custom
                PeriodTab(
                    title = "مخصص",
                    selected = selectedPeriod == "CUSTOM",
                    modifier = Modifier.width(64.dp)
                ) {
                    selectedPeriod = "CUSTOM"
                    showCustomDialog = true
                }
                // All time
                PeriodTab(
                    title = "شامل",
                    selected = selectedPeriod == "ALL",
                    modifier = Modifier.width(60.dp)
                ) {
                    selectedPeriod = "ALL"
                }
            }
        }

        // Numerical Abstract Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F1EF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Income (Warrd)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("الوارد الكلي 🔵", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(formatVal(totalIncome), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color(0xFFF1F1EF)))

                    // Expenses
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("المصروفات 🔴", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(formatVal(totalExpense), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SoftRed)
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color(0xFFF1F1EF)))

                    // Savings / Net
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("صافي المتبقي 🟢", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            formatVal(netSavings),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (netSavings.compareTo(BigDecimal.ZERO) >= 0) SoftGreen else SoftRed
                        )
                    }
                }
            }
        }

        // Donut Pie Graphic
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F1EF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isChartExpanded = !isChartExpanded }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "التوزيع البياني الدائري للمصروفات 🎂",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isChartExpanded) "▲" else "▼",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    AnimatedVisibility(
                        visible = isChartExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (categoryTotals.isEmpty()) {
                                Column(
                                    modifier = Modifier.padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("🎂", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "لا توجد بيانات نفقات خلال هذه الفترة لعرض الهياكل التوزيعية لها.",
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                // Drawing Donut Chart on Native Canvas
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(210.dp)
                                ) {
                                    Canvas(modifier = Modifier.size(175.dp)) {
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
                                                style = Stroke(width = 32.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                            currentStartAngle += sweepAngle
                                        }
                                    }

                                    // Center text summary inside Donut hole
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "إجمالي المنصرف",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = formatVal(totalExpense),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shared Button
        item {
            Button(
                onClick = { shareReportText() },
                colors = ButtonDefaults.buttonColors(containerColor = CoralAccent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "مشاركة")
                Spacer(modifier = Modifier.width(12.dp))
                Text("تشارك كشف الحساب والتقرير (واتساب) 📊", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Category Detail Breakdown Item List
        if (categoryTotals.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "تفصيل الأبواب المفتوحة للصرفية 📁",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }
            }

            items(categoryTotals) { (cat, amount) ->
                val idx = categoryTotals.indexOfFirst { it.first == cat }
                val col = segmentColors[idx % segmentColors.size]
                val pct = if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                    amount.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 1, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F1EF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Numeric and Percentage
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = formatVal(amount),
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                            Text(
                                text = "يمثل $pct% من المنصرف",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        // Category label with Color bullet
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = cat,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 12.dp)
                            )

                            // Custom colored bullet (bullet shaped as soft capsule tag)
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(col)
                            )
                        }
                    }
                }
            }

            // Margin bottom
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
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
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}
