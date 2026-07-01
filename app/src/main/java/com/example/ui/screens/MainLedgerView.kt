package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import com.example.ui.components.CircularRevealShape
import com.example.ui.components.DeveloperSealFooter
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
import androidx.compose.ui.platform.LocalView
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
import com.example.data.local.entities.*
import com.example.domain.DateUtils
import com.example.ui.theme.*
import androidx.core.view.WindowCompat
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
    settings: AppSettings,
    onBackIntercept: (Boolean) -> Unit, // intercepts back to cancel selection mode if active
    onMenuClick: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val bottomPadding = contentPadding.calculateBottomPadding()
    val topPadding = contentPadding.calculateTopPadding()

    val totalCash by viewModel.totalCashState.collectAsStateWithLifecycle()
    val commitments by viewModel.commitmentsState.collectAsStateWithLifecycle()
    val monthlyLedger by viewModel.monthlyLedgerState.collectAsStateWithLifecycle()

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

    // Performance Profile: Defer heavy list rendering until navigation animation completes
    var isScreenReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // Small delay to allow navigation transition to complete smoothly
        isScreenReady = true
    }

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
    val habayebOwedByThemTotalState by viewModel.habayebOwedByThemTotalState.collectAsStateWithLifecycle()
    val habayebOwedByThemTotal = habayebOwedByThemTotalState.toDouble()

    // Precompute commitments coverage details
    // تحذير للمطور: يجب الحذر عند تحويل الـ BigDecimal إلى Double في الحسابات المالية لتجنب أخطاء التقريب العشري. تم إبقاء المنطق حالياً لتجنب كسر الوظيفة.
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

    DeleteDaysConfirmDialog(
        showDeleteDaysDialog = showDeleteDaysDialog,
        onDismiss = { showDeleteDaysDialog = false },
        monthlyLedger = monthlyLedger,
        selectedDayKeys = selectedDayKeys,
        viewModel = viewModel,
        scope = scope,
        context = context,
        onSuccess = {
            selectedDayKeys.clear()
            isDaySelectionMode = false
            showDeleteDaysDialog = false
        }
    )

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
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = bottomPadding + 110.dp // حشوة كافية للتمرير خلف الـ Dock والـ Navigation Bar بأمان وتناسق
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Compact Header + Total Cash + Coverage Ratio
            item(key = "header_total_cash") {
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
            item(key = "commitments_summary") {
                CommitmentsSummaryCards(
                    commitments = commitments,
                    computedCommitments = computedCommitments,
                    totalCash = totalCash,
                    currencySymbol = settings.currencySymbol,
                    formatCurrency = { value, sym -> viewModel.formatCurrency(value, sym) }
                )
            }



            // Quick Navigation Widgets removed for clean floating bottom dock interface.

            // No data placeholder
            if (monthlyLedger.isEmpty()) {
                item(key = "empty_state") {
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

            // Performance Profile: Deferred Loading for heavy list items
            if (!isScreenReady && monthlyLedger.isNotEmpty()) {
                item(key = "loading_skeleton") {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = EmeraldPrimary,
                            strokeWidth = 3.dp
                        )
                    }
                }
            } else {
                // Ledger Month-by-month list
                monthlyLedger.forEachIndexed { monthIdx, monthLedger ->
                    val isCollapsed = collapsedMonths.contains(monthLedger.monthKey)

                    // Month Header
                    item(key = "header_${monthLedger.monthKey}") {
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

                    DayCard(
                        dayLedger = dayLedger,
                        dayKey = dayKey,
                        isDaySelected = isDaySelected,
                        isDaySelectionMode = isDaySelectionMode,
                        haptic = haptic,
                        currencySymbol = settings.currencySymbol,
                        formatCurrency = { amt, sym -> viewModel.formatCurrency(amt, sym) },
                        onDayClick = {
                            if (isDaySelectionMode) {
                                if (selectedDayKeys.contains(it)) {
                                    selectedDayKeys.remove(it)
                                    if (selectedDayKeys.isEmpty()) {
                                        isDaySelectionMode = false
                                    }
                                } else {
                                    selectedDayKeys.add(it)
                                }
                            } else {
                                activeDayKey = it
                            }
                        },
                        onDayLongClick = {
                            if (!isDaySelectionMode && !isSelectionMode) {
                                isDaySelectionMode = true
                                selectedDayKeys.add(it)
                            } else if (isDaySelectionMode) {
                                if (selectedDayKeys.contains(it)) {
                                    selectedDayKeys.remove(it)
                                    if (selectedDayKeys.isEmpty()) {
                                        isDaySelectionMode = false
                                    }
                                } else {
                                    selectedDayKeys.add(it)
                                }
                            }
                        }
                    )
                }
                } // End of if (!isCollapsed)

                // Month Transition Separator
                if (monthIdx < monthlyLedger.size - 1) {
                    item(key = "transition_${monthLedger.monthKey}") {
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
            } // End of forEachIndexed
        } // End of else block for deferred loading
        }

        // Floating action buttons (Dual floating configuration) - Compressed & modern
        LedgerBottomDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding + 12.dp), // مسافة أمان بصري فخمة فوق الـ Navigation Bar المدمج
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
            }
        )
    }

    // Modal dialog for Recording Income / Expenses
    TransactionRecordDialog(
        showTxDialog = showTxDialog,
        txDialogType = txDialogType,
        editingTransaction = editingTransaction,
        currencySymbol = settings.currencySymbol,
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

    ReorderCommitmentDialog(
        reorderCommitmentTarget = reorderCommitmentTarget,
        commitmentsSize = commitments.size,
        onDismiss = { reorderCommitmentTarget = null },
        onApplyReorder = { target, position ->
            viewModel.reorderCommitment(target, position)
            reorderCommitmentTarget = null
        },
        context = context
    )

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
                onMenuClick = onMenuClick,
                onClose = {
                    scope.launch {
                        isHabayebActive = false
                    }
                }
            )
        }
    }
}




