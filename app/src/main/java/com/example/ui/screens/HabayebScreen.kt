package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

val activeThemeColor = Color(0xFF3F51B5)    // Royal Indigo
val activeSubColor = Color(0xFFE8EAF6)       // Pastel Lavender
val RoyalPurple = activeThemeColor // For compatibility
val DeepLavender = Color(0xFF1E3A8A).copy(alpha = 0.8f) // Deep solid for gradient/card fallback if used
val SoftLavender = activeSubColor
val LightPurpleBg = Color(0xFFF8FAFC)        // Match Makhzan background
val DarkPurpleText = Color(0xFF1E1B4B)
val HabayebTextSecondary = Color(0xFF4B5563)

// Circular Reveal Shape for Liquid Morphing Effect
class CircularRevealShape(
    val progress: Float,
    val centerOffset: androidx.compose.ui.geometry.Offset,
    val isRelative: Boolean = false
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): androidx.compose.ui.graphics.Outline {
        val maxRadius = kotlin.math.hypot(size.width, size.height)
        val radius = maxRadius * progress
        val actualCenter = if (isRelative) {
            androidx.compose.ui.geometry.Offset(size.width * centerOffset.x, centerOffset.y)
        } else {
            centerOffset
        }
        val path = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(actualCenter, radius))
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

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
                                contentDescription = "إغلاق البحث",
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
                                            text = "بحث باسم العميل...",
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
                                    contentDescription = "حذف جماعي",
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
                                    contentDescription = "الرجوع للمحفظة",
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
                                text = "إدارة ديونك بكل سهولة",
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
                                    contentDescription = "تحديد الكل",
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
                                    contentDescription = "البحث",
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
                                 text = "إجمالي الرصيد الصافي",
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
                                         contentDescription = "عرض المبالغ",
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
                                     text = "لي عند الناس",
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
                                     text = "علي للناس",
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
                                    text = "إجمالي الرصيد الصافي",
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
                                            contentDescription = "عرض المبالغ",
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
                                        text = "لي عند الناس",
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
                                        text = "علي للناس",
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
                        1 -> "أكبر مبلغ 🔽"
                        2 -> "أقل مبلغ 🔼"
                        else -> "حسب المبلغ 💰"
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
                        1 -> "أحدث تاريخ ⏱️"
                        2 -> "أقدم تاريخ ⏱️"
                        else -> "حسب التاريخ 📅"
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
                                    1 -> "لا يوجد مدينين حالياً!"
                                    2 -> "لا يوجد دائنين حالياً!"
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
                    contentDescription = "إضافة عميل أو مورد",
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
                onDismiss = { showAddCustomerDialog = false }
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
                }
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
                }
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
                        text = if (isSingleDelete) stringResource(id = R.string.habayeb_delete_account_title) else "تأكيد الحذف الجماعي الآمن", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp
                    ) 
                },
                text = { 
                    Text(
                        text = if (isSingleDelete) {
                            stringResource(id = R.string.habayeb_delete_account_confirm, customerToDelete?.name ?: "")
                        } else {
                            "هل أنت متأكد من حذف الحسابات المختارة (${selectedCustomerIds.size}) مع جميع سجلاتها المالية نهائياً؟ لا يمكن التراجع عن هذا الإجراء."
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

@Composable
fun AddCustomerPopup(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var nameStr by rememberSaveable { mutableStateOf("") }
    var phoneStr by rememberSaveable { mutableStateOf("") }
    var notesStr by rememberSaveable { mutableStateOf("") }
    
    // First atomic transaction fields
    var initialAmountStr by rememberSaveable { mutableStateOf("") }
    var initialType by rememberSaveable { mutableStateOf("OWED_BY_THEM") } // OWED_BY_THEM (عليه لي) or OWED_TO_THEM (له عندي)

    var showCalculator by rememberSaveable { mutableStateOf(false) }
    var isSavingCustomer by rememberSaveable { mutableStateOf(false) }
    var showConfirmPopup by remember { mutableStateOf(false) }

    // Date Picker state
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val dateStr = remember(selectedCalendar) {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        sdf.format(selectedCalendar.time)
    }

    val year = selectedCalendar.get(Calendar.YEAR)
    val month = selectedCalendar.get(Calendar.MONTH)
    val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedCalendar = newCal
            },
            year,
            month,
            day
        )
    }

    // Auto-focus & keyboard navigation setup
    val focusRequester = remember { FocusRequester() }
    val initialAmountFocusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val softwareKeyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        try {
            focusRequester.requestFocus()
            softwareKeyboardController?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Storage integration launcher
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                notesStr = if (notesStr.isBlank()) {
                    "(تحتوي على مرفق مستند 📎)"
                } else {
                    notesStr.trim() + " (تحتوي على مرفق مستند 📎)"
                }
                Toast.makeText(context, "تم إلحاق المستند من التخزين بنجاح 📎", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "فشل الإرفاق: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            storageLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "الرجاء تفعيل إذن التخزين لإرفاق مستند 📂", Toast.LENGTH_SHORT).show()
        }
    }

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri ->
        contactUri?.let { uri ->
            val details = getContactDetails(context, uri)
            if (details != null) {
                nameStr = details.first
                phoneStr = details.second
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, "الرجاء تفعيل إذن جهات الاتصال لقراءتها تلقائياً 📖", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Snug vertical spacing
                ) {
                    Text(
                        text = stringResource(id = R.string.habayeb_add_account),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = activeThemeColor
                    )

                    // 1. Name field
                    OutlinedTextField(
                        value = nameStr,
                        onValueChange = { nameStr = it },
                        label = { Text(stringResource(id = R.string.habayeb_account_name), fontSize = 13.sp) },
                        placeholder = { Text(stringResource(id = R.string.habayeb_edit_name_desc), fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { initialAmountFocusRequester.requestFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                        )
                    )

                    // 2. Amount field with calculator trailingIcon
                    OutlinedTextField(
                        value = initialAmountStr,
                        onValueChange = { initialAmountStr = it },
                        label = { Text(stringResource(id = R.string.habayeb_amount), fontSize = 13.sp) },
                        placeholder = { Text("0.0", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(initialAmountFocusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showCalculator = true }) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = stringResource(id = R.string.habayeb_calculator),
                                    tint = activeThemeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )

                    // 3. Status switcher buttons: مستحقات لي vs التزامات علي
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (initialType == "OWED_BY_THEM") Color(0xFFEF4444) else Color.Transparent)
                                .clickable {
                                    initialType = "OWED_BY_THEM"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.habayeb_register_owed_by),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (initialType == "OWED_BY_THEM") Color.White else Color(0xFF475569)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (initialType == "OWED_TO_THEM") Color(0xFF10B981) else Color.Transparent)
                                .clickable {
                                    initialType = "OWED_TO_THEM"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.habayeb_register_owed_to),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (initialType == "OWED_TO_THEM") Color.White else Color(0xFF475569)
                            )
                        }
                    }

                    // 4. Interactive Date Picker Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(activeSubColor.copy(alpha = 0.5f))
                            .clickable { datePickerDialog.show() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = stringResource(id = R.string.habayeb_tx_date),
                                tint = activeThemeColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.habayeb_tx_date),
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = dateStr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeThemeColor
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Footer Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                        }

                        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 1500f),
                            label = "SaveBtnScale"
                        )
                        val saveBtnColor = if (initialType == "OWED_BY_THEM") Color(0xFFEF4444) else Color(0xFF10B981)

                        Button(
                            enabled = !isSavingCustomer,
                            onClick = {
                                if (isSavingCustomer) return@Button
                                isSavingCustomer = true

                                if (nameStr.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.habayeb_toast_enter_name), Toast.LENGTH_SHORT).show()
                                    isSavingCustomer = false
                                    return@Button
                                }
                                val initialAmount = initialAmountStr.toDoubleOrNull() ?: 0.0
                                if (initialAmount < 0.0) {
                                    Toast.makeText(context, context.getString(R.string.habayeb_toast_initial_amount_negative), Toast.LENGTH_SHORT).show()
                                    isSavingCustomer = false
                                    return@Button
                                }

                                showConfirmPopup = true
                                isSavingCustomer = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = saveBtnColor),
                            shape = RoundedCornerShape(12.dp),
                            interactionSource = interactionSource,
                            modifier = Modifier.weight(1.2f).graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            Text(stringResource(id = R.string.habayeb_confirm_save))
                        }
                    }
                }
            }
        }
    }

    if (showConfirmPopup) {
        val secondStepNotesFocusRequester = remember { FocusRequester() }
        Dialog(onDismissRequest = { showConfirmPopup = false }) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding()
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.habayeb_last_step),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = activeThemeColor
                        )

                        OutlinedTextField(
                            value = phoneStr,
                            onValueChange = { phoneStr = it },
                            label = { Text(stringResource(id = R.string.habayeb_phone_optional), fontSize = 13.sp) },
                            placeholder = { Text(stringResource(id = R.string.habayeb_contact_picker), fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { secondStepNotesFocusRequester.requestFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = activeThemeColor,
                                focusedLabelColor = activeThemeColor,
                                cursorColor = activeThemeColor
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        contactPickerLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Contacts,
                                        contentDescription = stringResource(id = R.string.habayeb_contact_picker),
                                        tint = activeThemeColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        )

                        Column {
                            OutlinedTextField(
                                value = notesStr,
                                onValueChange = { notesStr = it },
                                label = { Text(stringResource(id = R.string.habayeb_details_required), fontSize = 13.sp) },
                                placeholder = { Text(stringResource(id = R.string.habayeb_starting_balance), fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                isError = notesStr.isBlank(),
                                modifier = Modifier.fillMaxWidth().focusRequester(secondStepNotesFocusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { 
                                        if (notesStr.isNotBlank()) {
                                            focusManager.clearFocus()
                                            isSavingCustomer = true
                                            val initialAmount = initialAmountStr.toDoubleOrNull() ?: 0.0
                                            val transactionTimestamp = selectedCalendar.timeInMillis / 1000

                                            val newCustomer = HabayebCustomer(
                                                id = "cust_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                                                name = nameStr.trim(),
                                                phone = phoneStr.trim(),
                                                notes = notesStr.trim(),
                                                createdAt = transactionTimestamp
                                            )
                                            viewModel.saveHabayebCustomer(newCustomer, initialAmount, initialType, transactionTimestamp, notesStr.trim())
                                            Toast.makeText(context, context.getString(R.string.habayeb_toast_save_success), Toast.LENGTH_SHORT).show()
                                            showConfirmPopup = false
                                            onDismiss()
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeThemeColor,
                                    focusedLabelColor = activeThemeColor,
                                    cursorColor = activeThemeColor
                                )
                            )
                            if (notesStr.isBlank()) {
                                Text(
                                    text = stringResource(id = R.string.habayeb_required_field),
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfirmPopup = false },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(id = R.string.habayeb_go_back), color = Color.Gray)
                            }

                            Button(
                                enabled = notesStr.isNotBlank() && !isSavingCustomer,
                                onClick = {
                                    isSavingCustomer = true
                                    val initialAmount = initialAmountStr.toDoubleOrNull() ?: 0.0
                                    val transactionTimestamp = selectedCalendar.timeInMillis / 1000

                                    val newCustomer = HabayebCustomer(
                                        id = "cust_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                                        name = nameStr.trim(),
                                        phone = phoneStr.trim(),
                                        notes = notesStr.trim(),
                                        createdAt = transactionTimestamp
                                    )
                                    viewModel.saveHabayebCustomer(newCustomer, initialAmount, initialType, transactionTimestamp, notesStr.trim())
                                    Toast.makeText(context, context.getString(R.string.habayeb_toast_save_success), Toast.LENGTH_SHORT).show()
                                    showConfirmPopup = false
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(id = R.string.habayeb_save_final))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCalculator) {
        CalculatorModal(
            onDismiss = { showCalculator = false },
            onConfirmExpression = { value ->
                initialAmountStr = value.toString()
                showCalculator = false
            }
        )
    }
}

// Detailed history of debts overlay page
@Composable
fun CustomerHistoryOverlay(
    customer: HabayebCustomer,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onAddTransaction: (HabayebCustomer, String) -> Unit
) {
    val customers by viewModel.habayebCustomersState.collectAsStateWithLifecycle()
    val activeCustomer = customers.find { it.id == customer.id } ?: customer

    val transactions by viewModel.habayebTransactionsState.collectAsStateWithLifecycle()
    val currencySymbol = viewModel.settingsState.collectAsStateWithLifecycle().value.currencySymbol
    val isPrivacyModeState = viewModel.isPrivacyModeEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val customerTxs = remember(transactions, customer) {
        transactions.filter { it.customerId == customer.id }.sortedByDescending { it.timestamp }
    }

    val owedByThem = customerTxs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
    val paymentByThem = customerTxs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
    val owedToThem = customerTxs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
    val paymentToThem = customerTxs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
    val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)

    var editingTransactionForDialog by remember { mutableStateOf<HabayebTransaction?>(null) }
    var showAddTransactionDialogFromHistory by remember { mutableStateOf<HabayebCustomer?>(null) }
    var defaultTransactionTypeFromHistory by remember { mutableStateOf("OWED_BY_THEM") }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                // Top Custom Header: Red Delete left, "تفاصيل الحساب" center-right, Purple Close right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left extreme: Delete Customer
                    var confirmDeleteCust by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { confirmDeleteCust = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFFFF1F2), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف العميل",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (confirmDeleteCust) {
                        AlertDialog(
                            onDismissRequest = { confirmDeleteCust = false },
                            title = { Text("حذف كلي للحساب 🗑️", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                            text = { Text("هل أنت متأكد من حذف الحساب '${activeCustomer.name}' مع جميع حركاته المالية نهائياً؟") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.deleteHabayebCustomer(activeCustomer.id)
                                        Toast.makeText(context, "تم حذف الحساب بنجاح", Toast.LENGTH_SHORT).show()
                                        confirmDeleteCust = false
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                                ) {
                                    Text("نعم، حذف", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDeleteCust = false }) {
                                    Text("إلغاء", color = Color.Gray)
                                }
                            }
                        )
                    }

                    // Centered Text "تفاصيل الحساب"
                    Text(
                        text = "تفاصيل الحساب",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeThemeColor
                    )

                    // Right extreme: Return/Close indicator
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .background(activeSubColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "عودة",
                            tint = activeThemeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // State for editing name
                var showEditNameDialog by remember { mutableStateOf(false) }
                var editedNameStr by remember(activeCustomer.name) { mutableStateOf(activeCustomer.name) }

                if (showEditNameDialog) {
                    val editNameFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        editNameFocusRequester.requestFocus()
                    }
                    AlertDialog(
                        onDismissRequest = { showEditNameDialog = false },
                        title = {
                            Text("تعديل اسم الزبون", fontWeight = FontWeight.Bold)
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
                                    label = { Text("الاسم") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().focusRequester(editNameFocusRequester),
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
                                        viewModel.updateHabayebCustomerName(activeCustomer.id, editedNameStr.trim())
                                        // The flow update should propagate and update UI naturally. 
                                        // But just in case, we might need a manual trigger or it's handled by Flow
                                    }
                                    showEditNameDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor)
                            ) {
                                Text("حفظ التعديل")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEditNameDialog = false }) {
                                Text("إلغاء", color = Color.Gray)
                            }
                        }
                    )
                }

                // Consolidated Glassmorphic Header Card (Super High Density, Zero Waste Padding)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = activeSubColor.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, activeThemeColor.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Mini Avatar Circle
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(getInitialColor(activeCustomer.name).copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeCustomer.name.firstOrNull()?.toString() ?: "",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeThemeColor
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(10.dp))
                            
                            Column(horizontalAlignment = Alignment.Start) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = activeCustomer.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { 
                                            editedNameStr = activeCustomer.name
                                            showEditNameDialog = true
                                        }, 
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل الاسم",
                                            modifier = Modifier.size(12.dp),
                                            tint = activeThemeColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = activeCustomer.phone.ifEmpty { "لا يوجد هاتف" },
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Net balance summary on left side in RTL (right end visually)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "الرصيد المتبقي",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            val textBalanceColor = when {
                                netDebt > 0.0 -> Color(0xFFDC2626) // Red
                                netDebt < 0.0 -> Color(0xFF16A34A) // Green
                                else -> Color.DarkGray
                            }
                            val stateLabel = when {
                                netDebt > 0.0 -> "عليـه دين"
                                netDebt < 0.0 -> "لـه دين"
                                else -> "متزن"
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "($stateLabel) ",
                                    fontSize = 9.sp,
                                    color = textBalanceColor,
                                    fontWeight = FontWeight.Bold
                                )
                                AutoScaleText(
                                    text = formatYemeniRial(kotlin.math.abs(netDebt)),
                                    baseFontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textBalanceColor
                                )
                            }
                        }
                    }
                }

                // Action strip: Share, Print & Heading title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. زر تصدير كشف الحساب الفخم والمطور (PDF)
                        AssistChip(
                            onClick = {
                                // استدعاء دالة توليد التقرير المطور ومشاركته فوراً
                                generateModernPdfReport(
                                    context = context,
                                    title = "كشف حساب: ${activeCustomer.name}",
                                    transactions = customerTxs.map { tx ->
                                        PdfTransaction(
                                            date = "",
                                            description = tx.description,
                                            amount = tx.amount.toLong(),
                                            type = tx.type,
                                            timestamp = tx.timestamp * 1000
                                        )
                                    }
                                )
                            },
                            label = { Text("تصدير كشف PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Description, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(15.dp)
                                ) 
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = activeThemeColor.copy(alpha = 0.08f),
                                labelColor = activeThemeColor,
                                leadingIconContentColor = activeThemeColor
                            ),
                            border = BorderStroke(1.dp, activeThemeColor.copy(alpha = 0.2f))
                        )

                        // 2. زر مشاركة النص السريع
                        AssistChip(
                            onClick = {
                                // مشاركة النص السريع للمعاملات
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    val textBody = "كشف حساب ${activeCustomer.name}:\n" + customerTxs.joinToString("\n") { tx ->
                                        val txDate = try {
                                            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.ENGLISH)
                                            sdf.format(java.util.Date(tx.timestamp * 1000))
                                        } catch (e: Exception) {
                                            ""
                                        }
                                        "$txDate: ${formatYemeniRial(tx.amount)}"
                                    }
                                    putExtra(android.content.Intent.EXTRA_TEXT, textBody)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "مشاركة نصية سريعة"))
                            },
                            label = { Text("مشاركة نصية", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Share, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(14.dp)
                                ) 
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Gray.copy(alpha = 0.08f),
                                labelColor = Color.DarkGray,
                                leadingIconContentColor = Color.DarkGray
                            ),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                        )
                    }
                }

                // Dynamic previous transactions list
                if (customerTxs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا يوجد معاملات مسجلة بعد لهذا الحساب.", fontSize = 12.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(customerTxs, key = { it.id }) { tx ->
                            val formattedDate = remember(tx.timestamp) {
                                val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.ENGLISH)
                                val formatted = sdf.format(Date(tx.timestamp * 1000))
                                formatted.replace("AM", "ص").replace("PM", "م")
                                    .replace("am", "ص").replace("pm", "م")
                            }

                            val isPositive = tx.type == "PAYMENT_BY_THEM" || tx.type == "OWED_TO_THEM"
                            val indicatorColor = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626)
                            val iconEmoji = when (tx.type) {
                                "OWED_BY_THEM" -> "📝" // دين عليه
                                "OWED_TO_THEM" -> "📝" // دين له
                                "PAYMENT_BY_THEM", "PAYMENT_TO_THEM" -> "💸" // سداد
                                else -> "💼"
                            }
                            val isDebt = tx.type == "OWED_BY_THEM" || tx.type == "OWED_TO_THEM"
                            val sign = if (isDebt) "+" else "-"

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Rightmost vertical color indicator (Touches the right border in RTL)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(4.dp)
                                            .background(indicatorColor)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // 2. Small Avatar/Icon
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(indicatorColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = iconEmoji,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // 3. Middle: transaction details (Title & subtitle/description)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        val readableType = when (tx.type) {
                                            "OWED_BY_THEM" -> "دين عليه"     // في حال كان الحساب عميل وعليه دين
                                            "PAYMENT_BY_THEM" -> "استلام دفعة"    // في حال سدد العميل (بدلاً من سداد مقبوض)
                                            "OWED_TO_THEM" -> "دين علي"     // في حال كان الحساب مورد وله دين (بدلاً من دين له)
                                            "PAYMENT_TO_THEM" -> "سداد دفعة" // في حال سددنا للمورد (بدلاً من سداد مدفوع)
                                            else -> "معاملة"
                                        }

                                        if (tx.description.isNotEmpty()) {
                                            // الملاحظة التفصيلية للمستخدم تصبح هي العنوان الرئيسي لسهولة القراءة والتمييز
                                            Text(
                                                text = tx.description,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            // فاصل عمودي ناعم لمنع تداخل النصوص
                                            Spacer(modifier = Modifier.height(2.dp))
                                            // نوع المعاملة يصبح فرعياً وناعماً بوزن عادي وحجم أصغر لتجنب تكرار الهيكل البصري
                                            Text(
                                                text = readableType,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFF64748B)
                                            )
                                        } else {
                                            // في حال عدم كتابة تفاصيل، يظهر نوع المعاملة كعنوان رئيسي بحجم ووزن معتدل
                                            Text(
                                                text = readableType,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // 4. Far Left: Monetary value and Timestamp
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = "$sign${formatYemeniRial(tx.amount)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = indicatorColor
                                        )
                                        Text(
                                            text = formattedDate,
                                            fontSize = 9.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    // Edit & delete options
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                editingTransactionForDialog = tx
                                                defaultTransactionTypeFromHistory = tx.type
                                                showAddTransactionDialogFromHistory = customer
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "تعديل",
                                                tint = activeThemeColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.deleteHabayebTransaction(tx.id)
                                                Toast.makeText(context, "تم حذف المعاملة", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف",
                                                tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Beautiful custom floating button at the bottom extreme
            FloatingActionButton(
                onClick = {
                    val defaultType = if (netDebt >= 0.0) "OWED_BY_THEM" else "OWED_TO_THEM"
                    onAddTransaction(activeCustomer, defaultType)
                },
                containerColor = activeThemeColor,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 90.dp, end = 20.dp, start = 20.dp), // Elevated to float over bottom Pill Navigation Dock
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "معاملة جديدة", modifier = Modifier.size(18.dp))
                    Text("معاملة جديدة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

                }
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
            }
        )
    }
}

fun getDebtTypeColor(type: String): Color {
    return when (type) {
        "OWED_BY_THEM", "PAYMENT_TO_THEM" -> Color(0xFFBE123C)
        "OWED_TO_THEM", "PAYMENT_BY_THEM" -> Color(0xFF047857)
        else -> Color.DarkGray
    }
}

// Add/Edit Transaction popup
@Composable
fun AddTransactionPopup(
    customer: HabayebCustomer,
    viewModel: FinanceViewModel,
    initialSelectedType: String = "OWED_BY_THEM",
    editingTransaction: HabayebTransaction? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currencySymbol = viewModel.settingsState.collectAsStateWithLifecycle().value.currencySymbol

    var amountStr by rememberSaveable { mutableStateOf(editingTransaction?.amount?.toInt()?.toString() ?: "") }
    var descStr by rememberSaveable { mutableStateOf(editingTransaction?.description ?: "") }
    var selectedType by rememberSaveable { mutableStateOf(editingTransaction?.type ?: initialSelectedType) }
    
    val amountFocusRequester = remember { FocusRequester() }
    val descFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        try {
            amountFocusRequester.requestFocus()
            softwareKeyboardController?.show()
        } catch(e: Exception) {}
    }

    val isLendMode = selectedType == "OWED_BY_THEM" || selectedType == "PAYMENT_BY_THEM"
    var isLendOperationSelected by rememberSaveable { mutableStateOf(isLendMode) }
    var dateMillis by rememberSaveable { mutableStateOf(editingTransaction?.timestamp?.let { it * 1000 } ?: System.currentTimeMillis()) }
    var showCalculator by rememberSaveable { mutableStateOf(false) }
    var syncAsMainIncome by remember(customer.id, editingTransaction?.id) { 
        mutableStateOf(editingTransaction?.linkedMainTxId != null) 
    }
    var isSaving by remember { mutableStateOf(false) }

    val activeColor = if (selectedType == "OWED_BY_THEM" || selectedType == "OWED_TO_THEM") Color(0xFFEF5350) else Color(0xFF66BB6A)

    val datePickerDialog = remember {
        val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        dateMillis = calendar.timeInMillis
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val debtInteractionSource = remember { MutableInteractionSource() }
            val isDebtPressed by debtInteractionSource.collectIsPressedAsState()
            val debtScale by animateFloatAsState(
                targetValue = if (isDebtPressed) 0.95f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = 1500f
                ),
                label = "DebtBtnScale"
            )

            val payInteractionSource = remember { MutableInteractionSource() }
            val isPayPressed by payInteractionSource.collectIsPressedAsState()
            val payScale by animateFloatAsState(
                targetValue = if (isPayPressed) 0.95f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = 1500f
                ),
                label = "PayBtnScale"
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header (title and back click)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(40.dp)) // centered title spacer

                    Text(
                        text = if (editingTransaction != null) "تعديل معاملة" else "معاملة جديدة",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeThemeColor
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(activeSubColor, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = activeThemeColor, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Target Account indicate pill
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = activeSubColor.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(activeThemeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = customer.name?.firstOrNull()?.toString() ?: "ا",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "حساب: ${customer.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeThemeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Primary Operation Class Selector (Lend Mode vs. Borrow Mode) - Super beautiful RTL layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(activeSubColor.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLendOperationSelected) Color(0xFFEF4444) else Color.Transparent)
                            .clickable {
                                isLendOperationSelected = true
                                selectedType = "OWED_BY_THEM"
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "تسجيل دين لي",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLendOperationSelected) Color.White else Color(0xFFEF4444)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isLendOperationSelected) Color(0xFF10B981) else Color.Transparent)
                            .clickable {
                                isLendOperationSelected = false
                                selectedType = "OWED_TO_THEM"
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "تسجيل دين علي",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isLendOperationSelected) Color.White else Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Secondary Sub-Selector (Interactive Icon Cards)
                val isNegativeAct = selectedType == "OWED_BY_THEM" || selectedType == "OWED_TO_THEM"
                val isPositiveAct = selectedType == "PAYMENT_BY_THEM" || selectedType == "PAYMENT_TO_THEM"

                val dynamicThemeColor = if (isLendOperationSelected) Color(0xFFEF4444) else Color(0xFF10B981)
                val dynamicSubColor = if (isLendOperationSelected) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Debt Card (دين جديد)
                    val debtBg = if (isNegativeAct) Color(0xFF1B3B6F) else Color(0xFFF1F5F9)
                    val debtContentColor = if (isNegativeAct) Color.White else Color(0xFF475569)
                    val debtBorder = if (isNegativeAct) null else BorderStroke(1.dp, Color(0xFFE2E8F0))
                    val debtShadow = if (isNegativeAct) 6.dp else 0.dp
                    
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = debtBg),
                        border = debtBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = debtShadow),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = debtScale
                                scaleY = debtScale
                            }
                            .clickable(
                                interactionSource = debtInteractionSource,
                                indication = null,
                                onClick = {
                                    selectedType = if (isLendOperationSelected) "OWED_BY_THEM" else "OWED_TO_THEM"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📝", fontSize = 14.sp)
                                if (isNegativeAct) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            Crossfade(targetState = isLendOperationSelected, animationSpec = tween(150), label = "DebtSubLabel") { lendMode ->
                                val textLabel = if (lendMode) "تسجيل دين لي" else "تسجيل دين علي"
                                Text(
                                    text = textLabel,
                                    color = debtContentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // 2. Payment Card (سداد دفعة)
                    val payBg = if (isPositiveAct) Color(0xFF1B3B6F) else Color(0xFFF1F5F9)
                    val payContentColor = if (isPositiveAct) Color.White else Color(0xFF475569)
                    val payBorder = if (isPositiveAct) null else BorderStroke(1.dp, Color(0xFFE2E8F0))
                    val payShadow = if (isPositiveAct) 6.dp else 0.dp

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = payBg),
                        border = payBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = payShadow),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = payScale
                                scaleY = payScale
                            }
                            .clickable(
                                interactionSource = payInteractionSource,
                                indication = null,
                                onClick = {
                                    selectedType = if (isLendOperationSelected) "PAYMENT_BY_THEM" else "PAYMENT_TO_THEM"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "💸", fontSize = 14.sp)
                                if (isPositiveAct) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            Crossfade(targetState = isLendOperationSelected, animationSpec = tween(150), label = "PaySubLabel") { lendMode ->
                                val textLabel = if (lendMode) "استلام دفعة" else "سداد دفعة"
                                Text(
                                    text = textLabel,
                                    color = payContentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Input box Centered with Calculator leading and YR trailing
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountFocusRequester),
                    placeholder = {
                        Text(
                            text = "المبلغ بالريال اليمني *",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { descFocusRequester.requestFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = dynamicThemeColor,
                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f),
                        cursorColor = dynamicThemeColor
                    ),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    leadingIcon = {
                        IconButton(onClick = { showCalculator = true }) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "الآلة الحاسبة",
                                tint = dynamicThemeColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingIcon = {
                        Text(
                            text = "YR",
                            color = dynamicThemeColor,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            fontSize = 14.sp
                        )
                    },
                    shape = RoundedCornerShape(18.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Detail comments area with leading Hamburger symbol
                OutlinedTextField(
                    value = descStr,
                    onValueChange = { descStr = it },
                    placeholder = {
                        Text(
                            text = "تفاصيل المعاملة / البيان (اختياري)",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = dynamicThemeColor,
                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f),
                        cursorColor = dynamicThemeColor
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 13.sp),
                    leadingIcon = {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    },
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .focusRequester(descFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Date displays container
                val formattedSelectedDate = remember(dateMillis) {
                    val sdf = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.ENGLISH)
                    val formatted = sdf.format(Date(dateMillis))
                    formatted.replace("AM", "ص").replace("PM", "م")
                        .replace("am", "ص").replace("pm", "م")
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(dynamicSubColor)
                        .clickable { datePickerDialog.show() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "تعديل التاريخ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = dynamicThemeColor
                        )

                        Text(
                            text = formattedSelectedDate,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )

                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "التاريخ",
                            tint = dynamicThemeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 5. Sync Option checkbox for Main Mizan Al-Dar Income
                if (selectedType == "PAYMENT_BY_THEM") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(dynamicSubColor)
                            .clickable { syncAsMainIncome = !syncAsMainIncome }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💰", fontSize = 16.sp)
                            Text(
                                text = "تضمين كإيراد في الحساب الرئيسي",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = dynamicThemeColor
                            )
                        }
                        Switch(
                            checked = syncAsMainIncome,
                            onCheckedChange = { syncAsMainIncome = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = dynamicThemeColor
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Large action button
                Button(
                    enabled = !isSaving,
                    onClick = {
                        softwareKeyboardController?.hide()
                        if (isSaving) return@Button
                        isSaving = true

                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            Toast.makeText(context, "الرجاء كتابة مبلغ المعاملة بصحيح", Toast.LENGTH_SHORT).show()
                            isSaving = false
                            return@Button
                        }

                        if (editingTransaction != null) {
                            viewModel.deleteHabayebTransaction(editingTransaction.id)
                        }

                        val presetMainTxId = if (selectedType == "PAYMENT_BY_THEM" && syncAsMainIncome) {
                            "tx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
                        } else null

                        if (presetMainTxId != null) {
                            viewModel.addTransaction(
                                type = "INCOME",
                                category = "أخرى",
                                amount = amount,
                                description = "سداد دين: ${customer.name}${if (descStr.isNotBlank()) " - " + descStr.trim() else ""}",
                                timestamp = dateMillis / 1000,
                                presetId = presetMainTxId
                            )
                        }

                        viewModel.addHabayebTransaction(
                            customerId = customer.id,
                            type = selectedType,
                            amount = amount,
                            desc = descStr.trim(),
                            timestamp = dateMillis / 1000,
                            linkedMainTxId = presetMainTxId
                        )
                        Toast.makeText(context, "تم حفظ المعاملة بنجاح", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicThemeColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "تأكيد وحفظ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
        }
    }

    if (showCalculator) {
        CalculatorModal(
            onDismiss = { showCalculator = false },
            onConfirmExpression = { value ->
                amountStr = value.toInt().toString()
                showCalculator = false
            }
        )
    }
}

// INDEPENDENT POPUP DIALOG FOR THE CALCULATOR
@Composable
fun CalculatorModal(
    onDismiss: () -> Unit,
    onConfirmExpression: (Double) -> Unit
) {
    var rawExpression by remember { mutableStateOf("") }
    val resultPreview = remember(rawExpression) {
        if (rawExpression.isEmpty()) null
        else evaluateSimpleExpression(rawExpression)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = activeThemeColor), // Deep Purple theme
            modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header of Calculator
                Text("حاسبة الديون السريعة 🧮", color = activeSubColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Formula Monitor screen
                Card(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E0F34)),
                    border = BorderStroke(1.dp, Color(0xFF9333EA))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = rawExpression.ifEmpty { "0" },
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (resultPreview != null) {
                            Text(
                                text = "= ${resultPreview.toInt()}",
                                fontSize = 13.sp,
                                color = Color(0xFF86EFAC),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Layout of Keys
                val keysRow1 = listOf("÷", "9", "8", "7")
                val keysRow2 = listOf("×", "6", "5", "4")
                val keysRow3 = listOf("-", "3", "2", "1")
                val keysRow4 = listOf("+", "00", "0", ".")
                val keysRow5 = listOf("=", "C", "⌫")

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(keysRow1, keysRow2, keysRow3, keysRow4, keysRow5).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { char ->
                                val buttonColor = when (char) {
                                    "÷", "×", "-", "+" -> Color(0xFF9333EA)
                                    "=" -> Color(0xFF059669)
                                    "C", "⌫" -> Color(0xFFEF4444)
                                    else -> Color.White.copy(alpha = 0.1f)
                                }
                                val textColor = Color.White

                                Box(
                                    modifier = Modifier
                                        .weight(if (char == "=" && row.size == 3) 1.2f else 1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(buttonColor)
                                        .clickable {
                                            when (char) {
                                                "=" -> {
                                                    val finalRes = evaluateSimpleExpression(rawExpression)
                                                    if (finalRes != null) {
                                                        rawExpression = if (finalRes % 1.0 == 0.0) finalRes.toInt().toString() else finalRes.toString()
                                                    }
                                                }
                                                "C" -> rawExpression = ""
                                                "⌫" -> if (rawExpression.isNotEmpty()) rawExpression = rawExpression.dropLast(1)
                                                else -> rawExpression += char
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char, fontSize = if (char.length > 1) 13.sp else 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm and carry forward buttons
                Button(
                    onClick = {
                        val finalValue = evaluateSimpleExpression(rawExpression) ?: rawExpression.toDoubleOrNull() ?: 0.0
                        onConfirmExpression(finalValue)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7B54)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("تأكيد الحساب وترحيله 📥", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

// Safe equation evaluator tokenizer
fun evaluateSimpleExpression(expr: String): Double? {
    try {
        val sanitized = expr.replace("×", "*").replace("÷", "/")
        val tokens = mutableListOf<String>()
        var currentNum = StringBuilder()
        for (char in sanitized) {
            if (char.isDigit() || char == '.') {
                currentNum.append(char)
            } else if (char in listOf('+', '-', '*', '/')) {
                if (currentNum.isNotEmpty()) {
                    tokens.add(currentNum.toString())
                    currentNum = StringBuilder()
                }
                tokens.add(char.toString())
            }
        }
        if (currentNum.isNotEmpty()) {
            tokens.add(currentNum.toString())
        }
        
        if (tokens.isEmpty()) return null
        
        // Product stage
        val intermediateTokens = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == "*" || token == "/") {
                if (intermediateTokens.isEmpty() || i + 1 >= tokens.size) return null
                val prev = intermediateTokens.removeAt(intermediateTokens.size - 1).toDouble()
                val next = tokens[i + 1].toDouble()
                val res = if (token == "*") prev * next else prev / next
                intermediateTokens.add(res.toString())
                i += 2
            } else {
                intermediateTokens.add(token)
                i++
            }
        }
        
        // Sum additions stage
        if (intermediateTokens.isEmpty()) return null
        var result = intermediateTokens[0].toDouble()
        var j = 1
        while (j < intermediateTokens.size) {
            val op = intermediateTokens[j]
            if (j + 1 >= intermediateTokens.size) break
            val nextVal = intermediateTokens[j + 1].toDouble()
            if (op == "+") {
                result += nextVal
            } else if (op == "-") {
                result -= nextVal
            }
            j += 2
        }
        return result
    } catch (e: Exception) {
        return null
    }
}

// Contacts Picker Resolving helper (Safe and defensive with international prefix support)
fun getContactDetails(context: Context, contactUri: android.net.Uri): Pair<String, String>? {
    var name = ""
    var phone = ""
    try {
        val cr = context.contentResolver
        cr.query(contactUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: ""
                }
                
                val idIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                if (idIndex >= 0) {
                    val contactId = cursor.getString(idIndex)
                    val hasPhoneIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    val hasPhone = if (hasPhoneIndex >= 0) cursor.getString(hasPhoneIndex) else null
                    
                    if (hasPhone == "1") {
                        cr.query(
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(contactId),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                val numberIndex = phoneCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numberIndex >= 0) {
                                    phone = phoneCursor.getString(numberIndex) ?: ""
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("HabayebScreen", "خطأ آمن في جلب جهة الاتصال: ${e.message}", e)
    }

    try {
        // Safe Phone Sanitization: remove spaces, dashes, symbols and keep only numbers and +
        val cleanedPhone = phone.replace(Regex("[^0-9+]"), "")
        if (name.isNotEmpty()) {
            return Pair(name, cleanedPhone)
        }
    } catch (e: Exception) {
        android.util.Log.e("HabayebScreen", "خطأ آمن في تطهير رقم جهة الاتصال: ${e.message}", e)
    }

    if (name.isNotEmpty()) {
        return Pair(name, "")
    }
    return null
}

private fun drawArabicText(
    canvas: android.graphics.Canvas,
    text: String,
    x: Float,
    y: Float,
    width: Int,
    paint: android.graphics.Paint,
    alignment: android.text.Layout.Alignment = android.text.Layout.Alignment.ALIGN_NORMAL
) {
    val textPaint = android.text.TextPaint(paint)
    val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
    } else {
        @Suppress("DEPRECATION")
        android.text.StaticLayout(text, textPaint, width, alignment, 1f, 0f, false)
    }
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
}

fun generateAndHandleCustomerPdfReport(
    context: android.content.Context,
    customer: com.example.data.local.HabayebCustomer,
    netDebt: Double,
    transactions: List<com.example.data.local.HabayebTransaction>,
    action: String // "VIEW" or "SHARE"
) {
    val pdfDocument = android.graphics.pdf.PdfDocument()
    val pageWidth = 595
    val pageHeight = 842

    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    // Load business profile details
    val prefs = context.getSharedPreferences("business_profile", android.content.Context.MODE_PRIVATE)
    val bizName = prefs.getString("biz_name", "") ?: ""
    val bizDesc = prefs.getString("biz_desc", "") ?: ""
    val bizLogoPath = prefs.getString("biz_logo_path", "") ?: ""
    val bizPhones = mutableListOf<String>()
    try {
        val phonesJson = prefs.getString("biz_phones", "[]") ?: "[]"
        val jsonArray = org.json.JSONArray(phonesJson)
        for (i in 0 until jsonArray.length()) {
            bizPhones.add(jsonArray.getString(i))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Colors & Paints
    val primaryColorHex = "#3F51B5"

    // Draw dynamic business profile header
    val displayedName = if (bizName.isNotBlank()) bizName else "ميزان الدار"
    val displayedDesc = if (bizDesc.isNotBlank()) bizDesc else "التطبيق المالي للتدابير وتنسيق الميزانية"

    if (bizLogoPath.isNotEmpty()) {
        try {
            val logoFile = java.io.File(bizLogoPath)
            if (logoFile.exists()) {
                val rawBitmap = android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                if (rawBitmap != null) {
                    val scaledLogo = android.graphics.Bitmap.createScaledBitmap(rawBitmap, 45, 45, true)
                    canvas.drawBitmap(scaledLogo, 35f, 40f, null)

                    val framePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor(primaryColorHex)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1f
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(33f, 38f, 82f, 87f, 4f, 4f, framePaint)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val paintBizName = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 15f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    val paintBizDesc = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#475569")
        textSize = 10f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }
    val paintBizPhones = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#64748B")
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }

    drawArabicText(canvas, displayedName, 35f, 45f, 525, paintBizName, android.text.Layout.Alignment.ALIGN_NORMAL)
    drawArabicText(canvas, displayedDesc, 35f, 65f, 525, paintBizDesc, android.text.Layout.Alignment.ALIGN_NORMAL)

    val phonesToDraw = if (bizPhones.isNotEmpty()) bizPhones else listOf("هوية بصرية معتمدة")
    val phonesStr = "📞 " + phonesToDraw.joinToString("  |  ")
    drawArabicText(canvas, phonesStr, 35f, 82f, 525, paintBizPhones, android.text.Layout.Alignment.ALIGN_NORMAL)

    // Divider Line under Header
    val paintDivider = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        strokeWidth = 1f
        style = android.graphics.Paint.Style.STROKE
    }
    canvas.drawLine(35f, 115f, (pageWidth - 35).toFloat(), 115f, paintDivider)

    // Prepare content paints
    val paintTitle = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 18f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    val paintSectionHeader = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 13f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    val paintLabel = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 11f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }
    val paintValue = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor(primaryColorHex)
        textSize = 11f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    val paintMetadata = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        isAntiAlias = true
    }
    val paintBoxFill = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#F8F9FA")
        style = android.graphics.Paint.Style.FILL
    }
    val paintBorder = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#E9ECEF")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1f
    }
    val paintFooter = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
        isAntiAlias = true
    }

    var currentY = 140f

    // Draw Report Main Title
    drawArabicText(canvas, context.getString(R.string.habayeb_pdf_title), 35f, currentY, 525, paintTitle, android.text.Layout.Alignment.ALIGN_CENTER)
    currentY += 28f

    // Metadata
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ENGLISH)
    val dateString = format.format(java.util.Date())
    
    val nameText = context.getString(R.string.habayeb_pdf_client_name, customer.name)
    drawArabicText(canvas, nameText, 35f, currentY, 525, paintMetadata, android.text.Layout.Alignment.ALIGN_NORMAL)
    currentY += 14f
    
    val phoneText = context.getString(R.string.habayeb_pdf_phone, customer.phone.ifEmpty { context.getString(R.string.habayeb_no_phone) })
    drawArabicText(canvas, phoneText, 35f, currentY, 525, paintMetadata, android.text.Layout.Alignment.ALIGN_NORMAL)
    currentY += 14f
    
    val dateText = context.getString(R.string.habayeb_pdf_date, dateString)
    drawArabicText(canvas, dateText, 35f, currentY, 525, paintMetadata, android.text.Layout.Alignment.ALIGN_NORMAL)
    currentY += 22f

    // Net value header card
    canvas.drawRoundRect(35f, currentY - 10f, (pageWidth - 35).toFloat(), currentY + 26f, 8f, 8f, paintBoxFill)
    canvas.drawRoundRect(35f, currentY - 10f, (pageWidth - 35).toFloat(), currentY + 26f, 8f, 8f, paintBorder)

    val balanceLabel = if (netDebt > 0) {
        context.getString(R.string.habayeb_pdf_balance_owed_by)
    } else if (netDebt < 0) {
        context.getString(R.string.habayeb_pdf_balance_owed_to)
    } else {
        context.getString(R.string.habayeb_pdf_balance_balanced)
    }

    val formattedNetDebt = String.format(java.util.Locale.ENGLISH, "%,.2f", Math.abs(netDebt))
    val balanceVal = context.getString(R.string.habayeb_pdf_balance_val, formattedNetDebt)

    paintLabel.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    drawArabicText(canvas, balanceLabel, 50f, currentY + 2f, 495, paintLabel, android.text.Layout.Alignment.ALIGN_NORMAL)

    paintValue.color = if (netDebt >= 0) android.graphics.Color.parseColor("#10B981") else android.graphics.Color.parseColor("#EF4444")
    drawArabicText(canvas, balanceVal, 50f, currentY + 2f, 495, paintValue, android.text.Layout.Alignment.ALIGN_OPPOSITE)

    currentY += 45f

    // Transactions list header
    drawArabicText(canvas, context.getString(R.string.habayeb_pdf_history_title), 35f, currentY, 525, paintSectionHeader, android.text.Layout.Alignment.ALIGN_NORMAL)
    currentY += 22f

    // Loop transactions
    for (tx in transactions) {
        if (currentY > pageHeight - 65f) break

        canvas.drawRoundRect(35f, currentY - 8f, (pageWidth - 35).toFloat(), currentY + 18f, 6f, 6f, paintBoxFill)
        canvas.drawRoundRect(35f, currentY - 8f, (pageWidth - 35).toFloat(), currentY + 18f, 6f, 6f, paintBorder)

        val txTypeStr = when (tx.type) {
            "OWED_BY_THEM" -> context.getString(R.string.habayeb_pdf_tx_owed_by)
            "PAYMENT_BY_THEM" -> context.getString(R.string.habayeb_pdf_tx_payment_by)
            "OWED_TO_THEM" -> context.getString(R.string.habayeb_pdf_tx_owed_to)
            "PAYMENT_TO_THEM" -> context.getString(R.string.habayeb_pdf_tx_payment_to)
            else -> context.getString(R.string.habayeb_pdf_tx_generic)
        }

        val formattedDate = try {
            val sDate = java.util.Date(tx.timestamp * 1000)
            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.ENGLISH)
            sdf.format(sDate)
        } catch(e: Exception) {
            ""
        }

        val txLabel = context.getString(R.string.habayeb_pdf_tx_format, txTypeStr, tx.description, formattedDate)
        val formattedAmount = String.format(java.util.Locale.ENGLISH, "%,.2f", tx.amount)
        val txValue = context.getString(R.string.habayeb_pdf_val_format, formattedAmount)

        paintLabel.textSize = 10f
        paintLabel.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        drawArabicText(canvas, txLabel, 50f, currentY, 495, paintLabel, android.text.Layout.Alignment.ALIGN_NORMAL)

        paintValue.textSize = 10f
        paintValue.color = android.graphics.Color.parseColor("#34495E")
        drawArabicText(canvas, txValue, 50f, currentY, 495, paintValue, android.text.Layout.Alignment.ALIGN_OPPOSITE)

        currentY += 34f
    }

    // Footer
    drawArabicText(canvas, context.getString(R.string.habayeb_pdf_footer), 35f, (pageHeight - 35).toFloat(), 525, paintFooter, android.text.Layout.Alignment.ALIGN_CENTER)

    pdfDocument.finishPage(page)

    // Save and Share or View
    val fileName = "habayeb_${customer.name}_${System.currentTimeMillis() % 100000}.pdf"
    val file = java.io.File(context.cacheDir, fileName)
    try {
        pdfDocument.writeTo(java.io.FileOutputStream(file))
        pdfDocument.close()

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        if (action == "SHARE") {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.pdf_chooser_title)))
        } else {
            // VIEW/OPEN action
            val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, context.getString(R.string.habayeb_toast_pdf_export_failed, e.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
        pdfDocument.close()
    }
}

private fun String.toEnglishDigits(): String {
    var result = this
    val arabicIndicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    val westernDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    for (i in 0..9) {
        result = result.replace(arabicIndicDigits[i], westernDigits[i])
    }
    return result
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CustomerItemRow(
    customer: com.example.ui.state.CustomerUiState,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    activeThemeColor: Color,
    activeSubColor: Color,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onCustomerClick: () -> Unit,
    onCustomerLongClick: () -> Unit,
    onQuickAdd: () -> Unit
) {
    val lastTxTime = customer.lastTransactionTimestamp
    val sdf = remember { java.text.SimpleDateFormat("yyyy/MM/dd hh:mm a", java.util.Locale.ENGLISH) }
    val formattedDate = remember(lastTxTime) {
        val formatted = sdf.format(java.util.Date(lastTxTime * 1000))
        formatted.replace("AM", "ص").replace("PM", "م")
                 .replace("am", "ص").replace("pm", "م")
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) activeThemeColor.copy(alpha = 0.5f) else Color(0xFFF1F5F9)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) activeSubColor.copy(alpha = 0.3f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 200))
            .combinedClickable(
                onClick = onCustomerClick,
                onLongClick = onCustomerLongClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val avatarBgColor = activeThemeColor.copy(alpha = 0.08f)
                    val avatarIconColor = activeThemeColor

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(avatarBgColor)
                            .clickable { onQuickAdd() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "إضافة سريعة",
                            tint = avatarIconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.Start) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "محدد",
                                    tint = SoftLavender,
                                    modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                )
                            }
                            Text(
                                text = customer.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkPurpleText
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "آخر تعديل: $formattedDate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = HabayebTextSecondary
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    val netDebt = customer.netDebt
                    if (netDebt > 0.0) {
                        AutoScaleText(
                            text = formatYemeniRial(netDebt),
                            baseFontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "مدين (عليه)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = HabayebTextSecondary
                        )
                    } else if (netDebt < 0.0) {
                        AutoScaleText(
                            text = formatYemeniRial(netDebt),
                            baseFontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "دائن (له)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = HabayebTextSecondary
                        )
                    } else {
                        Text(
                            text = "خالص",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HabayebTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "متزن",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = HabayebTextSecondary
                        )
                    }
                }
            }

            // Smooth, lightweight details expansion
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 200)) + shrinkVertically(animationSpec = tween(durationMillis = 200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.material3.HorizontalDivider(
                        color = Color(0xFFF1F5F9),
                        thickness = 1.dp
                    )
                    if (customer.phone.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "الهاتف",
                                tint = activeThemeColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = customer.phone,
                                fontSize = 12.sp,
                                color = DarkPurpleText
                            )
                        }
                    }
                    if (customer.notes.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "ملاحظات",
                                tint = activeThemeColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = customer.notes,
                                fontSize = 12.sp,
                                color = DarkPurpleText
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "إجمالي العمليات",
                            tint = activeThemeColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "إجمالي العمليات: ${customer.totalTransactions}",
                            fontSize = 12.sp,
                            color = DarkPurpleText
                        )
                    }
                }
            }
        }
    }
}