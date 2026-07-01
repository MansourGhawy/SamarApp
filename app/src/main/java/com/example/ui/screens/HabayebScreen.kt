package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.evaluateSimpleExpression
import com.example.domain.StringUtils.getContactDetails
import com.example.domain.StringUtils.toEnglishDigits
import com.example.ui.components.CircularRevealShape
import com.example.data.serialization.PdfReportGenerator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Dialog
import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.HabayebTransaction
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.state.CustomerUiState
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalView

import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import android.app.Activity

import com.example.ui.screens.habayeb.components.AddCustomerPopup
import com.example.ui.screens.habayeb.components.CustomerHistoryOverlay
import com.example.ui.screens.habayeb.components.CustomerItemRow
import com.example.ui.screens.habayeb.components.AddTransactionPopup
import com.example.ui.screens.habayeb.components.HabayebFilterTabs
import com.example.ui.screens.habayeb.components.HabayebHeaderTopBar
import com.example.ui.screens.habayeb.components.HabayebFilterToolbar
import com.example.ui.helper.AutoScaleText
import com.example.ui.helper.formatCurrency
import com.example.ui.helper.getInitialColor


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HabayebScreen(
    viewModel: FinanceViewModel,
    onMenuClick: () -> Unit,
    onClose: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val activeThemeColor = MaterialTheme.colorScheme.primary
    val activeSubColor = MaterialTheme.colorScheme.primaryContainer
    val primaryColor = activeThemeColor
    val containerColor = activeSubColor
    val surfaceBackgroundColor = Color(0xFFF1F5F9)
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false // Dark gradient needs white icons
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    // Observe DB lists
    val customersState by viewModel.customersUiState.collectAsStateWithLifecycle()
    val transactions by viewModel.habayebTransactionsState.collectAsStateWithLifecycle()
    val totalOwedByThemState by viewModel.habayebOwedByThemTotalState.collectAsStateWithLifecycle()
    val totalOwedToThemState by viewModel.habayebOwedToThemTotalState.collectAsStateWithLifecycle()
    val totalOwedByThem = totalOwedByThemState.toDouble()
    val totalOwedToThem = totalOwedToThemState.toDouble()
    val currencySymbol = viewModel.settingsState.collectAsStateWithLifecycle().value.currencySymbol
    val isPrivacyModeState = viewModel.isPrivacyModeEnabled.collectAsStateWithLifecycle()

    // UI filters
    // 0 = الكل, 1 = لي عند الناس (المدينين), 2 = علي للناس (الدائنين)
    var selectedFilterTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Multi-Select state
    val selectedCustomerIds = remember { mutableStateListOf<String>() }
    val temporarilyHiddenCustomerIds = remember { mutableStateListOf<String>() }
    var isMultiSelectActive by remember { mutableStateOf(false) }

    // Dialog sheets states
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var activeCustomerForHistory by remember { mutableStateOf<HabayebCustomer?>(null) }
    var stableCustomer by remember { mutableStateOf<HabayebCustomer?>(null) }
    LaunchedEffect(activeCustomerForHistory) {
        if (activeCustomerForHistory != null) {
            stableCustomer = activeCustomerForHistory
        }
    }
    var showAddTransactionDialogForCustomer by remember { mutableStateOf<HabayebCustomer?>(null) }
    var defaultTransactionTypeForDialog by remember { mutableStateOf("OWED_BY_THEM") }
    var editingTransactionForDialog by remember { mutableStateOf<HabayebTransaction?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditCustomerDialog by remember { mutableStateOf(false) }
    var editingCustomerForDialog by remember { mutableStateOf<HabayebCustomer?>(null) }
    var financialSortMode by remember { mutableStateOf(0) }
    var historicalSortMode by remember { mutableStateOf(1) }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Collapsible header logic
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val expandedHeaderHeight = 220.dp // Restored to a compact, beautiful height
    val collapsedHeaderHeight = 56.dp
    val maxScrollPx = with(LocalDensity.current) { (expandedHeaderHeight - collapsedHeaderHeight).toPx() }
    var headerOffsetHeightPx by remember { mutableStateOf(0f) }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            headerOffsetHeightPx = 0f
        }
    }

    val nestedScrollConnection = remember(isSearchActive) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (isSearchActive) return androidx.compose.ui.geometry.Offset.Zero
                val delta = available.y
                val newOffset = headerOffsetHeightPx + delta
                headerOffsetHeightPx = newOffset.coerceIn(-maxScrollPx, 0f)
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    val collapseProgress = (headerOffsetHeightPx + maxScrollPx) / maxScrollPx


    // Back handler: dismisses overlays of selection first
    BackHandler {
        if (isMultiSelectActive) {
            selectedCustomerIds.clear()
            isMultiSelectActive = false
        } else if (isSearchActive) {
            searchQuery = ""
            isSearchActive = false
        } else if (activeCustomerForHistory != null) {
            activeCustomerForHistory = null
        } else {
            onClose()
        }
    }

    // Performance Profile: Defer heavy list rendering until navigation animation completes
    var isScreenReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        isScreenReady = true
    }

    // Optimized, non-blocking asynchronous calculation of filtered and sorted customers list
    var filteredCustomers by remember { mutableStateOf(emptyList<CustomerUiState>()) }

    LaunchedEffect(customersState, selectedFilterTab, searchQuery, financialSortMode, historicalSortMode, temporarilyHiddenCustomerIds.toList()) {
        val filtered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val filteredList = customersState.customers.filter { customerUi ->
                if (temporarilyHiddenCustomerIds.contains(customerUi.id)) return@filter false
                val matchesSearch = searchQuery.isEmpty() ||
                        customerUi.name.contains(searchQuery, ignoreCase = true) ||
                        customerUi.phone.contains(searchQuery, ignoreCase = true)
                if (!matchesSearch) return@filter false

                when (selectedFilterTab) {
                    1 -> customerUi.netDebt > 0.0 // Debtors (لي عند الناس)
                    2 -> customerUi.netDebt < 0.0 // Creditors (علي للناس)
                    else -> true
                }
            }

            if (financialSortMode != 0) {
                if (financialSortMode == 1) {
                    filteredList.sortedByDescending { kotlin.math.abs(it.netDebt) }
                } else {
                    filteredList.sortedBy { kotlin.math.abs(it.netDebt) }
                }
            } else if (historicalSortMode != 0) {
                if (historicalSortMode == 1) {
                    filteredList.sortedByDescending { it.lastTransactionTimestamp }
                } else {
                    filteredList.sortedBy { it.lastTransactionTimestamp }
                }
            } else {
                filteredList
            }
        }
        filteredCustomers = filtered
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceBackgroundColor)
                .testTag("habayeb_screen_root")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Static top Column to anchor TopBar, FilterTabs, and FilterToolbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceBackgroundColor)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = activeThemeColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        HabayebHeaderTopBar(
                            isSearchActive = isSearchActive,
                            onSearchActiveChanged = { isSearchActive = it },
                            searchQuery = searchQuery,
                            onSearchQueryChanged = { searchQuery = it },
                            onMenuClick = onMenuClick,
                            haptic = haptic,
                            netDebt = totalOwedByThem - totalOwedToThem,
                            isPrivacyMode = isPrivacyModeState.value,
                            onTogglePrivacy = { viewModel.togglePrivacyMode() },
                            currencySymbol = currencySymbol
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    HabayebFilterTabs(
                        selectedFilterTab = selectedFilterTab,
                        onFilterTabSelected = { selectedFilterTab = it },
                        totalOwedByThem = totalOwedByThem,
                        totalOwedToThem = totalOwedToThem,
                        currencySymbol = currencySymbol,
                        isPrivacyMode = isPrivacyModeState.value,
                        haptic = haptic
                    )

                    HabayebFilterToolbar(
                        filteredCustomersCount = filteredCustomers.size,
                        financialSortMode = financialSortMode,
                        onFinancialSortModeChanged = { financialSortMode = it },
                        historicalSortMode = historicalSortMode,
                        onHistoricalSortModeChanged = { historicalSortMode = it },
                        activeThemeColor = activeThemeColor,
                        activeSubColor = activeSubColor,
                        haptic = haptic,
                        onScrollToTop = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = contentPadding.calculateBottomPadding() + 80.dp
                    )
                ) {
                    // Density Optimized List Area
                    if (!isScreenReady && customersState.customers.isNotEmpty()) {
                        item(key = "loading_skeleton") {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = EmeraldPrimary,
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    } else if (filteredCustomers.isEmpty()) {
                        item(key = "empty_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight(0.6f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🤝", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = when (selectedFilterTab) {
                                            1 -> stringResource(id = R.string.habayeb_no_debtors)
                                            2 -> stringResource(id = R.string.habayeb_no_creditors)
                                            else -> stringResource(id = R.string.habayeb_empty_list)
                                        },
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            val isSelected = selectedCustomerIds.contains(customer.id)

                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                CustomerItemRow(
                                    isPrivacyMode = isPrivacyModeState.value,
                                    customer = customer,
                                    isSelected = isSelected,
                                    isMultiSelectActive = isMultiSelectActive,
                                    activeThemeColor = activeThemeColor,
                                    activeSubColor = activeSubColor,
                                    currencySymbol = currencySymbol,
                                    haptic = haptic,
                                    onCustomerClick = {
                                        if (isMultiSelectActive) {
                                            if (isSelected) {
                                                selectedCustomerIds.remove(customer.id)
                                                if (selectedCustomerIds.isEmpty()) isMultiSelectActive = false
                                            } else {
                                                selectedCustomerIds.add(customer.id)
                                            }
                                        } else {
                                            activeCustomerForHistory = customer.originalCustomer
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    onCustomerLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectActive = true
                                        if (!isSelected) selectedCustomerIds.add(customer.id)
                                    },
                                    onQuickAdd = {
                                        defaultTransactionTypeForDialog = if (customer.netDebt >= 0.0) "OWED_BY_THEM" else "OWED_TO_THEM"
                                        showAddTransactionDialogForCustomer = customer.originalCustomer
                                    }
                                )
                            }
                        }
                    }
                }
            }

        if (!isMultiSelectActive && activeCustomerForHistory == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp, start = 16.dp)
                    .size(58.dp)
                    .shadow(10.dp, CircleShape, spotColor = primaryColor.copy(alpha = 0.6f))
                    .background(primaryColor, CircleShape)
                    .border(1.dp, containerColor.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAddCustomerDialog = true
                    }
                    .testTag("add_customer_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.habayeb_add_customer_fab),
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            }
        }

        // --- Multi-Select Floating Bar ---
        AnimatedVisibility(
            visible = isMultiSelectActive,
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
                        isMultiSelectActive = false
                        selectedCustomerIds.clear()
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
                            val allSelected = filteredCustomers.isNotEmpty() && filteredCustomers.all { selectedCustomerIds.contains(it.id) }
                            if (allSelected) {
                                selectedCustomerIds.clear()
                            } else {
                                filteredCustomers.forEach { if (!selectedCustomerIds.contains(it.id)) selectedCustomerIds.add(it.id) }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val allSelected = filteredCustomers.isNotEmpty() && filteredCustomers.all { selectedCustomerIds.contains(it.id) }
                    Icon(
                        imageVector = if (allSelected) Icons.Default.Check else Icons.Default.List,
                        contentDescription = "تحديد الكل",
                        tint = if (allSelected) activeThemeColor else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (allSelected) "تم تحديد الكل" else "${selectedCustomerIds.size} محدد",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete Button
                IconButton(
                    onClick = {
                        if (selectedCustomerIds.isNotEmpty()) {
                            showDeleteConfirmDialog = true
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

        // --- Dialogs & Panels ---

        // 1. ADD NEW CUSTOMER DIALOG (Includes atomic transaction requirement)
        if (showAddCustomerDialog) {
            AddCustomerPopup(
                viewModel = viewModel,
                onDismiss = { showAddCustomerDialog = false },
                onCustomerAdded = {
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(200)
                        listState.animateScrollToItem(0)
                    }
                },
                activeThemeColor = activeThemeColor,
                activeSubColor = activeSubColor
            )
        }

        // 2. DETAILED CUSTOMER DEBT TRANSACTION HISTORY OVERLAY
        AnimatedVisibility(
            visible = activeCustomerForHistory != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.zIndex(10f)
        ) {
            stableCustomer?.let { customer ->
                CustomerHistoryOverlay(
                    customer = customer,
                    viewModel = viewModel,
                    onDismiss = { activeCustomerForHistory = null },
                    onAddTransaction = { c, type ->
                        defaultTransactionTypeForDialog = type
                        showAddTransactionDialogForCustomer = c
                    },
                    activeThemeColor = activeThemeColor,
                    activeSubColor = activeSubColor,
                    currencySymbol = currencySymbol,
                    contentPadding = contentPadding
                )
            }
        }

        // 3. ADD/EDIT DEBT TRANSACTION POPUP
        if (showAddTransactionDialogForCustomer != null) {
            AddTransactionPopup(
                customer = showAddTransactionDialogForCustomer!!,
                viewModel = viewModel,
                initialSelectedType = defaultTransactionTypeForDialog,
                editingTransaction = editingTransactionForDialog,
                onDismiss = {
                    showAddTransactionDialogForCustomer = null
                    editingTransactionForDialog = null
                },
                activeThemeColor = activeThemeColor,
                activeSubColor = activeSubColor
            )
        }

        // 4. MULTI-DELETE / SINGLE DELETE CONFIRMATION DIALOG
        if (showDeleteConfirmDialog) {
            com.example.ui.screens.habayeb.components.DeleteConfirmDialog(
                selectedCustomerIds = selectedCustomerIds.toList(),
                viewModel = viewModel,
                onDismiss = {
                    showDeleteConfirmDialog = false
                },
                onSuccessBulkDelete = {
                    selectedCustomerIds.clear()
                    isMultiSelectActive = false
                }
            )
        }

        if (showEditCustomerDialog && editingCustomerForDialog != null) {
            com.example.ui.screens.habayeb.components.EditCustomerDialog(
                customer = editingCustomerForDialog!!,
                viewModel = viewModel,
                activeThemeColor = activeThemeColor,
                onDismiss = { showEditCustomerDialog = false }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )

        // 5. SAFE EXIT CONFIRMATION DIALOG GONE FOR EASY ENTRY/EXIT
        }
        }
}