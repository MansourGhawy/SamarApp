package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.*
import com.example.domain.DateUtils
import com.example.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import android.app.Activity
import com.example.ui.viewmodel.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import java.math.BigDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainLedgerView(
    viewModel: FinanceViewModel,
    monthlyLedger: List<MonthLedger>,
    totalCash: BigDecimal,
    commitments: List<FixedCommitment>,
    settings: AppSettings,
    onBackIntercept: (Boolean) -> Unit, // intercepts back to cancel selection mode if active
    onMenuClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false // White text/icons
            insetsController.isAppearanceLightNavigationBars = false // White/transparent look
        }
    }

    val lazyListState = rememberLazyListState()
    val collapseFraction by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offset = lazyListState.firstVisibleItemScrollOffset.toFloat()
                (offset / 180f).coerceIn(0f, 1f)
            }
        }
    }

    // Transaction dialog states
    var showTxDialog by remember { mutableStateOf(false) }
    var txDialogType by remember { mutableStateOf("EXPENSE") } // INCOME or EXPENSE
    var editingTransaction by remember { mutableStateOf<TransactionDb?>(null) }

    // Licensing & Activation states
    val isActivated by viewModel.isActivatedState.collectAsState()
    val totalTransactionsCount by viewModel.totalTransactionsCount.collectAsState()
    val deviceId by viewModel.deviceIdState.collectAsState()
    val showActivationRequired by viewModel.showActivationRequired.collectAsState()

    var showActivationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(totalTransactionsCount, isActivated) {
        if (viewModel.isTrialExpired()) {
            showActivationDialog = true
        }
    }

    LaunchedEffect(showActivationRequired) {
        if (showActivationRequired) {
            showActivationDialog = true
            viewModel.showActivationRequired.value = false
        }
    }

    // Floating commitment dialog
    var showCommitmentsListSheet by remember { mutableStateOf(false) }
    val commitmentsScaleFraction = remember { Animatable(0f) }
    LaunchedEffect(showCommitmentsListSheet) {
        if (showCommitmentsListSheet) {
            commitmentsScaleFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            )
        } else {
            commitmentsScaleFraction.snapTo(0f)
        }
    }
    var reorderCommitmentTarget by remember { mutableStateOf<FixedCommitment?>(null) }
    var showCommitmentDialog by remember { mutableStateOf(false) }
    var editingCommitment by remember { mutableStateOf<FixedCommitment?>(null) }

    // Pop-up dialog day state tracking key
    var activeDayKey by remember { mutableStateOf<String?>(null) }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResultsState.collectAsState()

    // Reactive active day resolver helper
    val activeDayLedger = remember(activeDayKey, monthlyLedger) {
        if (activeDayKey == null) null
        else {
            monthlyLedger.flatMap { ml ->
                ml.days.map { day -> "${ml.monthKey}_${day.dayNumber}" to day }
            }.find { it.first == activeDayKey }?.second
        }
    }

    // Selection/Deletion mode states
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTxIds = remember { mutableStateListOf<String>() }
    var collapsedMonths by remember { mutableStateOf(setOf<String>()) }
    var isHabayebActive by remember { mutableStateOf(false) }
    var habayebButtonCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var isMakhzanActive by remember { mutableStateOf(false) }
    var makhzanButtonCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    var isDaySelectionMode by remember { mutableStateOf(false) }
    val selectedDayKeys = remember { mutableStateListOf<String>() }
    var showDeleteDaysDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode || isDaySelectionMode || activeDayKey != null || showSearch || isHabayebActive || isMakhzanActive) {
        if (isHabayebActive) {
            isHabayebActive = false
        } else if (isMakhzanActive) {
            isMakhzanActive = false
        } else if (isSelectionMode) {
            selectedTxIds.clear()
            isSelectionMode = false
        } else if (isDaySelectionMode) {
            selectedDayKeys.clear()
            isDaySelectionMode = false
        } else if (activeDayKey != null) {
            activeDayKey = null
        } else if (showSearch) {
            showSearch = false
        }
    }

    // Export public clear selection trigger
    fun clearSelection() {
        selectedTxIds.clear()
        selectedDayKeys.clear()
        isSelectionMode = false
        isDaySelectionMode = false
    }

    // Daily budget alerts
    val dailyComp by viewModel.dailyExpenseComparisonState.collectAsState()
    val todayExp = dailyComp.first
    val yesterdayExp = dailyComp.second
    val diffExp = todayExp.subtract(yesterdayExp)

    val linkHabayebDebts by viewModel.linkHabayebDebtsState.collectAsState()
    val habayebOwedByThemTotal by viewModel.habayebOwedByThemTotalState.collectAsState()

    // Precompute commitments coverage details
    val computedCommitments = remember(commitments, totalCash, linkHabayebDebts, habayebOwedByThemTotal) {
        var remainingCash = totalCash.toDouble()
        if (linkHabayebDebts) {
            remainingCash += habayebOwedByThemTotal
        }
        commitments.map { fc ->
            val target = fc.targetAmount
            val alreadyPaid = fc.currentProgress
            val needed = (target - alreadyPaid).coerceAtLeast(0.0)
            val allocatedFromCash = if (remainingCash >= needed) {
                remainingCash -= needed
                needed
            } else if (remainingCash > 0) {
                val temp = remainingCash
                remainingCash = 0.0
                temp
            } else {
                0.0
            }
            val remaining = needed - allocatedFromCash
            val totalCovered = alreadyPaid + allocatedFromCash
            Triple(fc, totalCovered, remaining)
        }
    }

    if (showDeleteDaysDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDaysDialog = false },
            title = {
                Text(
                    text = "تأكيد الحذف الشامل ⚠️",
                    fontWeight = FontWeight.Bold,
                    color = SoftRed,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف الأيام المحددة وكافة المعاملات المالية التابعة لها نهائياً؟ هذه العملية لا يمكن التراجع عنها وتؤثر على صافي الميزانية.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDaysDialog = false
                        scope.launch {
                            val txsToDelete = mutableListOf<String>()
                            monthlyLedger.forEach { ml ->
                                ml.days.forEach { day ->
                                    val dayKey = "${ml.monthKey}_${day.dayNumber}"
                                    if (selectedDayKeys.contains(dayKey)) {
                                        day.transactions.forEach { tx ->
                                            txsToDelete.add(tx.id)
                                        }
                                    }
                                }
                            }
                            txsToDelete.forEach { txId ->
                                viewModel.deleteTransactionById(txId)
                            }
                            selectedDayKeys.clear()
                            isDaySelectionMode = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed)
                ) {
                    Text("حذف شامل نهائي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDaysDialog = false }) {
                    Text("إلغاء", color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(IvoryBackground)) {
        // High-fidelity pinned collapsible top header when scrolled - Redesigned Clock to Top-Right
        if (collapseFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(10f)
                    .background(EmeraldPrimary)
                    .statusBarsPadding()
                    .height(48.dp)
                    .alpha(collapseFraction)
            ) {
                // Centered App Name
                Text(
                    text = "ميزان الدار",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start components - القائمة الجانبية وساعة السجل (Menu and History)
                    IconButton(
                        onClick = { onMenuClick() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "القائمة الجانبية",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Left Corner (RTL end component): Search and Habayeb shortcuts in a Row
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search shortcut
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSearch = true
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Habayeb Wallet shortcut
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isHabayebActive = true
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "حسابات العملاء",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Compact Header + Total Cash + Coverage Ratio
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                        .background(EmeraldPrimary)
                        .statusBarsPadding()
                        .padding(bottom = 4.dp)
                ) {
                    val topRowHeight = if (isDaySelectionMode) {
                        52.dp
                    } else {
                        (46 * (1f - collapseFraction)).dp
                    }
                    val topRowAlpha = if (isDaySelectionMode) 1f else (1f - collapseFraction)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topRowHeight)
                            .alpha(topRowAlpha)
                    ) {
                        if (isDaySelectionMode) {
                            // High-fidelity Multi-Select Days Selection Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cancel button & Select All label/button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            isDaySelectionMode = false
                                            selectedDayKeys.clear()
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إلغاء",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val allKeys = monthlyLedger.flatMap { ml -> ml.days.map { "${ml.monthKey}_${it.dayNumber}" } }
                                            if (selectedDayKeys.size == allKeys.size) {
                                                selectedDayKeys.clear()
                                            } else {
                                                selectedDayKeys.clear()
                                                selectedDayKeys.addAll(allKeys)
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        val allKeys = monthlyLedger.flatMap { ml -> ml.days.map { "${ml.monthKey}_${it.dayNumber}" } }
                                        Text(
                                            text = if (selectedDayKeys.size == allKeys.size) "إلغاء الكل" else "تحديد الكل 📋",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Center text: Selection status
                                val selectedCountText = when (selectedDayKeys.size) {
                                    1 -> "تم تحديد يوم واحد"
                                    2 -> "تم تحديد يومين"
                                    else -> "تم تحديد ${selectedDayKeys.size} أيام"
                                }
                                Text(
                                    text = selectedCountText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )

                                // Right portion: Delete Button
                                IconButton(
                                    onClick = {
                                        if (selectedDayKeys.isNotEmpty()) {
                                            showDeleteDaysDialog = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف الأيام المحددة",
                                        tint = if (selectedDayKeys.isEmpty()) Color.White.copy(alpha = 0.4f) else Color(0xFFFF8A80),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        } else {
                            // Header Box (RTL-optimized & symmetric) - Centering the title perfectly to prevent any leaning/biasing
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "HeaderTransition")

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Menu item (☰)
                                    IconButton(
                                        onClick = { onMenuClick() },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "القائمة الجانبية",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                }

                                // Center portion: App name & description styled as a premium light subtitle - Mathematically centered in the Box
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(top = 12.dp, bottom = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "ميزان الدار",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "صمام أمان التدبير المالي",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Light,
                                        color = Color(0xFFB2DFDB)
                                    )
                                }

                                // Left portion: Modern Search Button to avoid any overlay completely
                                IconButton(
                                    onClick = { showSearch = true },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "بحث",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Total Cash Center - Upgraded Luxury Glassmorphic layout (Tighter padding and sizes)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Glassmorphic Card Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "المبلغ المتاح الفعلي",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFEF08A) // Glowing soft gold
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
                                    IconButton(
                                        onClick = { viewModel.togglePrivacyMode() },
                                        modifier = Modifier.size(24.dp).padding(end = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "عرض المبالغ",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                    Text(
                                        text = if (isPrivacyMode) "*****" else viewModel.formatCurrency(totalCash, settings.currencySymbol),
                                        fontSize = 24.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        // Redundant local capital bar removed as requested.

                        // Coverage Bar (More compact spacing)
                        if (commitments.isNotEmpty()) {
                            val totalTarget = commitments.sumOf { it.targetAmount }
                            val totalAllocated = computedCommitments.sumOf { it.second }
                            val percentFloat = if (totalTarget > 0.0) {
                                (totalAllocated / totalTarget).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            val cashPercentFloat = remember(commitments, totalCash) {
                                if (totalTarget > 0.0) {
                                    var remainingCash = totalCash.toDouble()
                                    val allocated = commitments.sumOf { fc ->
                                        val needed = (fc.targetAmount - fc.currentProgress).coerceAtLeast(0.0)
                                        if (remainingCash >= needed) {
                                            remainingCash -= needed
                                            needed
                                        } else if (remainingCash > 0) {
                                            val temp = remainingCash
                                            remainingCash = 0.0
                                            temp
                                        } else {
                                            0.0
                                        }
                                    }
                                    ((commitments.sumOf { it.currentProgress } + allocated) / totalTarget).toFloat().coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تضمين الديون المستحقة 🔗",
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Switch(
                                    checked = linkHabayebDebts,
                                    onCheckedChange = { viewModel.toggleLinkHabayebDebts(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFF3E8FF),
                                        checkedTrackColor = Color(0xFF8B5CF6),
                                        uncheckedThumbColor = Color(0xFFE2E8F0),
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.height(18.dp).scale(0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f))
                                        .border(1.dp, Color(0xFF00E676), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${(percentFloat * 100).toInt()}%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF00E676)
                                    )
                                }
                                Text(
                                    text = "نسبة تغطية الالتزامات",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                            
                            val neonGradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E676), // Neon green
                                    Color(0xFF00B0FF)  // Neon light blue
                                )
                            )

                            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                )
                                if (linkHabayebDebts && percentFloat > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(percentFloat)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFFC4B5FD))
                                    )
                                }
                                val frontPercent = if (linkHabayebDebts) cashPercentFloat else percentFloat
                                if (frontPercent > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(frontPercent)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(neonGradient)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Commitments Summary Cards - Row 1 of the 2x2 Grid block
            if (commitments.isNotEmpty()) {
                item {
                    val totalRemainingCommitments = computedCommitments.sumOf { it.third }
                    val allocatedFromCashTotal = computedCommitments.sumOf {
                        val needed = (it.first.targetAmount - it.first.currentProgress).coerceAtLeast(0.0)
                        needed - it.third
                    }
                    val netAmount = (totalCash.toDouble() - allocatedFromCashTotal).coerceAtLeast(0.0)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp), // perfectly balanced 5dp vertical margin padding to align with Row 1
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: Net Amount Capsule matching Row 1 style & size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEDF7ED)) // Light Pastel Green
                                .border(
                                    1.dp,
                                    Color(0xFFC8E6C9), // Soft green boundary
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "❇️ الصافي: " + viewModel.formatCurrency(java.math.BigDecimal.valueOf(netAmount), settings.currencySymbol),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32), // Clear green readability
                                textAlign = TextAlign.Center
                            )
                        }

                        // Card 2: Remaining Commitments Capsule matching Row 1 style & size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFDEDED)) // Light Pastel Red
                                .border(
                                    1.dp,
                                    Color(0xFFFFCDD2), // Soft red boundary
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🛑 بقي للالتزامات: " + viewModel.formatCurrency(java.math.BigDecimal.valueOf(totalRemainingCommitments), settings.currencySymbol),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828), // Clear red readability
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Quick Navigation Widgets - Row 2 of the Balanced 2x2 Grid Layout
            item {
                val habayebOwedByThem by viewModel.habayebOwedByThemTotalState.collectAsState()
                val makhzanCapitalProduct by viewModel.makhzanCapitalState.collectAsState()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp), // perfectly balanced margin padding
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Chip 1: Habayeb Screen Shortcut (Glassmorphic)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                Color(0xFFC084FC).copy(alpha = 0.4f), // Soft purple boundary
                                RoundedCornerShape(12.dp)
                            )
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                val centerX = pos.x + coordinates.size.width / 2f
                                val centerY = pos.y + coordinates.size.height / 2f
                                habayebButtonCenter = androidx.compose.ui.geometry.Offset(centerX, centerY)
                            }
                            .clickable { isHabayebActive = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👥 ديون لي: " + viewModel.formatDoubleCurrency(habayebOwedByThem, settings.currencySymbol),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE9D5FF), // Soft lavender gray
                            textAlign = TextAlign.Center
                        )
                    }

                    // Chip 2: Makhzan Screen Shortcut (Glassmorphic)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                Color(0xFF6366F1).copy(alpha = 0.4f), // Indigo / Nil blue
                                RoundedCornerShape(12.dp)
                            )
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                val centerX = pos.x + coordinates.size.width / 2f
                                val centerY = pos.y + coordinates.size.height / 2f
                                makhzanButtonCenter = androidx.compose.ui.geometry.Offset(centerX, centerY)
                            }
                            .clickable { isMakhzanActive = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📦 المخزن: " + viewModel.formatDoubleCurrency(makhzanCapitalProduct, settings.currencySymbol),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC7D2FE), // Soft indigo-sky blue
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // No data placeholder
            if (monthlyLedger.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📓", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "السجل المالي فارغ حالياً!\nقم بتسجيل الإيرادات والمصروفات لتتبع ميزانيتك.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Ledger Month-by-month list
            monthlyLedger.forEachIndexed { monthIdx, monthLedger ->
                val isCollapsed = collapsedMonths.contains(monthLedger.monthKey)

                // Month Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                collapsedMonths = if (isCollapsed) {
                                    collapsedMonths - monthLedger.monthKey
                                } else {
                                    collapsedMonths + monthLedger.monthKey
                                }
                            }
                            .padding(start = 14.dp, end = 14.dp, top = if (monthIdx == 0) 2.dp else 12.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp).padding(end = 4.dp)
                            )
                            Text(
                                text = if (monthIdx == 0) "السجل اليومي 📖" else "حركات شهر:",
                                color = EmeraldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = monthLedger.monthName,
                                fontSize = 9.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.weight(1f).padding(start = 12.dp), color = Color(0xFFE2E8F0))
                    }
                }

                if (!isCollapsed) {
                    // Days list inside this month
                    items(monthLedger.days) { dayLedger ->
                    val dayKey = "${monthLedger.monthKey}_${dayLedger.dayNumber}"
                    val isDaySelected = selectedDayKeys.contains(dayKey)

                    // Alternating gentle cash flow background colors (beautiful light gradients)
                    val cardBrush = if (isDaySelected) {
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFFE6F4EA), Color(0xFFD1FAE5)) // gentle emerald tint when selected
                        )
                    } else if (dayLedger.netAmount.compareTo(java.math.BigDecimal.ZERO) >= 0) {
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFFF3FAF5), Color.White)
                        )
                    } else {
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFFFFF7F7), Color.White)
                        )
                    }

                    // Tidy minimal Day Card wrapper with gorgeous borders
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = if (isDaySelected) {
                            androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981))
                        } else {
                            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEC))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isDaySelectionMode) {
                                        if (selectedDayKeys.contains(dayKey)) {
                                            selectedDayKeys.remove(dayKey)
                                            if (selectedDayKeys.isEmpty()) {
                                                isDaySelectionMode = false
                                            }
                                        } else {
                                            selectedDayKeys.add(dayKey)
                                        }
                                    } else {
                                        activeDayKey = dayKey
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!isDaySelectionMode && !isSelectionMode) {
                                        isDaySelectionMode = true
                                        selectedDayKeys.add(dayKey)
                                    } else if (isDaySelectionMode) {
                                        if (selectedDayKeys.contains(dayKey)) {
                                            selectedDayKeys.remove(dayKey)
                                            if (selectedDayKeys.isEmpty()) {
                                                isDaySelectionMode = false
                                            }
                                        } else {
                                            selectedDayKeys.add(dayKey)
                                        }
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardBrush)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Net balance indicator & sleek interactive detail label
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = (if (dayLedger.netAmount.compareTo(java.math.BigDecimal.ZERO) > 0) "+" else "") +
                                            viewModel.formatCurrency(dayLedger.netAmount, settings.currencySymbol),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = if (dayLedger.netAmount.compareTo(java.math.BigDecimal.ZERO) >= 0) SoftGreen else SoftRed
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray.copy(alpha = 0.05f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text("التفاصيل", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("📑", fontSize = 11.sp)
                                }
                            }

                            // Right: Day title and Date description along with circular Selection indicator (Checkbox)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "يوم ${dayLedger.dayNumber} - ${dayLedger.dayOfWeek}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = dayLedger.fullDate,
                                        fontSize = 9.sp,
                                        color = Color.Gray.copy(alpha = 0.7f)
                                    )
                                }

                                if (isDaySelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(if (isDaySelected) Color(0xFF10B981) else Color.White)
                                            .border(1.5.dp, Color(0xFF10B981), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isDaySelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                } // End of if (!isCollapsed)

                // Month Transition Separator
                if (monthIdx < monthlyLedger.size - 1) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MonthTransitionLine()
                        }
                    }
                }
            }

            // Bottom spacing past absolute FAB overlays (compressed rhythm)
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // Floating action buttons (Dual floating configuration) - Compressed & modern
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp, start = 12.dp, end = 12.dp) // Pushed lower closer to sleeker bottom bar
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Delete selection items floating banner if active
            if (isSelectionMode && selectedTxIds.isNotEmpty()) {
                Button(
                    onClick = {
                        scope.launch {
                            selectedTxIds.forEach { id ->
                                viewModel.deleteTransactionById(id)
                            }
                            delay(200)
                            clearSelection()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(46.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف سجل المحددة", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "حذف السجلات المحددة (${selectedTxIds.size}) نهائياً 🗑️",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (!isSelectionMode) {
                // Breathe/Pulsing Glow Effect for Goals & Commitments
                val pulsingTransition = rememberInfiniteTransition(label = "CommitmentPulsingTransition")
                val scalePulse by pulsingTransition.animateFloat(
                    initialValue = 0.98f,
                    targetValue = 1.02f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ScalePulse"
                )
                val borderGlow by pulsingTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "BorderGlow"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scalePulse
                            scaleY = scalePulse
                            transformOrigin = TransformOrigin(0.5f, 1.0f)
                        }
                        .clip(CircleShape)
                        .background(Color(0xFFF1F8E9)) // Soft olive-green tint
                        .border(
                            width = 1.dp,
                            color = Color(0xFF33691E).copy(alpha = borderGlow), // Pulsing olive-green border
                            shape = CircleShape
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCommitmentsListSheet = true
                        }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎯 الأهداف والالتزامات",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20) // Deep Dark Olive/Green text
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(0.95f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Add Income Button (Add Finance) - Sleek Pill style
                Button(
                    onClick = {
                        editingTransaction = null
                        txDialogType = "INCOME"
                        showTxDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftGreen),
                    shape = RoundedCornerShape(24.dp), // Modern Full-Pill Curve
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp) // Sleek, compressed button height
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة إيراد", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة إيراد 💰", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Add Expense Button (Record Platform Expense) - Sleek Pill style
                Button(
                    onClick = {
                        editingTransaction = null
                        txDialogType = "EXPENSE"
                        showTxDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAccent),
                    shape = RoundedCornerShape(24.dp), // Modern Full-Pill Curve
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp) // Sleek, compressed button height
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "إضافة مصروف", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مصروف 🛒", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    // Modal dialog for Recording Income / Expenses
    if (showTxDialog) {
        var numAmount by remember { mutableStateOf(editingTransaction?.amount?.toString() ?: "") }
        var descriptionStr by remember { mutableStateOf(editingTransaction?.description ?: "") }
        var categoryName by remember { mutableStateOf(editingTransaction?.category ?: if (txDialogType == "INCOME") "الواردات الإجمالية 💰" else "منصرف") }
        var categoryEmoji by remember { mutableStateOf("") }

        var showCalcPopup by remember { mutableStateOf(false) }
        var isSavingTx by remember { mutableStateOf(false) }
        var showCategoryPickerSheet by remember { mutableStateOf(false) }

        val focusRequester = remember { FocusRequester() }
        val descriptionFocusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        // Autofocus amount on launch
        LaunchedEffect(Unit) {
            delay(150)
            focusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { showTxDialog = false },
            title = {
                Text(
                    text = if (editingTransaction != null) "تعديل المعاملة المالية ✍️" else if (txDialogType == "INCOME") "إضافة إيراد جديد 💰" else "إضافة مصروف جديد 🛒",
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    // Autofocus Amount TF
                    OutlinedTextField(
                        value = numAmount,
                        onValueChange = { numAmount = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { descriptionFocusRequester.requestFocus() }
                        ),
                        label = { Text("القيمة المالية (" + settings.currencySymbol + ")") },
                        singleLine = true,
                        leadingIcon = {
                            IconButton(onClick = { showCalcPopup = true }) {
                                Icon(Icons.Default.Build, contentDescription = "حاسبة", tint = CoralAccent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description text input
                    OutlinedTextField(
                        value = descriptionStr,
                        onValueChange = { descriptionStr = it },
                        label = { Text("الوصف أو البيان (مثال: عشاء، للتموين)") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(descriptionFocusRequester),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category choosing button (replaces dropdown) - Removed for expenses as requested
                    if (txDialogType == "INCOME" && editingTransaction != null) {
                        Button(
                            onClick = { showCategoryPickerSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (categoryName.isNotBlank()) "التصنيف: $categoryName" else "اختر التصنيف المالي... 🏷️",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSavingTx && (numAmount.toDoubleOrNull() ?: 0.0) > 0,
                    onClick = {
                        if (isSavingTx) return@Button
                        isSavingTx = true

                        val amtParsed = numAmount.toDoubleOrNull() ?: 0.0
                        if (amtParsed > 0) {
                            if (editingTransaction != null) {
                                val tx = editingTransaction!!.copy(
                                    amount = amtParsed,
                                    description = descriptionStr,
                                    category = categoryName
                                )
                                viewModel.updateTransaction(tx)
                            } else {
                                viewModel.addTransaction(
                                    type = txDialogType,
                                    category = categoryName,
                                    amount = amtParsed,
                                    description = descriptionStr
                                )
                            }
                            showTxDialog = false
                        } else {
                            isSavingTx = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("حفظ المعاملة ✅")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTxDialog = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )

        if (showCategoryPickerSheet) {
            val customCats by viewModel.customCategoriesState.collectAsState()
            CategoryBottomSheet(
                schoolExpensesEnabled = settings.schoolExpensesEnabled,
                customCategories = customCats,
                onCategorySelected = { name, emoji ->
                    categoryName = "$name $emoji"
                    showCategoryPickerSheet = false
                },
                onAddCustomCategory = { name, emoji, tab ->
                    viewModel.saveCustomCategory(name, tab, emoji)
                },
                onDeleteCategory = { cat ->
                    viewModel.deleteCustomCategory(cat)
                },
                onDismiss = { showCategoryPickerSheet = false }
            )
        }

        // Custom Calculator popup trigger
        if (showCalcPopup) {
            CalculatorDialog(
                onDismiss = { showCalcPopup = false },
                onValueConfirmed = { calcResult ->
                    numAmount = calcResult.toString()
                    showCalcPopup = false
                }
            )
        }
    }

    if (showSearch) {
        SearchLedgerDialog(
            query = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            results = searchResults,
            formatCurrency = { amt -> viewModel.formatCurrency(java.math.BigDecimal.valueOf(amt), settings.currencySymbol) },
            onDismiss = { showSearch = false }
        )
    }

    // Modal dialog for Fixed commitments
    // Commitments List Popup Dialog
    if (showCommitmentsListSheet) {
        Dialog(onDismissRequest = { showCommitmentsListSheet = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .graphicsLayer(
                        scaleX = commitmentsScaleFraction.value,
                        scaleY = commitmentsScaleFraction.value,
                        alpha = commitmentsScaleFraction.value,
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { 
                                    editingCommitment = null
                                    showCommitmentDialog = true 
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFEF3C7))
                            ) {
                                Icon(Icons.Default.Add, "إضافة", tint = Color(0xFFD97706))
                            }
                            IconButton(
                                onClick = {
                                    val builder = StringBuilder()
                                    builder.append("🎯 *سجل الأهداف والالتزامات للحساب الرئيسي*\n\n")
                                    var idx = 1
                                    commitments.forEach { fc ->
                                        builder.append("$idx. ${fc.name} - ${viewModel.formatDoubleCurrency(fc.targetAmount, settings.currencySymbol)}\n")
                                        idx++
                                    }
                                    
                                    val totalReq = commitments.sumOf { it.targetAmount }
                                    val totalRemaining = computedCommitments.sumOf { it.third }
                                    
                                    builder.append("\n💰 *إجمالي المطلوب:* ${viewModel.formatDoubleCurrency(totalReq, settings.currencySymbol)}")
                                    builder.append("\n💵 *المبلغ المتوفر حالياً:* ${viewModel.formatCurrency(totalCash, settings.currencySymbol)}")
                                    builder.append("\n⏳ *المتبقي لإتمام الالتزامات:* ${viewModel.formatDoubleCurrency(totalRemaining, settings.currencySymbol)}")
                                    
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, builder.toString())
                                    }
                                    try {
                                        shareIntent.setPackage("com.whatsapp")
                                        context.startActivity(shareIntent)
                                    } catch (e: Exception) {
                                        shareIntent.setPackage(null)
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة عبر"))
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE5F6FD))
                            ) {
                                Icon(Icons.Default.Share, "مشاركة واتساب", tint = Color(0xFF0369A1), modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        Text(
                            text = "الأهداف والالتزامات",
                            fontWeight = FontWeight.ExtraBold,
                            color = EmeraldPrimary,
                            fontSize = 16.sp
                        )
                    }
                    
                    if (commitments.isEmpty()) {
                        Text(
                            text = "لا توجد أهداف أو التزامات مدونة حالياً. اضغط على (+) لتسجيلها 🎯",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    } else {
                        val totalTargetSum = commitments.sumOf { it.targetAmount }
                        val totalAllocatedSum = computedCommitments.sumOf { it.second }
                        val coveredCount = computedCommitments.count { it.third <= 0.0 }
                        
                        Text(
                            text = "تغطية الصندوق: ${coveredCount} من ${commitments.size} (${viewModel.formatDoubleCurrency(totalAllocatedSum, settings.currencySymbol)} / ${viewModel.formatDoubleCurrency(totalTargetSum, settings.currencySymbol)})",
                            fontSize = 11.sp,
                            color = if (totalAllocatedSum >= totalTargetSum) SoftGreen else Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxHeight(0.6f)
                        ) {
                            itemsIndexed(computedCommitments) { index, (fc, allocated, remaining) ->
                                val isCovered = remaining <= 0.0
                                
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left side actions & state
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                var dragOffset by remember { mutableFloatStateOf(0f) }
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            reorderCommitmentTarget = fc
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectDragGestures(
                                                                onDragStart = { _ -> dragOffset = 0f },
                                                                onDrag = { _, dragAmount ->
                                                                    dragOffset += dragAmount.y
                                                                    if (dragOffset > 70f) {
                                                                        dragOffset = 0f
                                                                        val pos = index + 2 // index+1 is next, but the user views indices starting at 1, so moving down means position = index+2
                                                                        if (pos <= commitments.size) {
                                                                            viewModel.reorderCommitment(fc, pos)
                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                        }
                                                                    } else if (dragOffset < -70f) {
                                                                        dragOffset = 0f
                                                                        val pos = index // Moving up means position = index
                                                                        if (pos >= 1) {
                                                                            viewModel.reorderCommitment(fc, pos)
                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                        }
                                                                    }
                                                                },
                                                                onDragEnd = { dragOffset = 0f }
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Menu, "تحريك", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        editingCommitment = fc
                                                        showCommitmentDialog = true
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, "تعديل", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteCommitment(fc.name)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, "حذف", tint = SoftRed, modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.Start) {
                                                if (isCovered) {
                                                    Text("مكتمل ✔️", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SoftGreen)
                                                } else {
                                                    Text("-${viewModel.formatDoubleCurrency(remaining, settings.currencySymbol)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SoftRed)
                                                    if (allocated > 0.0) {
                                                        Text("مغطى: ${viewModel.formatDoubleCurrency(allocated, settings.currencySymbol)}", fontSize = 9.sp, color = SoftGreen)
                                                    }
                                                }
                                            }
                                        }

                                        // Right side Name/Check
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    val neededToComplete = (fc.targetAmount - fc.currentProgress).coerceAtLeast(0.0)
                                                    val canAffordButNotCovered = !isCovered && totalCash.toDouble() >= neededToComplete
                                                    if (canAffordButNotCovered) {
                                                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                                        val alphaAnim by infiniteTransition.animateFloat(
                                                            initialValue = 0.2f,
                                                            targetValue = 1.0f,
                                                            animationSpec = infiniteRepeatable(
                                                                animation = tween(800, easing = LinearEasing),
                                                                repeatMode = RepeatMode.Reverse
                                                            ),
                                                            label = "alpha"
                                                        )
                                                        Text("🟢", modifier = Modifier.alpha(alphaAnim), fontSize = 10.sp)
                                                    }
                                                    Text(fc.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EmeraldPrimary)
                                                }
                                                Text("المستهدف: ${viewModel.formatDoubleCurrency(fc.targetAmount, settings.currencySymbol)}", fontSize = 10.sp, color = Color.Gray)
                                            }
                                            Checkbox(
                                                checked = isCovered,
                                                onCheckedChange = { checked ->
                                                    viewModel.saveCommitment(fc.name, fc.targetAmount, if (checked) fc.targetAmount else 0.0)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = SoftGreen, checkmarkColor = Color.White),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                commitmentsScaleFraction.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                                )
                                showCommitmentsListSheet = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تم", fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showCommitmentDialog) {
        val initialName = editingCommitment?.name ?: ""
        val initialTarget = editingCommitment?.targetAmount?.let { if (it > 0) it.toInt().toString() else "" } ?: ""
        val initialProgress = editingCommitment?.currentProgress?.let { if (it > 0) it.toInt().toString() else "" } ?: ""

        var obligationName by remember(editingCommitment) { mutableStateOf(initialName) }
        var targetAmtStr by remember(editingCommitment) { mutableStateOf(initialTarget) }
        var progressAmtStr by remember(editingCommitment) { mutableStateOf(initialProgress) }

        AlertDialog(
            onDismissRequest = { 
                showCommitmentDialog = false
                editingCommitment = null
            },
            title = {
                Text(
                    text = if (editingCommitment != null) "تعديل الهدف أو الالتزام 🎯" else "إضافة هدف أو التزام جديد 🎯",
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = obligationName,
                        onValueChange = { if (editingCommitment == null) obligationName = it },
                        enabled = (editingCommitment == null),
                        label = { Text("اسم الهدف أو الالتزام (مثال: الإيجار، قسط سيارة، ادخار)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
 
                    Spacer(modifier = Modifier.height(12.dp))
 
                    OutlinedTextField(
                        value = targetAmtStr,
                        onValueChange = { targetAmtStr = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("المبلغ المستهدف المطلوب") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
 
                    Spacer(modifier = Modifier.height(12.dp))
 
                    OutlinedTextField(
                        value = progressAmtStr,
                        onValueChange = { progressAmtStr = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("المبلغ المتوفر حالياً لهذا الهدف (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tar = targetAmtStr.toDoubleOrNull() ?: 0.0
                        val prg = progressAmtStr.toDoubleOrNull() ?: 0.0
                        if (obligationName.isNotBlank() && tar > 0) {
                            viewModel.saveCommitment(obligationName, tar, prg)
                            showCommitmentDialog = false
                            editingCommitment = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("حفظ الهدف أو الالتزام 🎯")
                }
            },
            dismissButton = {
                Row {
                    if (editingCommitment != null) {
                        TextButton(onClick = {
                            viewModel.deleteCommitment(editingCommitment!!.name)
                            showCommitmentDialog = false
                            editingCommitment = null
                        }) {
                            Text("حذف 🗑️", color = SoftRed)
                        }
                    }
                    TextButton(onClick = { 
                        showCommitmentDialog = false
                        editingCommitment = null
                    }) {
                        Text("إلغاء", color = Color.Gray)
                    }
                }
            }
        )
    }

    if (reorderCommitmentTarget != null) {
        var targetPositionStr by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { reorderCommitmentTarget = null },
            title = {
                Text(
                    "نقل الالتزام: ${reorderCommitmentTarget?.name}",
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.End) {
                    Text("أدخل رقم المكان الذي تريد نقله إليه (1 إلى ${commitments.size}):", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    LaunchedEffect(focusRequester) {
                        delay(500)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    OutlinedTextField(
                        value = targetPositionStr,
                        onValueChange = { 
                            targetPositionStr = it
                            errorMsg = ""
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        isError = errorMsg.isNotEmpty()
                    )
                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = SoftRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pos = targetPositionStr.toIntOrNull()
                        if (pos == null || pos < 1 || pos > commitments.size) {
                            errorMsg = "لا يوجد التزام بهذا الرقم."
                        } else {
                            viewModel.reorderCommitment(reorderCommitmentTarget!!, pos)
                            reorderCommitmentTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("تطبيق النقل", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { reorderCommitmentTarget = null }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }

    if (showActivationDialog) {
        var activationCodeInput by remember { mutableStateOf("") }
        var isCodeError by remember { mutableStateOf(false) }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { 
                showActivationDialog = false 
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header lock icon with modern glow
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "قفل",
                            tint = SoftRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "تفعيل ترخيص التطبيق 🔐",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "لقد انتهت الفترة التجريبية المجانية. يرجى إدخال مفتاح التفعيل الخاص بجهازك أدناه لمواصلة العمل بكامل الصلاحيات.\n\nملاحظة: إذا كان لديك مفتاح تفعيل دائم، يمكنك إعادة كتابة نفس المفتاح دائماً في حال احتجت لحذف التطبيق وإعادة تثبيته مستقبلاً على هذا الجهاز.",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Device ID Display
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("رمز الجهاز", deviceId)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "تم نسخ رمز الجهاز للمقطع 📋", Toast.LENGTH_SHORT).show()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "رمز جهازك الفني",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = deviceId,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF334155),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Soft Green WhatsApp Button
                    Button(
                        onClick = {
                            val msg = "مرحباً م/ منصور قطينه، أود تفعيل النسخة الكاملة لتطبيق ميزان الدار. رمز جهازي الفني هو: $deviceId"
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://api.whatsapp.com/send?phone=967774004399&text=" + android.net.Uri.encode(msg))
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "لا يمكن العثور على تطبيق واتساب 💬", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "💬 تواصل مع المطور عبر واتساب",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Input for Activation Code
                    OutlinedTextField(
                        value = activationCodeInput,
                        onValueChange = { 
                            activationCodeInput = it
                            isCodeError = false
                        },
                        label = { Text("أدخل كود التفعيل المزدوج", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = isCodeError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = Color.LightGray,
                            errorBorderColor = SoftRed
                        ),
                        placeholder = { Text("ACT-T-XXXXXXXX أو ACT-P-XXXXXXXX", color = Color.LightGray) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    if (isCodeError) {
                        Text(
                            text = "كود التفعيل غير صالح، يرجى التحقق وإعادة المحاولة ❌",
                            color = SoftRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Submit & Cancel Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { showActivationDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تصفح حالياً بروية", color = Color.Gray, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val cleanInput = activationCodeInput.trim().uppercase()
                                val success = viewModel.activateLicense(cleanInput)
                                if (success) {
                                    val isPermanentCode = cleanInput.startsWith("ACT-P-")
                                    val toastMsg = if (isPermanentCode) {
                                        "تم التفعيل الدائم لجهازك بنجاح وسيبقى مفعلاً حتى بعد إعادة تثبيت التطبيق! شكراً لك 🎉"
                                    } else {
                                        "تم التفعيل المؤقت لجهازك بنجاح! شكراً لك 🎉"
                                    }
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                                    showActivationDialog = false
                                    isCodeError = false
                                } else {
                                    isCodeError = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("تأكيد التفعيل ✅", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Interactive Custom Pop-up Dialog for Day Transactions (Chronological order)
    if (activeDayKey != null && activeDayLedger != null) {
        AlertDialog(
            onDismissRequest = { activeDayKey = null },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "دفتر حركات اليوم 📑",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeDayLedger.dayOfWeek}، اليوم ${activeDayLedger.dayNumber} - ${activeDayLedger.fullDate}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            text = {
                // Chronological order sorted automatically by time
                val sortedTxs = remember(activeDayLedger.transactions) {
                    activeDayLedger.transactions.sortedBy { it.timestamp }
                }

                if (sortedTxs.isEmpty()) {
                    LaunchedEffect(Unit) {
                        activeDayKey = null
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(sortedTxs) { tx ->
                            // Subtle alternating gentle color gradient tints
                            val itemBg = if (tx.type == "INCOME") {
                                Color(0xFFF3FAF5) // Soft positive green tint
                            } else {
                                Color(0xFFFFF7F7) // Soft negative pink/coral tint
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(itemBg)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Actions and Amount
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Quick Deletion
                                    IconButton(
                                        modifier = Modifier.size(28.dp),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.deleteTransactionById(tx.id)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف المعاملة",
                                            tint = SoftRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Quick Editing
                                    IconButton(
                                        modifier = Modifier.size(28.dp),
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            editingTransaction = tx
                                            txDialogType = tx.type
                                            showTxDialog = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل المعاملة",
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = (if (tx.type == "INCOME") "+" else "-") +
                                                    viewModel.formatDoubleCurrency(tx.amount, settings.currencySymbol),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (tx.type == "INCOME") SoftGreen else SoftRed
                                        )
                                        Text(
                                            text = DateUtils.formatTime24Or12(tx.timestamp),
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                // Right: Details and Emoji Category badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = tx.description.ifBlank { "بلا وصف كافٍ" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Right
                                        )
                                        if (tx.category.isNotBlank() && tx.category != "منصرف") {
                                            Text(
                                                text = tx.category,
                                                fontSize = 9.sp,
                                                color = Color.Gray,
                                                textAlign = TextAlign.Right
                                            )
                                        }
                                    }

                                    val parsedEmoji = extractEmoji(tx.category, if (tx.type == "INCOME") "💰" else "🛒")
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(getEmojiBgColor(parsedEmoji), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = parsedEmoji, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFF1F1EF))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Daily totals inside Dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "صافي حركة اليوم:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = (if (activeDayLedger.netAmount.compareTo(java.math.BigDecimal.ZERO) >= 0) "+" else "") +
                                    viewModel.formatCurrency(activeDayLedger.netAmount, settings.currencySymbol),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (activeDayLedger.netAmount.compareTo(java.math.BigDecimal.ZERO) >= 0) SoftGreen else SoftRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { activeDayKey = null },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("تم وإغلاق 💾")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val builder = StringBuilder()
                    val income = activeDayLedger.transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
                    val expense = activeDayLedger.transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                    builder.append("📑 *دفتر حركات اليوم: ${activeDayLedger.dayOfWeek}، ${activeDayLedger.fullDate}*\n\n")
                    builder.append("💰 *إجمالي ما دخل:* ${viewModel.formatDoubleCurrency(income, settings.currencySymbol)}\n")
                    builder.append("🛒 *إجمالي ما صُرف:* ${viewModel.formatDoubleCurrency(expense, settings.currencySymbol)}\n")
                    builder.append("___________________\n\n")
                    
                    val txs = activeDayLedger.transactions.sortedBy { it.timestamp }
                    if (txs.isEmpty()) {
                       builder.append("لا توجد حركات مسجلة لهذا اليوم.\n")
                    } else {
                       txs.forEach { tx ->
                           val icon = if (tx.type == "INCOME") "🟢 (+)" else "🔴 (-)"
                           builder.append("$icon ${tx.category} - ${viewModel.formatDoubleCurrency(tx.amount, settings.currencySymbol)}\n")
                           if (tx.description.isNotBlank()) {
                               builder.append("   📝 ${tx.description}\n")
                           }
                       }
                    }

                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, builder.toString())
                    }
                    try {
                        // Directly target WhatsApp
                        shareIntent.setPackage("com.whatsapp")
                        context.startActivity(shareIntent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        try {
                            shareIntent.setPackage("com.whatsapp.w4b") // Try WhatsApp Business
                            context.startActivity(shareIntent)
                        } catch (e2: android.content.ActivityNotFoundException) {
                            // Fallback to normal share picker
                            shareIntent.setPackage(null)
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة عبر"))
                        }
                    }
                }) {
                    Text("مشاركة واتساب 💬", color = Color(0xFF25D366), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- Container Transform / Shared Bounds Motion Screen Overlay ---
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(isHabayebActive) {
        if (isHabayebActive) {
            animProgress.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        } else {
            animProgress.animateTo(0f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
    }

    if (animProgress.value > 0f) {
        val revealCenter = if (habayebButtonCenter != androidx.compose.ui.geometry.Offset.Zero) {
            habayebButtonCenter
        } else {
            androidx.compose.ui.geometry.Offset(250f, 400f) // comfortable default
        }
        val isRelativeReveal = (habayebButtonCenter == androidx.compose.ui.geometry.Offset.Zero)

        val density = androidx.compose.ui.platform.LocalDensity.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val pivotX = if (isRelativeReveal) 0.5f else (revealCenter.x / screenWidthPx).coerceIn(0f, 1f)
        val pivotY = if (isRelativeReveal) 0.5f else (revealCenter.y / screenHeightPx).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = animProgress.value
                    scaleX = animProgress.value
                    scaleY = animProgress.value
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                }
                .clip(CircularRevealShape(animProgress.value, revealCenter, isRelative = isRelativeReveal))
        ) {
            HabayebScreen(
                viewModel = viewModel,
                onClose = {
                    scope.launch {
                        isHabayebActive = false
                    }
                }
            )
        }
    }

    // --- Makhzan Container Transform / Shared Bounds Motion Screen Overlay ---
    val makhzanAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(isMakhzanActive) {
        if (isMakhzanActive) {
            makhzanAnimProgress.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        } else {
            makhzanAnimProgress.animateTo(0f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
    }

    if (makhzanAnimProgress.value > 0f) {
        val revealCenter = if (makhzanButtonCenter != androidx.compose.ui.geometry.Offset.Zero) {
            makhzanButtonCenter
        } else {
            androidx.compose.ui.geometry.Offset(450f, 400f) // comfortable default
        }
        val isRelativeReveal = (makhzanButtonCenter == androidx.compose.ui.geometry.Offset.Zero)

        val density = androidx.compose.ui.platform.LocalDensity.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val pivotX = if (isRelativeReveal) 0.5f else (revealCenter.x / screenWidthPx).coerceIn(0f, 1f)
        val pivotY = if (isRelativeReveal) 0.5f else (revealCenter.y / screenHeightPx).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = makhzanAnimProgress.value
                    scaleX = makhzanAnimProgress.value
                    scaleY = makhzanAnimProgress.value
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                }
                .clip(CircularRevealShape(makhzanAnimProgress.value, revealCenter, isRelative = isRelativeReveal))
        ) {
            MakhzanScreen(
                viewModel = viewModel,
                onClose = {
                    scope.launch {
                        isMakhzanActive = false
                    }
                }
            )
        }
    }
}

// Budget Advice message generator based on comparison
@Composable
fun BudgetAdviceBanner(diffExp: BigDecimal) {
    val showBanner = diffExp.compareTo(BigDecimal.ZERO) > 0

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (showBanner) SoftRed.copy(alpha = 0.08f) else SoftGreen.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (showBanner) {
                Text(
                    text = "مصروفات اليوم أعلى من الأمس بـ $diffExp. يرجى مراجعة نفقاتك لضمان تحقيق أهداف الميزانية.",
                    fontSize = 12.sp,
                    color = SoftRed,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Right,
                    lineHeight = 16.sp
                )
            } else {
                Text(
                    text = "الوضع المالي مستقر وطيب للدار اليوم 🌸. بارك الله في أرزاق بيتكم وعمركم بكل فضل ويسر ورضا.",
                    fontSize = 12.sp,
                    color = SoftGreen,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Right,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// Gorgeous vertical dashed divider between months
@Composable
fun MonthTransitionLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.Center
    ) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        Canvas(
            modifier = Modifier.fillMaxWidth(0.8f).matchParentSize()
        ) {
            drawLine(
                color = EmeraldPrimary.copy(alpha = 0.4f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                pathEffect = pathEffect,
                strokeWidth = 2.dp.toPx()
            )
        }
        Text(
            text = "ــ بداية شهر جديد ــ",
            color = EmeraldPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.background(IvoryBackground).padding(horizontal = 12.dp)
        )
    }
}

fun extractEmoji(category: String, defaultEmoji: String): String {
    val cat = category.lowercase()
    return when {
        cat.contains("دقيق") || cat.contains("🌾") -> "🌾"
        cat.contains("غاز") || cat.contains("🔥") -> "🔥"
        cat.contains("كهرباء") || cat.contains("⚡") -> "⚡"
        cat.contains("ماء") || cat.contains("💧") -> "💧"
        cat.contains("حليب") || cat.contains("🍼") -> "🍼"
        cat.contains("حفاظ") || cat.contains("👶") -> "👶"
        cat.contains("سكر") || cat.contains("🍬") -> "🍬"
        cat.contains("شاي") || cat.contains("☕") -> "☕"
        cat.contains("نت") || cat.contains("رصيد") || cat.contains("🌐") || cat.contains("إنترنت") -> "🌐"
        cat.contains("مدرس") || cat.contains("🎒") -> "🎒"
        cat.contains("ادخار") || cat.contains("🏦") -> "🏦"
        cat.contains("طوارئ") || cat.contains("🚨") -> "🚨"
        cat.contains("علاج") || cat.contains("💊") -> "💊"
        cat.contains("أثاث") || cat.contains("🛋️") -> "🛋️"
        else -> {
            // Check if there is already an emoji in the string
            val emojiRegex = "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+".toRegex()
            val match = emojiRegex.find(category)
            match?.value ?: defaultEmoji
        }
    }
}

@Composable
fun SearchLedgerDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<TransactionDb>,
    formatCurrency: (Double) -> String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                    Text(
                        "بحث في السجل 🔍",
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldPrimary,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("بحث بالبيان أو التصنيف...", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    textStyle = LocalTextStyle.current.copy(color = Color(0xFF1E293B), textAlign = TextAlign.Right),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedPlaceholderColor = Color(0xFF94A3B8),
                        unfocusedPlaceholderColor = Color(0xFF94A3B8)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (results.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank()) "ابدأ بكتابة كلمة للبحث في كل معاملاتك..." else "لا توجد نتائج مطابقة لبحثك 📓",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        "نتائج البحث (${results.size}):",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(results) { index, tx ->
                            key(tx.id) {
                                SearchResultItem(
                                    tx = tx,
                                    nextTx = if (index < results.size - 1) results[index + 1] else null,
                                    formatCurrency = formatCurrency
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    tx: TransactionDb,
    nextTx: TransactionDb?,
    formatCurrency: (Double) -> String
) {
    val dayName = DateUtils.getDayOfWeekArabic(tx.timestamp)
    val fullDate = DateUtils.formatDateFull(tx.timestamp)
    val timeStr = DateUtils.formatTime24Or12(tx.timestamp)

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = formatCurrency(tx.amount),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (tx.type == "INCOME") SoftGreen else SoftRed,
                        fontSize = 13.sp
                    )
                    Text(
                        text = timeStr,
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = tx.description.ifBlank { tx.category },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = EmeraldPrimary,
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = "$dayName - $fullDate",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        if (nextTx != null) {
            val interval = DateUtils.formatDurationBetween(tx.timestamp, nextTx.timestamp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = interval,
                    fontSize = 9.sp,
                    color = EmeraldPrimary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = EmeraldPrimary.copy(alpha = 0.4f)
                )
            }
        }
    }
}

fun getEmojiBgColor(emoji: String): Color {
    return when (emoji) {
        "🌾" -> Color(0xFFFEF3C7) // amber-100 (🌾)
        "🍬", "🍭" -> Color(0xFFFCE7F3) // pink-100 (🍬)
        "☕" -> Color(0xFFEFEFEF) // gray-100 (☕)
        "🔥" -> Color(0xFFFEE2E2) // red-100 (🔥)
        "⚡" -> Color(0xFFFEF9C3) // yellow-100 (⚡)
        "💧" -> Color(0xFFDBEAFE) // blue-100 (💧)
        "🚀", "🌐" -> Color(0xFFE0F2FE) // sky-100 (🌐)
        "🍼", "👶" -> Color(0xFFF3E8FF) // purple-100 (👶, 🍼)
        "🎒" -> Color(0xFFE0F2FE) // sky-100 (🎒)
        "🏦" -> Color(0xFFD1FAE5) // emerald-100 (🏦)
        "🚨" -> Color(0xFFFFE4E6) // rose-100 (🚨)
        "💊" -> Color(0xFFFCE7F3) // pink-100 (💊)
        "🛋️" -> Color(0xFFF3F4F6) // gray-100 (🛋️)
        "💰" -> Color(0xFFECFDF5) // green-50
        else -> Color(0xFFF1F5F9) // slate-100
    }
}

fun getAuditLogGroupDate(timestampMs: Long): String {
    val logCal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
    val todayCal = java.util.Calendar.getInstance()
    val yesterdayCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val dayBeforeCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -2) }

    val isSameDay = logCal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
            logCal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)

    val isYesterday = logCal.get(java.util.Calendar.YEAR) == yesterdayCal.get(java.util.Calendar.YEAR) &&
            logCal.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCal.get(java.util.Calendar.DAY_OF_YEAR)

    val isDayBefore = logCal.get(java.util.Calendar.YEAR) == dayBeforeCal.get(java.util.Calendar.YEAR) &&
            logCal.get(java.util.Calendar.DAY_OF_YEAR) == dayBeforeCal.get(java.util.Calendar.DAY_OF_YEAR)

    return when {
        isSameDay -> "اليوم"
        isYesterday -> "الأمس"
        isDayBefore -> "أول أمس"
        else -> {
            val sdf = java.text.SimpleDateFormat("EEEE، dd-MM-yyyy", java.util.Locale("ar"))
            sdf.format(java.util.Date(timestampMs))
        }
    }
}

fun formatAuditLogTime(timestampMs: Long): String {
    val sdf = java.text.SimpleDateFormat("dd-MM-yyyy | hh:mm a", java.util.Locale("ar"))
    return sdf.format(java.util.Date(timestampMs))
}
