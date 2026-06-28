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
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.screens.habayeb.components.CalculatorModal
import com.example.ui.screens.habayeb.components.HabayebNetBalanceHeader
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val activeThemeColor = MaterialTheme.colorScheme.primary
    val activeSubColor = MaterialTheme.colorScheme.primaryContainer
    val primaryColor = activeThemeColor
    val containerColor = activeSubColor
    val surfaceBackgroundColor = MaterialTheme.colorScheme.background
    
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
    var showAddTransactionDialogForCustomer by remember { mutableStateOf<HabayebCustomer?>(null) }
    var defaultTransactionTypeForDialog by remember { mutableStateOf("OWED_BY_THEM") }
    var editingTransactionForDialog by remember { mutableStateOf<HabayebTransaction?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var customerToDelete by remember { mutableStateOf<HabayebCustomer?>(null) }
    var showEditCustomerDialog by remember { mutableStateOf(false) }
    var editingCustomerForDialog by remember { mutableStateOf<HabayebCustomer?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = activeThemeColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                HabayebHeaderTopBar(
                    isSearchActive = isSearchActive,
                    onSearchActiveChanged = { isSearchActive = it },
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { searchQuery = it },
                    isMultiSelectActive = isMultiSelectActive,
                    selectedCount = selectedCustomerIds.size,
                    onDeleteBulkClick = {
                        showDeleteConfirmDialog = true
                    },
                    onClose = onClose,
                    onSelectAllClick = {
                        val allInListSelected = filteredCustomers.isNotEmpty() &&
                                filteredCustomers.all { selectedCustomerIds.contains(it.id) }
                        if (allInListSelected) {
                            selectedCustomerIds.clear()
                            isMultiSelectActive = false
                        } else {
                            filteredCustomers.forEach { customer ->
                                if (!selectedCustomerIds.contains(customer.id)) {
                                    selectedCustomerIds.add(customer.id)
                                }
                            }
                        }
                    },
                    haptic = haptic
                )
            } // Close Floating Header Card

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = 76.dp + with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() },
                    bottom = 80.dp
                )
            ) {
                // Item 1: Giant Net Balance Card
                item {
                    HabayebNetBalanceHeader(
                        totalOwedByThem = totalOwedByThem,
                        totalOwedToThem = totalOwedToThem,
                        currencySymbol = currencySymbol,
                        isPrivacyMode = isPrivacyModeState.value,
                        onTogglePrivacy = { viewModel.togglePrivacyMode() }
                    )
                }

                // Item 2: Compact Row of 2 Interactive Filter Tabs
                item {
                    HabayebFilterTabs(
                        selectedFilterTab = selectedFilterTab,
                        onFilterTabSelected = { selectedFilterTab = it },
                        totalOwedByThem = totalOwedByThem,
                        totalOwedToThem = totalOwedToThem,
                        currencySymbol = currencySymbol,
                        haptic = haptic
                    )
                }

             item {
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
             } // Close Smart Filter Toolbar item block

            // Density Optimized List Area
            if (filteredCustomers.isEmpty()) {
                item {
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
                // Let's add spacing between items using item blocks or item Content padding if possible?
                // Wait, we can't easily specify verticalArrangement on LazyColumn item by item. 
                // We'll wrap the inner card with padding.
                items(filteredCustomers, key = { it.id }) { customer ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                        val isSelected = selectedCustomerIds.contains(customer.id)
                        CustomerItemRow(
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

        if (!isMultiSelectActive && activeCustomerForHistory == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 16.dp, start = 16.dp)
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

        // --- Dialogs & Panels ---

        // 1. ADD NEW CUSTOMER DIALOG (Includes atomic transaction requirement)
        if (showAddCustomerDialog) {
            AddCustomerPopup(
                viewModel = viewModel,
                onDismiss = { showAddCustomerDialog = false },
                activeThemeColor = activeThemeColor,
                activeSubColor = activeSubColor
            )
        }

        // 2. DETAILED CUSTOMER DEBT TRANSACTION HISTORY OVERLAY
        if (activeCustomerForHistory != null) {
            CustomerHistoryOverlay(
                customer = activeCustomerForHistory!!,
                viewModel = viewModel,
                onDismiss = { activeCustomerForHistory = null },
                onAddTransaction = { customer, type ->
                    defaultTransactionTypeForDialog = type
                    showAddTransactionDialogForCustomer = customer
                },
                activeThemeColor = activeThemeColor,
                activeSubColor = activeSubColor,
                currencySymbol = currencySymbol
            )
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
                customerToDelete = customerToDelete,
                selectedCustomerIds = selectedCustomerIds.toList(),
                viewModel = viewModel,
                onDismiss = {
                    showDeleteConfirmDialog = false
                    customerToDelete = null
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