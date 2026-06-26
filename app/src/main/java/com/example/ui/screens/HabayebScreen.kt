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
import com.example.data.local.HabayebCustomer
import com.example.data.local.HabayebTransaction
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
val activeThemeColor = Color(0xFF3F51B5)    // Royal Indigo
val activeSubColor = Color(0xFFE8EAF6)       // Pastel Lavender
val RoyalPurple = activeThemeColor // For compatibility
val DeepLavender = Color(0xFF1E3A8A).copy(alpha = 0.8f) // Deep solid for gradient/card fallback if used
val SoftLavender = activeSubColor
val LightPurpleBg = Color(0xFFF8FAFC)        // Match Makhzan background
val DarkPurpleText = Color(0xFF1E1B4B)
val HabayebTextSecondary = Color(0xFF4B5563)

// Pastel Colors for Initials
val PastelColors = listOf(
    Color(0xFFFCA5A5), Color(0xFFFDBA74), Color(0xFFFDE047),
    Color(0xFF86EFAC), Color(0xFF93C5FD), Color(0xFFC4B5FD),
    Color(0xFFF472B6), Color(0xFF2DD4BF)
)

fun getInitialColor(name: String): Color {
    val hash = name.hashCode().coerceAtLeast(0)
    return PastelColors[hash % PastelColors.size]
}

fun formatYemeniRial(amount: Double): String {
    val absVal = kotlin.math.abs(amount)
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
    val formatter = java.text.DecimalFormat("#,##0", symbols)
    return "${formatter.format(absVal)} ر.ي"
}

@Composable
fun AutoScaleText(
    text: String,
    baseFontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1
) {
    val adjustedFontSize = when {
        text.length > 18 -> baseFontSize * 0.70f
        text.length > 13 -> baseFontSize * 0.82f
        else -> baseFontSize
    }
    Text(
        text = text,
        color = color,
        fontSize = adjustedFontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HabayebScreen(
    viewModel: FinanceViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

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
    val totalOwedByThem by viewModel.habayebOwedByThemTotalState.collectAsStateWithLifecycle()
    val totalOwedToThem by viewModel.habayebOwedToThemTotalState.collectAsStateWithLifecycle()
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
                .background(LightPurpleBg)
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
                 val focusRequester = remember { FocusRequester() }
                 Column(
                     modifier = Modifier
                         .fillMaxWidth()
                         .statusBarsPadding()
                         .padding(bottom = 8.dp)
                 ) {
                // Header Top Row (Dual State: Normal vs Search Active)
                if (isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .height(46.dp)
                            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(23.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Close Search Icon Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                searchQuery = ""
                                isSearchActive = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.habayeb_close_search),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Search Input field in center
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Right
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(id = R.string.habayeb_search_hint),
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Passive Search Icon
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Right/Start Element: Wallet icon button that goes back, or Delete button when multi-selecting
                        if (isMultiSelectActive) {
                            IconButton(
                                onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDeleteConfirmDialog = true 
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.Red.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(id = R.string.habayeb_delete_bulk),
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onClose()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = stringResource(id = R.string.habayeb_back_to_wallet),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Centered head title
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isMultiSelectActive) stringResource(id = R.string.habayeb_selected_count, selectedCustomerIds.size) else stringResource(id = R.string.habayeb_subtitle),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = stringResource(id = R.string.habayeb_subtitle),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        // Left/End Element: Search glass icon, or Check icon when multi-selecting
                        if (isMultiSelectActive) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(id = R.string.habayeb_select_all),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isSearchActive = true
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(id = R.string.habayeb_search_label),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                 if (false) Column(
                     modifier = Modifier
                         .fillMaxWidth()
                         .alpha(collapseProgress.coerceIn(0f, 1f)),
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     // 2. Big Glassmorphic Net Balance Card (Sleek Slim Edition)
                     Card(
                         shape = RoundedCornerShape(20.dp),
                         colors = CardDefaults.cardColors(
                             containerColor = Color.White.copy(alpha = 0.14f)
                         ),
                         border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(horizontal = 16.dp, vertical = 4.dp)
                     ) {
                         Column(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(vertical = 8.dp),
                             horizontalAlignment = Alignment.CenterHorizontally,
                             verticalArrangement = Arrangement.Center
                         ) {
                             Text(
                                 text = stringResource(id = R.string.habayeb_net_balance),
                                 color = Color.White.copy(alpha = 0.85f),
                                 fontSize = 11.sp,
                                 fontWeight = FontWeight.Bold
                             )
                             Spacer(modifier = Modifier.height(4.dp))
                             val netTotal = totalOwedByThem - totalOwedToThem
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                 IconButton(
                                     onClick = { viewModel.togglePrivacyMode() },
                                     modifier = Modifier.size(24.dp).padding(end = 6.dp)
                                 ) {
                                     Icon(
                                         imageVector = if (isPrivacyModeState.value) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                         contentDescription = stringResource(id = R.string.habayeb_visibility_toggle),
                                         tint = Color.White.copy(alpha = 0.7f)
                                     )
                                 }
                                 AutoScaleText(
                                     text = if (isPrivacyModeState.value) "*****" else formatYemeniRial(netTotal),
                                     baseFontSize = 28.sp,
                                     color = Color.White,
                                     fontWeight = FontWeight.Bold,
                                     textAlign = TextAlign.Center
                                 )
                             }
                         }
                     }

                     // 3. Compact row of 2 balanced pastel Stats Cards (Super Rich & Thin)
                     Row(
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(horizontal = 16.dp, vertical = 4.dp),
                         horizontalArrangement = Arrangement.spacedBy(8.dp),
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         // Right: "لي عند الناس" (Filter Tab 1) - Green Pastel Card
                         val isTab1Selected = selectedFilterTab == 1
                         Card(
                             shape = RoundedCornerShape(12.dp),
                             colors = CardDefaults.cardColors(
                                 containerColor = Color(0xFFE8F5E9)
                             ),
                             border = BorderStroke(
                                 width = if (isTab1Selected) 2.dp else 1.dp,
                                 color = if (isTab1Selected) Color(0xFF10B981) else Color(0xFFA7F3D0)
                             ),
                             modifier = Modifier
                                 .weight(1f)
                                 .clickable {
                                     selectedFilterTab = if (isTab1Selected) 0 else 1
                                     haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                 }
                         ) {
                             Column(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .padding(vertical = 8.dp),
                                 horizontalAlignment = Alignment.CenterHorizontally,
                                 verticalArrangement = Arrangement.Center
                             ) {
                                 Text(
                                     text = stringResource(id = R.string.habayeb_filter_owed_by),
                                     fontSize = 11.sp,
                                     fontWeight = FontWeight.Bold,
                                     color = Color(0xFF047857)
                                 )
                                 Spacer(modifier = Modifier.height(2.dp))
                                 AutoScaleText(
                                     text = formatYemeniRial(totalOwedByThem),
                                     baseFontSize = 14.sp,
                                     color = Color(0xFF10B981),
                                     fontWeight = FontWeight.Bold
                                 )
                             }
                         }

                         // Left: "علي للناس" (Filter Tab 2) - Red Pastel Card
                         val isTab2Selected = selectedFilterTab == 2
                         Card(
                             shape = RoundedCornerShape(12.dp),
                             colors = CardDefaults.cardColors(
                                 containerColor = Color(0xFFFFEBEE)
                             ),
                             border = BorderStroke(
                                 width = if (isTab2Selected) 2.dp else 1.dp,
                                 color = if (isTab2Selected) Color(0xFFEF4444) else Color(0xFFFECACA)
                             ),
                             modifier = Modifier
                                 .weight(1f)
                                 .clickable {
                                     selectedFilterTab = if (isTab2Selected) 0 else 2
                                     haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                 }
                         ) {
                             Column(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .padding(vertical = 8.dp),
                                 horizontalAlignment = Alignment.CenterHorizontally,
                                 verticalArrangement = Arrangement.Center
                             ) {
                                 Text(
                                     text = stringResource(id = R.string.habayeb_filter_owed_to),
                                     fontSize = 11.sp,
                                     fontWeight = FontWeight.Bold,
                                     color = Color(0xFFB91C1C)
                                 )
                                 Spacer(modifier = Modifier.height(2.dp))
                                 AutoScaleText(
                                     text = formatYemeniRial(totalOwedToThem),
                                     baseFontSize = 14.sp,
                                     color = Color(0xFFEF4444),
                                     fontWeight = FontWeight.Bold
                                 )
                             }
                         }
                     }
                 }
             }
         } // Close Floating Header Card

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 76.dp + with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() },
                    bottom = 80.dp
                )
            ) {
                // Item 1: Giant Net Balance Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = activeThemeColor
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.habayeb_net_balance),
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val netTotal = totalOwedByThem - totalOwedToThem
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.togglePrivacyMode() },
                                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPrivacyModeState.value) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = stringResource(id = R.string.habayeb_visibility_toggle),
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    AutoScaleText(
                                        text = if (isPrivacyModeState.value) "*****" else formatYemeniRial(netTotal),
                                        baseFontSize = 20.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Item 2: Compact Row of 2 Interactive Filter Tabs
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Right: "لي عند الناس" (Filter Tab 1) - Green Pastel Card
                        val isTab1Selected = selectedFilterTab == 1
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isTab1Selected) Color(0xFFFEE2E2) else Color(0xFFFFEBEE)
                            ),
                            border = BorderStroke(
                                width = if (isTab1Selected) 1.5.dp else 1.dp,
                                color = if (isTab1Selected) Color(0xFFEF4444) else Color(0xFFFECACA)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedFilterTab = if (isTab1Selected) 0 else 1
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(id = R.string.habayeb_filter_owed_by),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB91C1C)
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    AutoScaleText(
                                        text = if (isPrivacyModeState.value) "*****" else formatYemeniRial(totalOwedByThem),
                                        baseFontSize = 13.sp,
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Left: "علي للناس" (Filter Tab 2) - Green Pastel Card
                        val isTab2Selected = selectedFilterTab == 2
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isTab2Selected) Color(0xFFD1FAE5) else Color(0xFFE8F5E9)
                            ),
                            border = BorderStroke(
                                width = if (isTab2Selected) 1.5.dp else 1.dp,
                                color = if (isTab2Selected) Color(0xFF10B981) else Color(0xFFA7F3D0)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedFilterTab = if (isTab2Selected) 0 else 2
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(id = R.string.habayeb_filter_owed_to),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF047857)
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    AutoScaleText(
                                        text = if (isPrivacyModeState.value) "*****" else formatYemeniRial(totalOwedToThem),
                                        baseFontSize = 13.sp,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

             item {
                 // Compact Smart Filter Toolbar
                 Row(
                     modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Right side (RTL left): Count Badge
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .background(activeSubColor, RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${filteredCustomers.size} زبائن",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B5CF6)
                    )
                }

                // Left side (RTL right): Filtering and Sorting Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Financial Sorting Button
                    val isFinSorted = financialSortMode != 0
                    val finText = when (financialSortMode) {
                        1 -> stringResource(id = R.string.habayeb_sort_amount_desc)
                        2 -> stringResource(id = R.string.habayeb_sort_amount_asc)
                        else -> stringResource(id = R.string.habayeb_sort_amount)
                    }
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isFinSorted) activeSubColor else activeSubColor.copy(alpha = 0.5f))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                historicalSortMode = 0
                                financialSortMode = when (financialSortMode) {
                                    0 -> 1
                                    1 -> 2
                                    else -> 0
                                }
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = finText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = activeThemeColor
                        )
                    }

                    // Historical Sorting Button
                    val isHistSorted = historicalSortMode != 0
                    val histText = when (historicalSortMode) {
                        1 -> stringResource(id = R.string.habayeb_sort_date_desc)
                        2 -> stringResource(id = R.string.habayeb_sort_date_asc)
                        else -> stringResource(id = R.string.habayeb_sort_date)
                    }
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isHistSorted) activeSubColor else activeSubColor.copy(alpha = 0.5f))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                financialSortMode = 0
                                historicalSortMode = when (historicalSortMode) {
                                    0 -> 1
                                    1 -> 2
                                    else -> 0
                                }
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = histText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = activeThemeColor
                        )
                    }
                }
            }
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
                                activeCustomerForHistory = customer.originalCustomer
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
                    .padding(bottom = 96.dp, start = 24.dp) // Elevated to float over bottom Pill Navigation Dock
                    .size(58.dp)
                    .shadow(10.dp, CircleShape, spotColor = RoyalPurple.copy(alpha = 0.6f))
                    .background(RoyalPurple, CircleShape)
                    .border(1.dp, SoftLavender.copy(alpha = 0.3f), CircleShape)
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
                activeSubColor = activeSubColor
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
            val isSingleDelete = customerToDelete != null
            AlertDialog(
                onDismissRequest = { 
                    showDeleteConfirmDialog = false
                    customerToDelete = null 
                },
                title = { 
                    Text(
                        text = if (isSingleDelete) stringResource(id = R.string.habayeb_delete_account_title) else stringResource(id = R.string.habayeb_bulk_delete_title), 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp
                    ) 
                },
                text = { 
                    Text(
                        text = if (isSingleDelete) {
                            stringResource(id = R.string.habayeb_delete_account_confirm, customerToDelete?.name ?: "")
                        } else {
                            stringResource(id = R.string.habayeb_bulk_delete_confirm, selectedCustomerIds.size)
                        }, 
                        fontSize = 13.sp, 
                        color = Color.Gray
                    ) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isSingleDelete) {
                                customerToDelete?.let {
                                    viewModel.deleteHabayebCustomer(it.id)
                                    Toast.makeText(context, context.getString(R.string.habayeb_toast_delete_success), Toast.LENGTH_SHORT).show()
                                }
                                customerToDelete = null
                            } else {
                                viewModel.deleteMultipleHabayebCustomers(selectedCustomerIds.toList())
                                selectedCustomerIds.clear()
                                isMultiSelectActive = false
                                Toast.makeText(context, context.getString(R.string.habayeb_toast_delete_success), Toast.LENGTH_SHORT).show()
                            }
                            showDeleteConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text(stringResource(id = R.string.habayeb_delete_yes), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showDeleteConfirmDialog = false
                            customerToDelete = null
                        }
                    ) {
                        Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                    }
                }
            )
        }

        if (showEditCustomerDialog && editingCustomerForDialog != null) {
            val customer = editingCustomerForDialog!!
            var editedNameStr by remember(customer.name) { mutableStateOf(customer.name) }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            AlertDialog(
                onDismissRequest = { showEditCustomerDialog = false },
                title = {
                    Text(stringResource(id = R.string.habayeb_edit_name_title), fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = editedNameStr,
                            onValueChange = { editedNameStr = it },
                            label = { Text(stringResource(id = R.string.habayeb_account_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
                                viewModel.updateHabayebCustomerName(customer.id, editedNameStr.trim())
                                Toast.makeText(context, context.getString(R.string.habayeb_toast_update_success), Toast.LENGTH_SHORT).show()
                            }
                            showEditCustomerDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor)
                    ) {
                        Text(stringResource(id = R.string.habayeb_save_edit), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditCustomerDialog = false }) {
                        Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                    }
                }
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