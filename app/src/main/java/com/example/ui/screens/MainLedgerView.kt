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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.example.ui.components.CircularRevealShape
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
import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.data.local.*
import com.example.domain.DateUtils
import com.example.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import android.app.Activity
import com.example.ui.viewmodel.*
import com.example.ui.screens.ledger.components.*
import com.example.domain.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import java.math.BigDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    val isActivated by viewModel.isActivatedState.collectAsStateWithLifecycle()
    val totalTransactionsCount by viewModel.totalTransactionsCount.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceIdState.collectAsStateWithLifecycle()
    val showActivationRequired by viewModel.showActivationRequired.collectAsStateWithLifecycle()

    var showActivationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showActivationRequired) {
        if (showActivationRequired) {
            showActivationDialog = true
            viewModel.showActivationRequired.value = false
        }
    }

    // Floating commitment dialog
    var showCommitmentsListSheet by remember { mutableStateOf(false) }
    var reorderCommitmentTarget by remember { mutableStateOf<FixedCommitment?>(null) }
    var showCommitmentDialog by remember { mutableStateOf(false) }
    var editingCommitment by remember { mutableStateOf<FixedCommitment?>(null) }

    // Pop-up dialog day state tracking key
    var activeDayKey by remember { mutableStateOf<String?>(null) }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResultsState.collectAsStateWithLifecycle()
    val customCats by viewModel.customCategoriesState.collectAsStateWithLifecycle()

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

    var isDaySelectionMode by remember { mutableStateOf(false) }
    val selectedDayKeys = remember { mutableStateListOf<String>() }
    var showDeleteDaysDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode || isDaySelectionMode || activeDayKey != null || showSearch || isHabayebActive) {
        if (isHabayebActive) {
            isHabayebActive = false
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
    val dailyComp by viewModel.dailyExpenseComparisonState.collectAsStateWithLifecycle()
    val todayExp = dailyComp.first
    val yesterdayExp = dailyComp.second
    val diffExp = todayExp.subtract(yesterdayExp)

    val linkHabayebDebts by viewModel.linkHabayebDebtsState.collectAsStateWithLifecycle()
    val habayebOwedByThemTotal by viewModel.habayebOwedByThemTotalState.collectAsStateWithLifecycle()

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
                    text = stringResource(id = R.string.ledger_bulk_delete_days_title),
                    fontWeight = FontWeight.Bold,
                    color = SoftRed,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.ledger_bulk_delete_days_msg),
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
                                val tx = monthlyLedger.flatMap { ml -> ml.days.flatMap { it.transactions } }.find { it.id == txId }
                            }
                            viewModel.deleteTransactionsBulk(txsToDelete, context.getString(R.string.ledger_bulk_delete_days_desc))
                            selectedDayKeys.clear()
                            isDaySelectionMode = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed)
                ) {
                    Text(stringResource(id = R.string.ledger_bulk_delete_days_confirm_btn), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDaysDialog = false }) {
                    Text(stringResource(id = R.string.common_cancel), color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(IvoryBackground)) {
        // High-fidelity pinned collapsible top header when scrolled - Redesigned Clock to Top-Right
        PinnedMainLedgerHeader(
            collapseFraction = collapseFraction,
            onMenuClick = onMenuClick,
            onSearchClick = { showSearch = true },
            onHabayebClick = { isHabayebActive = true }
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Compact Header + Total Cash + Coverage Ratio
            item {
                val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsStateWithLifecycle()
                val allKeys = remember(monthlyLedger) {
                    monthlyLedger.flatMap { ml -> ml.days.map { "${ml.monthKey}_${it.dayNumber}" } }
                }
                val selectedDayKeysCountText = when (selectedDayKeys.size) {
                    1 -> stringResource(id = R.string.ledger_selected_days_count_1)
                    2 -> stringResource(id = R.string.ledger_selected_days_count_2)
                    else -> stringResource(id = R.string.ledger_selected_days_count_more, selectedDayKeys.size)
                }
                val isSelectAllChecked = selectedDayKeys.size == allKeys.size && allKeys.isNotEmpty()

                MainLedgerHeader(
                    collapseFraction = collapseFraction,
                    isDaySelectionMode = isDaySelectionMode,
                    selectedDayKeys = selectedDayKeys,
                    onCancelDaySelection = {
                        isDaySelectionMode = false
                        selectedDayKeys.clear()
                    },
                    onSelectAllDays = {
                        if (selectedDayKeys.size == allKeys.size) {
                            selectedDayKeys.clear()
                        } else {
                            selectedDayKeys.clear()
                            selectedDayKeys.addAll(allKeys)
                        }
                    },
                    onDeleteSelectedDays = {
                        if (selectedDayKeys.isNotEmpty()) {
                            showDeleteDaysDialog = true
                        }
                    },
                    onMenuClick = onMenuClick,
                    onSearchClick = { showSearch = true },
                    totalCash = totalCash,
                    isPrivacyMode = isPrivacyMode,
                    onTogglePrivacyMode = { viewModel.togglePrivacyMode() },
                    currencySymbol = settings.currencySymbol,
                    formatCurrency = { value, sym -> viewModel.formatCurrency(value, sym) },
                    commitments = commitments,
                    computedCommitments = computedCommitments,
                    linkHabayebDebts = linkHabayebDebts,
                    onLinkHabayebDebtsChange = { viewModel.toggleLinkHabayebDebts(it) },
                    monthlyLedger = monthlyLedger,
                    selectedDayKeysCountText = selectedDayKeysCountText,
                    isSelectAllChecked = isSelectAllChecked
                )
            }

            // Commitments Summary Cards - Row 1 of the 2x2 Grid block
            item {
                CommitmentsSummaryCards(
                    commitments = commitments,
                    computedCommitments = computedCommitments,
                    totalCash = totalCash,
                    currencySymbol = settings.currencySymbol,
                    formatCurrency = { value, sym -> viewModel.formatCurrency(value, sym) }
                )
            }

            // Budget Advice Banner based on daily comparison
            item {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    BudgetAdviceBanner(diffExp)
                }
            }

            // Quick Navigation Widgets removed for clean floating bottom dock interface.

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
                            text = stringResource(id = R.string.ledger_empty_state_msg),
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
                                text = if (monthIdx == 0) stringResource(id = R.string.ledger_daily_record) else stringResource(id = R.string.ledger_monthly_record),
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
                    items(monthLedger.days, key = { dayLedger -> "${monthLedger.monthKey}_${dayLedger.dayNumber}" }) { dayLedger ->
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
                                    Text(stringResource(id = R.string.ledger_details_label), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
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
                                        text = stringResource(id = R.string.ledger_days_prefix, dayLedger.dayNumber, dayLedger.dayOfWeek),
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
        LedgerBottomDock(
            isSelectionMode = isSelectionMode,
            selectedTxIdsCount = selectedTxIds.size,
            onDeleteSelectedClick = {
                scope.launch {
                    viewModel.deleteTransactionsBulk(selectedTxIds.toList(), context.getString(R.string.ledger_delete_selected_warning, selectedTxIds.size))
                    delay(200)
                    clearSelection()
                }
            },
            onShowCommitmentsClick = { showCommitmentsListSheet = true },
            onAddIncomeClick = {
                editingTransaction = null
                txDialogType = "INCOME"
                showTxDialog = true
            },
            onAddExpenseClick = {
                editingTransaction = null
                txDialogType = "EXPENSE"
                showTxDialog = true
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Modal dialog for Recording Income / Expenses
    TransactionRecordDialog(
        showTxDialog = showTxDialog,
        txDialogType = txDialogType,
        editingTransaction = editingTransaction,
        currencySymbol = settings.currencySymbol,
        schoolExpensesEnabled = settings.schoolExpensesEnabled,
        customCategories = customCats,
        onDismiss = { showTxDialog = false },
        onSave = { id, type, category, amount, description ->
            if (editingTransaction != null) {
                val tx = editingTransaction!!.copy(
                    amount = amount,
                    description = description,
                    category = category
                )
                viewModel.updateTransaction(tx)
            } else {
                viewModel.addTransaction(
                    type = type,
                    category = category,
                    amount = amount,
                    description = description
                )
            }
        },
        onSaveCustomCategory = { name, tab, emoji ->
            viewModel.saveCustomCategory(name, tab, emoji)
        },
        onDeleteCustomCategory = { cat ->
            viewModel.deleteCustomCategory(cat)
        }
    )

    if (showSearch) {
        SearchLedgerDialog(
            query = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            results = searchResults,
            formatCurrency = { amt -> viewModel.formatCurrency(java.math.BigDecimal.valueOf(amt), settings.currencySymbol) },
            onDismiss = { showSearch = false }
        )
    }

    // Commitments List Popup Dialog
    CommitmentsListDialog(
        showCommitmentsListSheet = showCommitmentsListSheet,
        commitments = commitments,
        computedCommitments = computedCommitments,
        totalCash = totalCash,
        currencySymbol = settings.currencySymbol,
        formatCurrency = { amt, sym -> viewModel.formatCurrency(amt, sym) },
        formatDoubleCurrency = { amt, sym -> viewModel.formatDoubleCurrency(amt, sym) },
        onDismissRequest = { showCommitmentsListSheet = false },
        onAddCommitmentClick = {
            editingCommitment = null
            showCommitmentDialog = true
        },
        onEditCommitmentClick = { fc ->
            editingCommitment = fc
            showCommitmentDialog = true
        },
        onDeleteCommitment = { name -> viewModel.deleteCommitment(name) },
        onReorderCommitment = { fc, pos -> viewModel.reorderCommitment(fc, pos) },
        onCheckedChange = { fc, checked ->
            viewModel.saveCommitment(fc.name, fc.targetAmount, if (checked) fc.targetAmount else 0.0)
        },
        onSetReorderTarget = { reorderCommitmentTarget = it }
    )

    // Commitment Add/Edit Dialog
    CommitmentEditDialog(
        showCommitmentDialog = showCommitmentDialog,
        editingCommitment = editingCommitment,
        onDismissRequest = {
            showCommitmentDialog = false
            editingCommitment = null
        },
        onSaveCommitment = { name, targetAmount, currentProgress ->
            viewModel.saveCommitment(name, targetAmount, currentProgress)
            showCommitmentDialog = false
            editingCommitment = null
        },
        onDeleteCommitment = { name ->
            viewModel.deleteCommitment(name)
            showCommitmentDialog = false
            editingCommitment = null
        }
    )

    if (reorderCommitmentTarget != null) {
        var targetPositionStr by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { reorderCommitmentTarget = null },
            title = {
                Text(
                    stringResource(id = R.string.ledger_reorder_target_title, reorderCommitmentTarget?.name ?: ""),
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(stringResource(id = R.string.ledger_reorder_position_label, commitments.size), fontSize = 13.sp, color = Color.Gray)
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
                            errorMsg = context.getString(R.string.ledger_reorder_position_error)
                        } else {
                            viewModel.reorderCommitment(reorderCommitmentTarget!!, pos)
                            reorderCommitmentTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(stringResource(id = R.string.ledger_reorder_apply), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { reorderCommitmentTarget = null }) {
                    Text(stringResource(id = R.string.common_cancel), color = Color.Gray)
                }
            }
        )
    }

    if (showActivationDialog) {
        DeviceActivationDialog(
            deviceId = deviceId,
            viewModel = viewModel,
            onDismiss = { showActivationDialog = false }
        )
    }

    // Interactive Custom Pop-up Dialog for Day Transactions (Chronological order)
    ActiveDayTransactionsDialog(
        activeDayKey = activeDayKey,
        activeDayLedger = activeDayLedger,
        currencySymbol = settings.currencySymbol,
        onDismiss = { activeDayKey = null },
        onDeleteTransaction = { txId -> viewModel.deleteTransactionById(txId) },
        onEditTransaction = { tx ->
            editingTransaction = tx
            txDialogType = tx.type
            showTxDialog = true
        },
        formatDoubleCurrency = { amt, sym -> viewModel.formatDoubleCurrency(amt, sym) },
        formatCurrency = { amt, sym -> viewModel.formatCurrency(amt, sym) }
    )

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
}




