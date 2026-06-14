package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Dialog
import com.example.data.local.HabayebCustomer
import com.example.data.local.HabayebTransaction
import com.example.ui.viewmodel.FinanceViewModel
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
    val formatter = java.text.DecimalFormat("#,##0")
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

@OptIn(ExperimentalFoundationApi::class)
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
    val customers by viewModel.habayebCustomersState.collectAsState()
    val transactions by viewModel.habayebTransactionsState.collectAsState()
    val totalOwedByThem by viewModel.habayebOwedByThemTotalState.collectAsState()
    val totalOwedToThem by viewModel.habayebOwedToThemTotalState.collectAsState()
    val currencySymbol = viewModel.settingsState.collectAsState().value.currencySymbol
    val isPrivacyModeState = viewModel.isPrivacyModeEnabled.collectAsState()

    // UI filters
    // 0 = الكل, 1 = لي عند الناس (المدينين), 2 = علي للناس (الدائنين)
    var selectedFilterTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Multi-Select state
    val selectedCustomerIds = remember { mutableStateListOf<String>() }
    var isMultiSelectActive by remember { mutableStateOf(false) }

    // Dialog sheets states
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var activeCustomerForHistory by remember { mutableStateOf<HabayebCustomer?>(null) }
    var showAddTransactionDialogForCustomer by remember { mutableStateOf<HabayebCustomer?>(null) }
    var defaultTransactionTypeForDialog by remember { mutableStateOf("OWED_BY_THEM") }
    var editingTransactionForDialog by remember { mutableStateOf<HabayebTransaction?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var financialSortMode by remember { mutableStateOf(0) }
    var historicalSortMode by remember { mutableStateOf(0) }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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

    // Filtered and Sorted Customers list
    val filteredCustomers = remember(customers, transactions, selectedFilterTab, searchQuery, financialSortMode, historicalSortMode) {
        val filtered = customers.filter { customer ->
            val matchesSearch = searchQuery.isEmpty() ||
                    customer.name.contains(searchQuery, ignoreCase = true) ||
                    customer.phone.contains(searchQuery, ignoreCase = true)
            if (!matchesSearch) return@filter false

            val custTxs = transactions.filter { it.customerId == customer.id }
            val owedByThem = custTxs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
            val paymentByThem = custTxs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
            val owedToThem = custTxs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
            val paymentToThem = custTxs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
            
            val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)
            
            when (selectedFilterTab) {
                1 -> netDebt > 0.0 // Debtors (لي عند الناس)
                2 -> netDebt < 0.0 // Creditors (علي للناس)
                else -> true
            }
        }

        if (financialSortMode != 0) {
            val netDebtMap = filtered.associateWith { customer ->
                val custTxs = transactions.filter { it.customerId == customer.id }
                val owedByThem = custTxs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
                val paymentByThem = custTxs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
                val owedToThem = custTxs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
                val paymentToThem = custTxs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
                kotlin.math.abs((owedByThem - paymentByThem) - (owedToThem - paymentToThem))
            }
            if (financialSortMode == 1) {
                filtered.sortedByDescending { netDebtMap[it] ?: 0.0 }
            } else {
                filtered.sortedBy { netDebtMap[it] ?: 0.0 }
            }
        } else if (historicalSortMode != 0) {
            val lastTxTimeMap = filtered.associateWith { customer ->
                val custTxs = transactions.filter { it.customerId == customer.id }
                custTxs.maxOfOrNull { it.timestamp } ?: customer.createdAt
            }
            if (historicalSortMode == 1) {
                filtered.sortedByDescending { lastTxTimeMap[it] ?: 0L }
            } else {
                filtered.sortedBy { lastTxTimeMap[it] ?: 0L }
            }
        } else {
            filtered
        }
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
                                text = if (isMultiSelectActive) "تم تحديد (${selectedCustomerIds.size})" else "حسابات العملاء الموردين",
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
                                     text = "مستحقاتي (لي)",
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
                                     text = "التزاماتي (علي)",
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
                                        text = "مستحقاتي (لي)",
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
                                        text = "التزاماتي (علي)",
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
                                    else -> "قائمة الحسابات فارغة.\nابدأ بإضافة أول جهة اتصال عبر زر الإضافة (+)"
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
                        
                        // Calculate specific customer net balance
                        val custTxs = transactions.filter { it.customerId == customer.id }
                        val owedByThem = custTxs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
                        val paymentByThem = custTxs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
                        val owedToThem = custTxs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
                        val paymentToThem = custTxs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
                        val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)

                        val lastTxTime = custTxs.maxOfOrNull { it.timestamp } ?: customer.createdAt
                        val sdf = remember { SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()) }
                        val formattedDate = remember(lastTxTime) {
                            val formatted = sdf.format(Date(lastTxTime * 1000))
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
                                .combinedClickable(
                                    onClick = {
                                        if (isMultiSelectActive) {
                                            if (isSelected) {
                                                selectedCustomerIds.remove(customer.id)
                                                if (selectedCustomerIds.isEmpty()) isMultiSelectActive = false
                                            } else {
                                                selectedCustomerIds.add(customer.id)
                                            }
                                        } else {
                                            activeCustomerForHistory = customer
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectActive = true
                                        if (!isSelected) selectedCustomerIds.add(customer.id)
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Right Side (Start of reading): Avatar & Name with timestamp metadata
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Initials circle (warm pastel avatar style)
                                    val avatarBgColor = when {
                                        netDebt > 0.0 -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                        netDebt < 0.0 -> Color(0xFF10B981).copy(alpha = 0.15f)
                                        else -> DeepLavender.copy(alpha = 0.5f)
                                    }
                                    val avatarTextColor = when {
                                        netDebt > 0.0 -> Color(0xFFEF4444)
                                        netDebt < 0.0 -> Color(0xFF10B981)
                                        else -> HabayebTextSecondary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(avatarBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = customer.name.firstOrNull()?.toString() ?: "",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = avatarTextColor
                                        )
                                    }

                                    // Name and date next to it (aligned right in column)
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

                                // Left Side (End of reading / Decision): Amount & Status
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    if (netDebt > 0.0) {
                                        AutoScaleText(
                                            text = if (isPrivacyModeState.value) "*****" else formatYemeniRial(netDebt),
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
                                            text = if (isPrivacyModeState.value) "*****" else formatYemeniRial(netDebt),
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
                        }
                    }
                }
            }
        }

        if (!isMultiSelectActive && activeCustomerForHistory == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
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

        // 4. MULTI-DELETE CONFIRMATION DIALOG
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("تأكيد الحذف الجماعي الآمن", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = { Text("هل أنت متأكد من حذف الحسابات المختارة (${selectedCustomerIds.size}) مع جميع سجلاتها المالية نهائياً؟ لا يمكن التراجع عن هذا الإجراء.", fontSize = 13.sp, color = Color.Gray) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteMultipleHabayebCustomers(selectedCustomerIds.toList())
                            selectedCustomerIds.clear()
                            isMultiSelectActive = false
                            showDeleteConfirmDialog = false
                            Toast.makeText(context, "تم الحذف بنجاح 🗑️", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("نعم، حذف", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("إلغاء", color = Color.Gray)
                    }
                }
            )
        }

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
    var nameStr by remember { mutableStateOf("") }
    var phoneStr by remember { mutableStateOf("") }
    var notesStr by remember { mutableStateOf("") }
    
    // First atomic transaction fields
    var initialAmountStr by remember { mutableStateOf("") }
    var initialType by remember { mutableStateOf("OWED_BY_THEM") } // OWED_BY_THEM (عليه لي) or OWED_TO_THEM (له عندي)

    var showCalculator by remember { mutableStateOf(false) }
    var isSavingCustomer by remember { mutableStateOf(false) }

    // Date Picker state
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val dateStr = remember(selectedCalendar) {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
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
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        try {
            focusRequester.requestFocus()
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
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Snug vertical spacing
                ) {
                    Text(
                        text = "إضافة معاملة جديدة 👥",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = activeThemeColor
                    )

                    // 1. Name field with contacts trailingIcon
                    OutlinedTextField(
                        value = nameStr,
                        onValueChange = { nameStr = it },
                        label = { Text("اسم العميل", fontSize = 13.sp) },
                        placeholder = { Text("الاسم", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_CONTACTS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        contactPickerLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = "جهات الاتصال",
                                    tint = activeThemeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )

                    // Phone Number Field (So user can see and edit the fetched number)
                    OutlinedTextField(
                        value = phoneStr,
                        onValueChange = { phoneStr = it },
                        label = { Text("رقم الهاتف (اختياري)", fontSize = 13.sp) },
                        placeholder = { Text("مثال: 777123456", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "رقم الهاتف",
                                tint = activeThemeColor,
                                modifier = Modifier.size(20.dp).padding(end = 8.dp)
                            )
                        }
                    )

                    // 2. Amount field with calculator trailingIcon
                    OutlinedTextField(
                        value = initialAmountStr,
                        onValueChange = { initialAmountStr = it },
                        label = { Text("مبلغ المعاملة الأولى", fontSize = 13.sp) },
                        placeholder = { Text("0.0", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
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
                                    contentDescription = "الآلة الحاسبة",
                                    tint = activeThemeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )

                    // 3. Status switcher buttons: عليه (مدين) vs له (دائن)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(activeSubColor)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { initialType = "OWED_BY_THEM" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (initialType == "OWED_BY_THEM") Color(0xFFDCFCE7) else Color.Transparent,
                                contentColor = if (initialType == "OWED_BY_THEM") Color(0xFF15803D) else activeThemeColor
                            ),
                            elevation = null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("عليه (مدين) 🟢", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { initialType = "OWED_TO_THEM" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (initialType == "OWED_TO_THEM") Color(0xFFFEE2E2) else Color.Transparent,
                                contentColor = if (initialType == "OWED_TO_THEM") Color(0xFFB91C1C) else activeThemeColor
                            ),
                            elevation = null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("له (دائن) 🔴", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                contentDescription = "تاريخ المعاملة",
                                tint = activeThemeColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "تاريخ المعاملة:",
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

                    // 5. Details / Notes field with storage trailingIcon
                    OutlinedTextField(
                        value = notesStr,
                        onValueChange = { notesStr = it },
                        label = { Text("التفاصيل / البيان (اختياري)", fontSize = 13.sp) },
                        placeholder = { Text("مثال: حساب الغداء، قيمة الخضار", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    // Depending on Android version, READ_EXTERNAL_STORAGE orREAD_MEDIA_IMAGES is needed.
                                    // For simplicity and to ensure it enters storage immediately without complex runtime checks for media, 
                                    // we can just launch GetContent directly which is safer in modern Android.
                                    try {
                                        storageLauncher.launch("image/*")
                                    } catch (e: Exception) {
                                        val hasStoragePermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasStoragePermission) {
                                            storageLauncher.launch("image/*")
                                        } else {
                                            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "إرفاق مستند",
                                    tint = activeThemeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )

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
                            Text("إلغاء", color = Color.Gray)
                        }

                        Button(
                            enabled = !isSavingCustomer,
                            onClick = {
                                if (isSavingCustomer) return@Button
                                isSavingCustomer = true

                                if (nameStr.isBlank()) {
                                    Toast.makeText(context, "الرجاء كتابة الاسم", Toast.LENGTH_SHORT).show()
                                    isSavingCustomer = false
                                    return@Button
                                }
                                val initialAmount = initialAmountStr.toDoubleOrNull() ?: 0.0
                                if (initialAmount <= 0.0) {
                                    Toast.makeText(context, "يجب تحديد أول مبلغ للمعاملة (أكبر من 0)", Toast.LENGTH_SHORT).show()
                                    isSavingCustomer = false
                                    return@Button
                                }

                                val transactionTimestamp = selectedCalendar.timeInMillis / 1000

                                val newCustomer = HabayebCustomer(
                                    id = "cust_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                                    name = nameStr.trim(),
                                    phone = phoneStr.trim(),
                                    notes = notesStr.trim(),
                                    createdAt = transactionTimestamp
                                )
                                viewModel.saveHabayebCustomer(newCustomer, initialAmount, initialType, transactionTimestamp)
                                Toast.makeText(context, "تم حفظ المعاملة بنجاح 👥", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("تأكيد وحفظ 💾")
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
    val customers by viewModel.habayebCustomersState.collectAsState()
    val activeCustomer = customers.find { it.id == customer.id } ?: customer

    val transactions by viewModel.habayebTransactionsState.collectAsState()
    val currencySymbol = viewModel.settingsState.collectAsState().value.currencySymbol
    val isPrivacyModeState = viewModel.isPrivacyModeEnabled.collectAsState()
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
                    AlertDialog(
                        onDismissRequest = { showEditNameDialog = false },
                        title = {
                            Text("تعديل اسم الزبون", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            OutlinedTextField(
                                value = editedNameStr,
                                onValueChange = { editedNameStr = it },
                                label = { Text("الاسم") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeThemeColor,
                                    focusedLabelColor = activeThemeColor,
                                    cursorColor = activeThemeColor
                                )
                            )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                val shareText = "كشف حساب: ${activeCustomer.name}\nالرصيد الصافي الحالي: ${formatYemeniRial(kotlin.math.abs(netDebt))}\n" +
                                        customerTxs.joinToString("\n") { tx ->
                                            val typeStr = when (tx.type) {
                                                "OWED_BY_THEM" -> "دين عليه"
                                                "PAYMENT_BY_THEM" -> "سداد مقبوض منه"
                                                "OWED_TO_THEM" -> "دين له"
                                                "PAYMENT_TO_THEM" -> "سداد مدفوع له"
                                                else -> ""
                                            }
                                            "$typeStr: ${formatYemeniRial(tx.amount)} - ${tx.description}"
                                        }
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "مشاركة كشف حساب"))
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFEEF2F6), CircleShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = activeThemeColor, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                Toast.makeText(context, "جاري طباعة الكشف الحالي... 📄", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFEEF2F6), CircleShape)
                        ) {
                            Text("📄", fontSize = 16.sp)
                        }
                    }

                    Text(
                        text = "سجل المعاملات السابقة",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4B5563)
                    )
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
                        items(customerTxs) { tx ->
                            val formattedDate = remember(tx.timestamp) {
                                val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
                                val formatted = sdf.format(Date(tx.timestamp * 1000))
                                formatted.replace("AM", "ص").replace("PM", "م")
                                    .replace("am", "ص").replace("pm", "م")
                            }

                            val isOurs = tx.type == "OWED_BY_THEM" || tx.type == "PAYMENT_BY_THEM"
                            val cardBg = if (isOurs) Color(0xFFFFF1F2) else Color(0xFFF0FDF4)
                            val trendColor = if (isOurs) Color(0xFFEF5350) else Color(0xFF66BB6A)
                            val isDebt = tx.type == "OWED_BY_THEM" || tx.type == "OWED_TO_THEM"
                            val sign = if (isDebt) "+" else "-"

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: edit/delete actions
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteHabayebTransaction(tx.id)
                                                Toast.makeText(context, "تم حذف المعاملة", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, "حذف", tint = activeThemeColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                editingTransactionForDialog = tx
                                                defaultTransactionTypeFromHistory = tx.type
                                                showAddTransactionDialogFromHistory = customer
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, "تعديل", tint = activeThemeColor, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Center: amount and info
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "$sign${if (isPrivacyModeState.value) "*****" else formatYemeniRial(tx.amount)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isOurs) Color(0xFFBE123C) else Color(0xFF047857),
                                            modifier = Modifier.padding(start = 6.dp)
                                        )

                                        Spacer(modifier = Modifier.weight(1f))

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            val readableType = when (tx.type) {
                                                "OWED_BY_THEM" -> "دين عليه"
                                                "PAYMENT_BY_THEM" -> "سداد مقبوض منه"
                                                "OWED_TO_THEM" -> "دين له"
                                                "PAYMENT_TO_THEM" -> "سداد مدفوع له"
                                                else -> "معاملة"
                                            }
                                            Text(
                                                text = readableType,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF374151)
                                            )
                                            if (tx.description.isNotEmpty()) {
                                                Text(
                                                    text = tx.description,
                                                    fontSize = 11.sp,
                                                    color = Color.DarkGray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = formattedDate,
                                                fontSize = 8.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    // Right: Trend bubble
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(trendColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isDebt) "▲" else "▼",
                                            color = trendColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
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
    val currencySymbol = viewModel.settingsState.collectAsState().value.currencySymbol

    var amountStr by remember { mutableStateOf(editingTransaction?.amount?.toInt()?.toString() ?: "") }
    var descStr by remember { mutableStateOf(editingTransaction?.description ?: "") }
    var selectedType by remember { mutableStateOf(editingTransaction?.type ?: initialSelectedType) }
    
    val amountFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            amountFocusRequester.requestFocus()
        } catch(e: Exception) {}
    }

    val isLendMode = selectedType == "OWED_BY_THEM" || selectedType == "PAYMENT_BY_THEM"
    var isLendOperationSelected by remember { mutableStateOf(isLendMode) }
    var dateMillis by remember { mutableStateOf(editingTransaction?.timestamp?.let { it * 1000 } ?: System.currentTimeMillis()) }
    var showCalculator by remember { mutableStateOf(false) }
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
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                        horizontalArrangement = Arrangement.SpaceBetween,
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
                                text = customer.name.firstOrNull()?.toString() ?: "أ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "الحساب المستهدف: ${customer.name}",
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
                            text = "إضافة ما لنا",
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
                            text = "إضافة ما علينا",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isLendOperationSelected) Color.White else Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Secondary Sub-Selector
                val isNegativeAct = selectedType == "OWED_BY_THEM" || selectedType == "OWED_TO_THEM"
                val isPositiveAct = selectedType == "PAYMENT_BY_THEM" || selectedType == "PAYMENT_TO_THEM"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(activeSubColor)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Right tab: OWED/DEBT
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isNegativeAct) (if (isLendOperationSelected) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)) else Color.Transparent)
                            .clickable {
                                selectedType = if (isLendOperationSelected) "OWED_BY_THEM" else "OWED_TO_THEM"
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLendOperationSelected) "دين جديد لنا" else "دين جديد علينا",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNegativeAct) (if (isLendOperationSelected) Color(0xFFB91C1C) else Color(0xFF047857)) else (if (isLendOperationSelected) Color(0xFFEF4444) else Color(0xFF10B981))
                        )
                    }

                    // Left tab: PAYMENT/REPAYMENT
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPositiveAct) (if (isLendOperationSelected) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)) else Color.Transparent)
                            .clickable {
                                selectedType = if (isLendOperationSelected) "PAYMENT_BY_THEM" else "PAYMENT_TO_THEM"
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLendOperationSelected) "سداد دفعة لنا" else "سداد دفعة علينا",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositiveAct) (if (isLendOperationSelected) Color(0xFFB91C1C) else Color(0xFF047857)) else (if (isLendOperationSelected) Color(0xFFEF4444) else Color(0xFF10B981))
                        )
                    }
                }

                val dynamicThemeColor = if (isLendOperationSelected) Color(0xFFEF4444) else Color(0xFF10B981)
                val dynamicSubColor = if (isLendOperationSelected) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)

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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

                // Date displays container
                val formattedSelectedDate = remember(dateMillis) {
                    val sdf = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.getDefault())
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
                )

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
                val keysRow1 = listOf("7", "8", "9", "÷")
                val keysRow2 = listOf("4", "5", "6", "×")
                val keysRow3 = listOf("1", "2", "3", "-")
                val keysRow4 = listOf(".", "0", "=", "+")

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(keysRow1, keysRow2, keysRow3, keysRow4).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { char ->
                                val buttonColor = when (char) {
                                    "÷", "×", "-", "+" -> Color(0xFF9333EA)
                                    "=" -> Color(0xFF059669)
                                    else -> Color.White.copy(alpha = 0.1f)
                                }
                                val textColor = when (char) {
                                    "=" -> Color.White
                                    else -> Color.White
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(buttonColor)
                                        .clickable {
                                            if (char == "=") {
                                                val finalRes = evaluateSimpleExpression(rawExpression)
                                                if (finalRes != null) {
                                                    rawExpression = finalRes.toInt().toString()
                                                }
                                            } else {
                                                rawExpression += char
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                        }
                    }

                    // Bottom Row: Erase Entire
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEF4444))
                                .clickable { rawExpression = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("مسح الكل C", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    if (rawExpression.isNotEmpty()) rawExpression = rawExpression.dropLast(1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("مسح ⌫", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
