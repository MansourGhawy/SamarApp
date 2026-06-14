package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.example.data.local.ProductEntity
import com.example.data.local.MakhzanTransactionEntity
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MakhzanScreen(
    viewModel: FinanceViewModel,
    onClose: () -> Unit
) {
    val settings by viewModel.settingsState.collectAsState()
    val productsList by viewModel.productsState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    // List scroll state
    val lazyListState = rememberLazyListState()

    // Search and filter state
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Barcode scanner trigger state
    var showBarcodeScanner by remember { mutableStateOf(false) }

    // Selection mode state
    var selectedProductIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedProductIds.isNotEmpty()
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }

    // Dialog trigger states
    var showAddDialog by remember { mutableStateOf(false) }
    var showCategoryReportDialog by remember { mutableStateOf(false) }
    var showRestockDialogForProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showSaleDialogForProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showEditDialogForProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showDetailSheetForProduct by remember { mutableStateOf<ProductEntity?>(null) }

    // Drive categories dynamically
    val categories = remember(productsList) {
        val list = productsList.map { it.category }.distinct().toMutableList()
        list.add(0, "الكل")
        list
    }

    // Filter products list based on search and category
    val filteredProducts = productsList.filter {
        val matchesSearch = it.name.contains(searchQuery, ignoreCase = true) || 
                            it.category.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "الكل" || it.category == selectedCategory
        matchesSearch && matchesCategory
    }

    // BI Computations
    val totalCapital = remember(productsList, selectedCategory) {
        val list = if (selectedCategory == "الكل") productsList else productsList.filter { it.category == selectedCategory }
        list.sumOf { it.purchasePrice * it.quantity }
    }

    val totalExpectedSell = remember(productsList, selectedCategory) {
        val list = if (selectedCategory == "الكل") productsList else productsList.filter { it.category == selectedCategory }
        list.sumOf { it.sellingPrice * it.quantity }
    }

    val totalExpectedProfit = remember(totalCapital, totalExpectedSell) {
        (totalExpectedSell - totalCapital).coerceAtLeast(0.0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC), // Faint, clean background light mode
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF1B3B6F),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, "إضافة منتج") },
                text = { Text("منتج جديد", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }
    ) { paddingValues ->
        val density = androidx.compose.ui.platform.LocalDensity.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val expandedHeaderHeight = configuration.screenHeightDp.dp * 0.20f
        val collapsedHeaderHeight = 56.dp
        val maxScrollPx = with(density) { (expandedHeaderHeight - collapsedHeaderHeight).toPx() }
        var headerOffsetHeightPx by remember { mutableStateOf(0f) }

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val delta = available.y
                    val newOffset = headerOffsetHeightPx + delta
                    headerOffsetHeightPx = newOffset.coerceIn(-maxScrollPx, 0f)
                    return Offset.Zero
                }
            }
        }

        val collapseProgress = (headerOffsetHeightPx + maxScrollPx) / maxScrollPx

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .background(Color(0xFFF8FAFC))
                .nestedScroll(nestedScrollConnection)
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = expandedHeaderHeight + 8.dp, bottom = 88.dp)
            ) {
                // 1. DUMMY PLACEHOLDER (Replaced by floating Collapsing header below)
                item {
                    Spacer(modifier = Modifier.height(1.dp))
                }

                // 2. Search & Scan Bar UI Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            shadowElevation = 0.5.dp,
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "ابحث عن منتج، فئة...",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF334155)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            "إغلاق",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                // Integrated inline barcode scan button inside the search field
                                IconButton(
                                    onClick = { showBarcodeScanner = true },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "ماسح الباركود",
                                        tint = Color(0xFF1B3B6F),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Horizontal LazyRow Category Pill Filters
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Report Button in the same micro chips style!
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFDCFCE7),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier
                                    .height(28.dp)
                                    .clickable { showCategoryReportDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assessment,
                                        contentDescription = "تقرير الجرد والارباح",
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "تقرير القسم 📊",
                                        color = Color(0xFF15803D),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        items(categories) { cat ->
                            val isSelected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0xFF1B3B6F) else Color(0xFFE2E8F0))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // 4. Products List or Empty View State
                if (filteredProducts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 90.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = "لا توجد منتجات",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (productsList.isEmpty()) "المستودع فارغ حالياً" else "لا توجد نتائج مطابقة لبحثك",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    items(filteredProducts) { item ->
                        val isSelected = selectedProductIds.contains(item.id)
                        ProductItemCard(
                            product = item,
                            currencySymbol = settings.currencySymbol,
                            viewModel = viewModel,
                            isSelected = isSelected,
                            onRestock = { if (!isSelectionMode) showRestockDialogForProduct = item },
                            onSale = { if (!isSelectionMode) showSaleDialogForProduct = item },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedProductIds = if (isSelected) selectedProductIds - item.id else selectedProductIds + item.id
                                } else {
                                    showDetailSheetForProduct = item
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedProductIds = setOf(item.id)
                                }
                            }
                        )
                    }
                }
            }

            // Floating Collapsible Header!
            val headerHeight = expandedHeaderHeight + with(density) { headerOffsetHeightPx.toDp() }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3B6F)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title Bar (Row of 56.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode) {
                            IconButton(onClick = { selectedProductIds = emptySet() }) {
                                Icon(Icons.Default.Close, "إلغاء التحديد", tint = Color.White)
                            }
                            Text(
                                text = "${selectedProductIds.size} محدد",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                            )
                            IconButton(onClick = { 
                                if (selectedProductIds.size == filteredProducts.size) {
                                    selectedProductIds = emptySet()
                                } else {
                                    selectedProductIds = filteredProducts.map { it.id }.toSet()
                                }
                            }) {
                                Icon(
                                    if (selectedProductIds.size == filteredProducts.size) Icons.Default.CheckBox else Icons.Default.SelectAll,
                                    "تحديد الكل",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { showMultiDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, "حذف المحدد", tint = Color.White)
                            }
                        } else {
                            // Left: Qr/Barcode button
                            IconButton(
                                onClick = { showBarcodeScanner = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "قراءة باركود",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Middle: Description & Header Text
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "المخزن الذكي",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                if (collapseProgress > 0.4f) {
                                    Text(
                                        text = "إدارة المخزون والعمليات",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.75f * collapseProgress)
                                    )
                                }
                            }

                            // Right: Close/Back button
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Collapsible Stats Row
                    if (collapseProgress > 0.05f) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .alpha(collapseProgress),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (selectedCategory == "الكل") "رأس المال الكلي للقسم" else "رأس مال ($selectedCategory)",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
                                    IconButton(
                                        onClick = { viewModel.togglePrivacyMode() },
                                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "عرض المبالغ",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                    Text(
                                        text = if (isPrivacyMode) "*****" else viewModel.formatDoubleCurrency(totalCapital, settings.currencySymbol),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "هامش الأرباح المتوقعة",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                val isPrivacyMode by viewModel.isPrivacyModeEnabled.collectAsState()
                                Text(
                                    text = if (isPrivacyMode) "*****" else "+ " + viewModel.formatDoubleCurrency(totalExpectedProfit, settings.currencySymbol),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF86EFAC)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Interactive Complete Dialogs ---

    // Barcode Scanner Simulator Dialog
    if (showBarcodeScanner) {
        BarcodeScannerDialog(
            products = productsList,
            onScanMatched = { matchedProduct ->
                showBarcodeScanner = false
                showDetailSheetForProduct = matchedProduct
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    // Add Product Dialog
    if (showAddDialog) {
        AddProductDialog(
            currencySymbol = settings.currencySymbol,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, category, purchasePr, sellingPr, initQty, unitType, alertThreshold, imageUrl, note, hasSubUnits, parentUnitName, subUnitName, subUnitCountPerParent ->
                viewModel.saveProduct(
                    name = name,
                    category = category,
                    purchasePrice = purchasePr,
                    sellingPrice = sellingPr,
                    quantity = initQty,
                    unitType = unitType,
                    lowStockThreshold = alertThreshold,
                    imageUrl = imageUrl,
                    note = note,
                    hasSubUnits = hasSubUnits,
                    parentUnitName = parentUnitName,
                    subUnitName = subUnitName,
                    subUnitCountPerParent = subUnitCountPerParent
                )
                showAddDialog = false
            }
        )
    }

    // Category Performance Report Dialog
    if (showCategoryReportDialog) {
        val catProducts = remember(productsList, selectedCategory) {
            productsList.filter { selectedCategory == "الكل" || it.category == selectedCategory }
        }
        val context = androidx.compose.ui.platform.LocalContext.current

        AlertDialog(
            onDismissRequest = { showCategoryReportDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val reportText = buildString {
                            append("📊 *تقرير المخزون لقسم ($selectedCategory)* 📊\n\n")
                            append("📂 *الإحصائيات الكلية للمستودع:* \n")
                            append("• إجمالي رأس المال: ${viewModel.formatDoubleCurrency(totalCapital, settings.currencySymbol)}\n")
                            append("• الأرباح المتوقعة عند البيع: +${viewModel.formatDoubleCurrency(totalExpectedProfit, settings.currencySymbol)}\n")
                            append("• إجمالي عدد المنتجات: ${catProducts.size} منتج\n")
                            append("• منتجات منخفضة المخزون: ${catProducts.count { it.quantity <= it.lowStockThreshold }} صنف حرج ⚠️\n\n")
                            append("📈 *تفاصيل المخزون:* \n")
                            catProducts.forEach { prod ->
                                val status = if (prod.quantity <= prod.lowStockThreshold) "⚠️ منخفض" else "✅ متوفر"
                                append("- *${prod.name}* ($status)\n")
                                append("  الكمية المتاحة: ${prod.quantity} ${prod.unitType}\n")
                                append("  رأس المال: ${viewModel.formatDoubleCurrency(prod.purchasePrice * prod.quantity, settings.currencySymbol)}\n")
                                append("  الربح المتوقع: +${viewModel.formatDoubleCurrency((prod.sellingPrice - prod.purchasePrice) * prod.quantity, settings.currencySymbol)}\n")
                            }
                            append("\n🔗 *تم الإنشاء بواسطة نظام ميزان الدار* 📱")
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة تقرير المخزون"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Share, "مشاركة", tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("مشاركة عبر واتساب 💬", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryReportDialog = false }) {
                    Text("إغلاق", color = Color(0xFF475569), fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    text = "تقرير أداء القسم ($selectedCategory)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("رأس مال القسم الحالي:", fontSize = 12.sp, color = Color(0xFF166534))
                                Text(
                                    text = viewModel.formatDoubleCurrency(totalCapital, settings.currencySymbol),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("هامش أرباح محققة متوقعة:", fontSize = 12.sp, color = Color(0xFF166534))
                                Text(
                                    text = viewModel.formatDoubleCurrency(totalExpectedProfit, settings.currencySymbol),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("الأصناف شحيحة المخزون:", fontSize = 12.sp, color = Color(0xFF991B1B))
                                Text(
                                    text = "${catProducts.count { it.quantity <= it.lowStockThreshold }} صنف حرج",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        }
                    }

                    Text(
                        text = "منتجات القسم:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )

                    catProducts.forEach { prod ->
                        val isCritical = prod.quantity <= prod.lowStockThreshold
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                Text(
                                    text = "متاح: ${prod.quantity} ${prod.unitType}",
                                    fontSize = 10.sp,
                                    color = if (isCritical) Color(0xFFEF4444) else Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                 val expectedProfitSingle = (prod.sellingPrice - prod.purchasePrice) * prod.quantity
                                Box(modifier = Modifier) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "+ " + viewModel.formatDoubleCurrency(expectedProfitSingle, settings.currencySymbol),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A)
                                        )
                                        Text(
                                            text = "رأس مال: " + viewModel.formatDoubleCurrency(prod.purchasePrice * prod.quantity, settings.currencySymbol),
                                            fontSize = 9.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    // Restock Dialog (الوارد)
    showRestockDialogForProduct?.let { product ->
        RestockDialog(
            product = product,
            onDismiss = { showRestockDialogForProduct = null },
            onConfirm = { qty, note ->
                viewModel.addProductStock(product, qty, note)
                showRestockDialogForProduct = null
            }
        )
    }

    // Register Sale Dialog (الصادر)
    showSaleDialogForProduct?.let { product ->
        RegisterSaleDialog(
            product = product,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showSaleDialogForProduct = null },
            onConfirm = { qty, syncToMizan, note ->
                viewModel.registerProductSale(product, qty, syncToMizan, note)
                showSaleDialogForProduct = null
            }
        )
    }

    // Edit Product Dialog
    showEditDialogForProduct?.let { product ->
        EditProductDialog(
            product = product,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showEditDialogForProduct = null },
            onConfirm = { updated ->
                viewModel.updateProduct(updated)
                showEditDialogForProduct = null
            }
        )
    }

    // Product Detail Timeline Bottom Sheet
    showDetailSheetForProduct?.let { product ->
        ProductDetailSheet(
            product = product,
            viewModel = viewModel,
            onDismiss = { showDetailSheetForProduct = null },
            onEdit = { 
                showDetailSheetForProduct = null
                showEditDialogForProduct = product 
            },
            onDelete = {
                viewModel.deleteProduct(product)
                showDetailSheetForProduct = null
            },
            onRestock = {
                showDetailSheetForProduct = null
                showRestockDialogForProduct = product
            },
            onSale = {
                showDetailSheetForProduct = null
                showSaleDialogForProduct = product
            }
        )
    }

    if (showMultiDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirm = false },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = productsList.filter { selectedProductIds.contains(it.id) }
                        toDelete.forEach { viewModel.deleteProduct(it) }
                        selectedProductIds = emptySet()
                        showMultiDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حذف ${selectedProductIds.size} منتجات", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirm = false }) {
                    Text("إلغاء", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            },
            title = { Text("تأكيد الحذف المتعدد 🗑️", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("هل أنت متأكد من نقل المنتجات المحددة (${selectedProductIds.size}) إلى سلة المحذوفات؟") },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

fun formatProductStock(product: ProductEntity): String {
    if (product.unitType == "كيلو") {
        val qty = product.quantity
        val qtyClean = if (qty % 1 == 0.0) qty.toInt().toString() else "%.3f".format(qty).trimEnd('0').trimEnd('.')
        return "$qtyClean كجم"
    } else {
        if (product.hasSubUnits && product.subUnitCountPerParent > 1.0) {
            val parentCount = (product.quantity / product.subUnitCountPerParent).toInt()
            val childCount = (product.quantity % product.subUnitCountPerParent).toInt()
            return when {
                parentCount > 0 && childCount > 0 -> "$parentCount ${product.parentUnitName} و $childCount ${product.subUnitName}"
                parentCount > 0 -> "$parentCount ${product.parentUnitName}"
                else -> "$childCount ${product.subUnitName}"
            }
        } else {
            val qtyClean = if (product.quantity % 1 == 0.0) product.quantity.toInt().toString() else product.quantity.toString()
            return "$qtyClean ${product.unitType}"
        }
    }
}

fun getProductEmoji(product: ProductEntity): String {
    val imageUrl = product.imageUrl
    if (!imageUrl.isNullOrBlank()) {
        return imageUrl
    }
    val name = product.name.lowercase()
    val category = product.category.lowercase()
    return when {
        category.contains("شراب") || category.contains("عصير") || category.contains("مشروب") || category.contains("ماء") || name.contains("ماء") || name.contains("عصير") || name.contains("بيبسي") || name.contains("بارد") -> "🥤"
        category.contains("أكل") || category.contains("طعام") || category.contains("غذاء") || category.contains("بسكويت") || category.contains("شيبس") || category.contains("مأكول") || name.contains("بسكويت") || name.contains("شيبس") || name.contains("دقيق") || name.contains("خبز") || name.contains("أرز") || name.contains("سكر") || name.contains("رز") -> "🥯"
        category.contains("جوار") || category.contains("إلكترون") || category.contains("هاتف") || category.contains("سلك") || category.contains("شاحن") || name.contains("تلفون") || name.contains("جوال") || name.contains("شاحن") || name.contains("سماعة") || name.contains("شاشة") || name.contains("جهاز") || category.contains("تقني") -> "💻"
        category.contains("لبس") || category.contains("ملابس") || category.contains("أثواب") || name.contains("ثوب") || name.contains("شميز") || name.contains("فستان") || name.contains("كوت") || name.contains("ملابس") -> "👕"
        category.contains("نظاف") || category.contains("غسيل") || name.contains("صابون") || name.contains("شامبو") || name.contains("كلور") || name.contains("مطهر") -> "🧼"
        category.contains("صيدلي") || category.contains("دواء") || category.contains("علاج") || name.contains("بندول") || name.contains("فيتامين") || name.contains("دواء") -> "💊"
        category.contains("مكتب") || category.contains("قلم") || category.contains("دفتر") || category.contains("قرطاس") || name.contains("قلم") || name.contains("دفتر") || name.contains("كتاب") -> "✏️"
        category.contains("عطور") || category.contains("بخور") || category.contains("تجميل") || name.contains("عطر") || name.contains("بخور") || name.contains("مكياج") -> "✨"
        else -> "📦"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductItemCard(
    product: ProductEntity,
    currencySymbol: String,
    viewModel: FinanceViewModel,
    isSelected: Boolean = false,
    onRestock: () -> Unit,
    onSale: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isCritical = product.quantity <= product.lowStockThreshold

    // Beautiful Pastel colored badges
    val badgeBgColor = if (isSelected) Color(0xFF1B3B6F).copy(alpha = 0.1f) else if (isCritical) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)
    val badgeTextColor = if (isCritical) Color(0xFFEF4444) else Color(0xFF16A34A)
    val badgeTextLabel = if (isCritical) "⚠️ حرج" else "متوفر"

    val totalPurchase = product.purchasePrice * product.quantity
    val totalExpectedSales = product.sellingPrice * product.quantity
    val expectedProfit = totalExpectedSales - totalPurchase

    val borderColor = if (isSelected) Color(0xFF1B3B6F) else Color(0xFFF1F5F9)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFF1F5F9) else Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Right Section (Arabic RTL reads from right): Avatar + Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circular leading avatar with beautiful pastel color matching stock state
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCritical) Color(0xFFFFECEF) else Color(0xFFE8FDF0)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getProductEmoji(product),
                        fontSize = 20.sp
                    )
                }

                Column {
                    Text(
                        text = product.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = product.category,
                                fontSize = 9.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isCritical) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFEE2E2))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "⚠️ حد نفاد",
                                    fontSize = 9.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            // Left Section (Arabic RTL reads from left): Live stock levels & profits
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                // Qty Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBgColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = formatProductStock(product),
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun AddProductDialog(
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        category: String,
        purchasePrice: Double,
        sellingPrice: Double,
        initQty: Double,
        unitType: String,
        alertThreshold: Double,
        imageUrl: String,
        note: String,
        hasSubUnits: Boolean,
        parentUnitName: String,
        subUnitName: String,
        subUnitCountPerParent: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var purchasePriceInput by remember { mutableStateOf("") }
    var sellingPriceInput by remember { mutableStateOf("") }
    var initQtyInput by remember { mutableStateOf("") }
    var alertThresholdInput by remember { mutableStateOf("") }
    var unitType by remember { mutableStateOf("حبة") }
    var selectedEmoji by remember { mutableStateOf("📦") }
    var noteInput by remember { mutableStateOf("") }

    // Multi-unit fields
    var hasSubUnits by remember { mutableStateOf(false) }
    var parentUnitName by remember { mutableStateOf("كرتون") }
    var subUnitName by remember { mutableStateOf("حبة") }
    var subUnitCountPerParentInput by remember { mutableStateOf("12") }
    var parentQtyInput by remember { mutableStateOf("") }
    var childQtyInput by remember { mutableStateOf("") }

    // Weight helper
    var weightGramInput by remember { mutableStateOf("") }

    var errorMsg by remember { mutableStateOf("") }

    val popularEmojis = listOf("📦", "🥤", "🥯", "💻", "👕", "🔋", "🧼", "💊", "✏️", "✨", "🍎", "🥛", "🍗", "🔑", "👟", "📱")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "إضافة منتج جديد للمستودع",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1B3B6F)
                )
            }
        },
        containerColor = Color.White,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (errorMsg.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEE2E2))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = errorMsg,
                            color = Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // GROUP 1: General Product Information (Compact & Side-by-side)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "بيانات السلعة الأساسية",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("اسم المنتج", fontSize = 9.sp) },
                                textStyle = TextStyle(fontSize = 11.sp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1B3B6F),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.weight(1.2f).height(54.dp)
                            )

                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("التصنيف", fontSize = 9.sp) },
                                placeholder = { Text("عصيرات، إلخ", fontSize = 9.sp) },
                                textStyle = TextStyle(fontSize = 11.sp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1B3B6F),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.weight(0.8f).height(54.dp)
                            )
                        }
                    }
                }

                // GROUP 2: Financials & Pricing
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "التسعير المالي والربحية",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    OutlinedTextField(
                                        value = purchasePriceInput,
                                        onValueChange = { purchasePriceInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("سعر الشراء", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                        placeholder = { Text("0.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                        textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.End),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF16A34A),
                                            unfocusedBorderColor = Color(0xFFBBF7D0),
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(54.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    OutlinedTextField(
                                        value = sellingPriceInput,
                                        onValueChange = { sellingPriceInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("سعر البيع", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                        placeholder = { Text("0.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                        textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.End),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF16A34A),
                                            unfocusedBorderColor = Color(0xFFBBF7D0),
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(54.dp)
                                    )
                                }
                            }
                        }

                        // Live Profitability Calculations
                        val purchasePr = purchasePriceInput.toDoubleOrNull() ?: 0.0
                        val sellingPr = sellingPriceInput.toDoubleOrNull() ?: 0.0
                        val singleProfit = sellingPr - purchasePr
                        val profitPct = if (sellingPr > 0.0) ((singleProfit / sellingPr) * 100).toInt() else 0

                        val calcBgColor = when {
                            singleProfit > 0.0 -> Color(0xFFDCFCE7)
                            singleProfit < 0.0 -> Color(0xFFFEE2E2)
                            else -> Color(0xFFF1F5F9)
                        }
                        val calcTextColor = when {
                            singleProfit > 0.0 -> Color(0xFF15803D)
                            singleProfit < 0.0 -> Color(0xFFB91C1C)
                            else -> Color(0xFF475569)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(calcBgColor)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = when {
                                    singleProfit > 0.0 -> "صافي ربح الوحدة: +$singleProfit $currencySymbol (بنسبة $profitPct%)"
                                    singleProfit < 0.0 -> "انتبه: خسارة محققة للقطعة: $singleProfit $currencySymbol!"
                                    else -> "أدخل سعر الشراء وسعر البيع لحساب الأرباح تلقائياً"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = calcTextColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // GROUP 3: Inventory Stock & Alarms
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "رصيد المخزون ووحدات القياس",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )

                        // Custom Segmented Unit Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("حبة", "كيلو").forEach { type ->
                                val selected = unitType == type
                                val label = if (type == "حبة") "حبة / قطعة" else "كيلو جرام / وزن"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Color(0xFF0284C7) else Color.White)
                                        .border(
                                            1.dp,
                                            if (selected) Color(0xFF0284C7) else Color(0xFFE2E8F0),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { unitType = type }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else Color(0xFF475569),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (unitType == "حبة") {
                            // Smart multi-units switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE0F2FE))
                                    .clickable { hasSubUnits = !hasSubUnits }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hasSubUnits,
                                    onCheckedChange = { hasSubUnits = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0284C7)),
                                    modifier = Modifier.scale(0.8f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(
                                        text = "تفعيل التعبئة والوحدات المتعددة (كرتون / علبة) 🔗",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0369A1)
                                    )
                                    Text(
                                        text = "مثالي لتسجيل مخزون بالصناديق/الكراتين وفرزه تلقائياً بالحبات",
                                        fontSize = 7.5.sp,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }

                            if (hasSubUnits) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "مسميّات كرتون التعبئة والتوزيع",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0369A1)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = parentUnitName,
                                                onValueChange = { parentUnitName = it },
                                                label = { Text("الوحدة الكبرى (مثال: كرتون)", fontSize = 8.sp) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                                modifier = Modifier.weight(1.0f).height(54.dp)
                                            )
                                            OutlinedTextField(
                                                value = subUnitName,
                                                onValueChange = { subUnitName = it },
                                                label = { Text("وحدة التجزئة (مثال: حبة)", fontSize = 8.sp) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                                modifier = Modifier.weight(1.0f).height(54.dp)
                                            )
                                        }

                                        // Presets
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("كرتون" to "حبة", "صندوق" to "قطعة", "شد" to "بكت", "درزن" to "حبة").forEach { (p, c) ->
                                                val isMatch = parentUnitName == p
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isMatch) Color(0xFFE0F2FE) else Color(0xFFF1F5F9))
                                                        .border(1.dp, if (isMatch) Color(0xFF0284C7) else Color.Transparent, RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            parentUnitName = p
                                                            subUnitName = c
                                                        }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(text = "$p/$c", fontSize = 8.sp, color = Color(0xFF0369A1))
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = subUnitCountPerParentInput,
                                            onValueChange = { subUnitCountPerParentInput = it.filter { c -> c.isDigit() || c == '.' } },
                                            label = { Text("معامل الاحتواء (كم $subUnitName داخل الـ $parentUnitName الواحد؟)", fontSize = 8.sp) },
                                            textStyle = TextStyle(fontSize = 10.sp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                            modifier = Modifier.fillMaxWidth().height(54.dp)
                                        )

                                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)

                                        Text(
                                            text = "إدخال كمية التأسيس التلقائية:",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.DarkGray
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = parentQtyInput,
                                                onValueChange = { parentQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("عدد ($parentUnitName)", fontSize = 8.sp) },
                                                placeholder = { Text("0.0", fontSize = 8.sp) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                                modifier = Modifier.weight(1f).height(54.dp)
                                            )
                                            OutlinedTextField(
                                                value = childQtyInput,
                                                onValueChange = { childQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("عدد ($subUnitName) إضافي", fontSize = 8.sp) },
                                                placeholder = { Text("0.0", fontSize = 8.sp) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                                modifier = Modifier.weight(1f).height(54.dp)
                                            )
                                        }

                                        // Formula calculation in real time
                                        val capacity = subUnitCountPerParentInput.toDoubleOrNull() ?: 12.0
                                        val parentVal = parentQtyInput.toDoubleOrNull() ?: 0.0
                                        val childVal = childQtyInput.toDoubleOrNull() ?: 0.0
                                        val calculatedTotal = (parentVal * capacity) + childVal

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFF0FDF4))
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = "حسبة كرتونية ذكية: $parentVal $parentUnitName × $capacity $subUnitName = ${(parentVal * capacity).toInt()} $subUnitName + $childVal $subUnitName = ${calculatedTotal.toInt()} $subUnitName إجمالاً.",
                                                color = Color(0xFF15803D),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.5.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)

                                        // Low stock threshold inside the multi-unit setup
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = alertThresholdInput,
                                                onValueChange = { alertThresholdInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("حد التنبيه الحرج لنفاد السلعة", fontSize = 8.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("5.0", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF0284C7),
                                                    unfocusedBorderColor = Color(0xFFBAE6FD),
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Default simple حبة input (Side-by-Side: الكمية + التنبيه)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = initQtyInput,
                                                onValueChange = { initQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("الكمية الواردة ($unitType)", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("0.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFBAE6FD)),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(0.8f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = alertThresholdInput,
                                                onValueChange = { alertThresholdInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("حد التنبيه", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("5.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF0284C7),
                                                    unfocusedBorderColor = Color(0xFFBAE6FD),
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Weight System
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Default simple كيلو input (Side-by-Side: الكمية + التنبيه)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = initQtyInput,
                                                onValueChange = { initQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("الكمية الواردة (كجم)", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("0.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFBAE6FD)),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(0.8f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = alertThresholdInput,
                                                onValueChange = { alertThresholdInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("حد التنبيه (كجم)", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("5.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF0284C7),
                                                    unfocusedBorderColor = Color(0xFFBAE6FD),
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick weights loaders
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("ربع كجم (+0.25)" to 0.25, "نصف كجم (+0.50)" to 0.50, "1 كجم (+1.00)" to 1.00).forEach { (lbl, valDec) ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFE0F2FE))
                                                .clickable {
                                                    val curr = initQtyInput.toDoubleOrNull() ?: 0.0
                                                    initQtyInput = (curr + valDec).toString()
                                                }
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = lbl, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = weightGramInput,
                                        onValueChange = {
                                            val filtered = it.filter { c -> c.isDigit() }
                                            weightGramInput = filtered
                                            val gramVal = filtered.toDoubleOrNull() ?: 0.0
                                            initQtyInput = (gramVal / 1000.0).toString()
                                        },
                                        label = { Text("محوّل ومساعد الغرامات المباشرة (مثال: 750 جرام)", fontSize = 8.sp) },
                                        textStyle = TextStyle(fontSize = 10.sp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth().height(54.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // GROUP 4: Customs Notes/Details Input Field (Sleek Notes)
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("ملاحظات وتفاصيل إضافية", fontSize = 9.sp) },
                    textStyle = TextStyle(fontSize = 11.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1B3B6F),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val purchasePr = purchasePriceInput.toDoubleOrNull() ?: 0.0
                    val sellingPr = sellingPriceInput.toDoubleOrNull() ?: 0.0
                    val alertThreshold = alertThresholdInput.toDoubleOrNull() ?: 5.0

                    // Calculate intelligent quantity
                    var finalQty = 0.0
                    if (unitType == "حبة") {
                        if (hasSubUnits) {
                            val capacity = subUnitCountPerParentInput.toDoubleOrNull() ?: 12.0
                            val parentVal = parentQtyInput.toDoubleOrNull() ?: 0.0
                            val childVal = childQtyInput.toDoubleOrNull() ?: 0.0
                            finalQty = (parentVal * capacity) + childVal
                        } else {
                            finalQty = initQtyInput.toDoubleOrNull() ?: 0.0
                        }
                    } else {
                        finalQty = initQtyInput.toDoubleOrNull() ?: 0.0
                    }

                    val countPerParent = subUnitCountPerParentInput.toDoubleOrNull() ?: 1.0

                    if (name.isBlank() || category.isBlank()) {
                        errorMsg = "فضلاً قم بملء اسم المنتج وتصنيفه أولاً"
                        return@Button
                    }
                    onConfirm(
                        name.trim(),
                        category.trim(),
                        purchasePr,
                        sellingPr,
                        finalQty,
                        unitType,
                        alertThreshold,
                        selectedEmoji,
                        noteInput.trim(),
                        hasSubUnits,
                        parentUnitName.trim(),
                        subUnitName.trim(),
                        countPerParent
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B6F))
            ) {
                Text("إضافة وحفظ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

// Restock dialog (وارد)
@Composable
fun RestockDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: (addQty: Double, note: String) -> Unit
) {
    var qtyInput by remember { mutableStateOf("") }
    var parentQtyInput by remember { mutableStateOf("") }
    var childQtyInput by remember { mutableStateOf("") }
    var weightGramInput by remember { mutableStateOf("") }

    var noteInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    val unitSuffix = if (product.unitType == "كيلو") "كجم" else "حبة"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("زيادة رصيد مخزون (وارد)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B3B6F)) },
        containerColor = Color.White,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اسم المنتج: ${product.name}", fontSize = 12.sp, color = Color(0xFF1B3B6F), fontWeight = FontWeight.Bold)
                
                // Show currently available formatted stock level
                Text(
                    text = "الكمية المتوفرة حالياً: ${formatProductStock(product)}", 
                    fontSize = 11.sp, 
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color.Red, fontSize = 11.sp)
                }

                if (product.unitType == "حبة" && product.hasSubUnits) {
                    // Multi-unit Restocking
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "أدخل كمية التعبئة الواردة (${product.parentUnitName} / ${product.subUnitName})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = parentQtyInput,
                                    onValueChange = { parentQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("عدد (${product.parentUnitName})", fontSize = 9.sp) },
                                    placeholder = { Text("0.0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFBAE6FD)),
                                    modifier = Modifier.weight(1f).height(54.dp)
                                )
                                OutlinedTextField(
                                    value = childQtyInput,
                                    onValueChange = { childQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("عدد (${product.subUnitName})", fontSize = 9.sp) },
                                    placeholder = { Text("0.0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFBAE6FD)),
                                    modifier = Modifier.weight(1f).height(54.dp)
                                )
                            }

                            val capacity = product.subUnitCountPerParent
                            val parentVal = parentQtyInput.toDoubleOrNull() ?: 0.0
                            val childVal = childQtyInput.toDoubleOrNull() ?: 0.0
                            val calculatedTotal = (parentVal * capacity) + childVal

                            if (calculatedTotal > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8FDF0))
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "المخزون المضاف: $parentVal × $capacity + $childVal = ${calculatedTotal.toInt()} ${product.subUnitName} إجمالاً.",
                                        color = Color(0xFF15803D),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                } else if (product.unitType == "كيلو") {
                    // Weigh System Restocking
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = qtyInput,
                                onValueChange = { qtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("الكمية الواردة بالكيلوجرام (كجم)", fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                placeholder = { Text("0.0", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            )
                        }

                        // Quick helpers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("+1 كجم" to 1.0, "+5 كجم" to 5.0, "+10 كجم" to 10.0).forEach { (lbl, valDec) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable {
                                            val curr = qtyInput.toDoubleOrNull() ?: 0.0
                                            qtyInput = (curr + valDec).toString()
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = lbl, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = weightGramInput,
                            onValueChange = {
                                val filtered = it.filter { c -> c.isDigit() }
                                weightGramInput = filtered
                                val gramVal = filtered.toDoubleOrNull() ?: 0.0
                                qtyInput = (gramVal / 1000.0).toString()
                            },
                            label = { Text("مساعد الغرامات السريعة (مثال: 500 جرام)", fontSize = 9.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        )
                    }
                } else {
                    // Standard Simple Count Restocking
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("الكمية الجديدة (حبّة/وحدة)", fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                            placeholder = { Text("0.0", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("ملاحظات وتفاصيل إضافية", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1B3B6F),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var q = 0.0
                    if (product.unitType == "حبة" && product.hasSubUnits) {
                        val capacity = product.subUnitCountPerParent
                        val parentVal = parentQtyInput.toDoubleOrNull() ?: 0.0
                        val childVal = childQtyInput.toDoubleOrNull() ?: 0.0
                        q = (parentVal * capacity) + childVal
                    } else {
                        q = qtyInput.toDoubleOrNull() ?: 0.0
                    }

                    if (q <= 0) {
                        errorMsg = "يرجى كتابة كمية صحيحة أكبر من الصفر"
                        return@Button
                    }
                    onConfirm(q, noteInput.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B6F))
            ) {
                Text("تحديث الوارد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

// Register Sale Dialog (صادر)
@Composable
fun RegisterSaleDialog(
    product: ProductEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (qty: Double, syncToMizan: Boolean, note: String) -> Unit
) {
    var qtyInput by remember { mutableStateOf("1") }
    var parentQtyInput by remember { mutableStateOf("") }
    var childQtyInput by remember { mutableStateOf("") }
    var weightGramInput by remember { mutableStateOf("") }

    var noteInput by remember { mutableStateOf("") }
    var syncToMizan by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    // Resolve entered qty depending on unit and carton packaging
    val resolvedQty = if (product.unitType == "حبة" && product.hasSubUnits) {
        val capacity = product.subUnitCountPerParent
        val parentVal = parentQtyInput.toDoubleOrNull() ?: 0.0
        val childVal = childQtyInput.toDoubleOrNull() ?: 0.0
        (parentVal * capacity) + childVal
    } else {
        qtyInput.toDoubleOrNull() ?: 0.0
    }

    val calculatedRevenue = resolvedQty * product.sellingPrice
    val calculatedProfit = resolvedQty * (product.sellingPrice - product.purchasePrice)
    val unitSuffix = if (product.unitType == "كيلو") "كجم" else "حبة"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل حركة بيع (صادر)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B3B6F)) },
        containerColor = Color.White,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اسم المنتج: ${product.name}", fontSize = 12.sp, color = Color(0xFF1B3B6F), fontWeight = FontWeight.Bold)
                
                // Show currently available formatted stock level
                Text(
                    text = "الكمية المتوفرة حالياً: ${formatProductStock(product)}", 
                    fontSize = 11.sp, 
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color.Red, fontSize = 11.sp)
                }

                if (product.unitType == "حبة" && product.hasSubUnits) {
                    // Multi-unit Carton and piece sales
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "مبيعات التعبئة والتجزئة (${product.parentUnitName} / ${product.subUnitName})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = parentQtyInput,
                                    onValueChange = { parentQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("صادر (${product.parentUnitName})", fontSize = 9.sp) },
                                    placeholder = { Text("0.0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFEF4444), unfocusedBorderColor = Color(0xFFFECACA)),
                                    modifier = Modifier.weight(1f).height(54.dp)
                                )
                                OutlinedTextField(
                                    value = childQtyInput,
                                    onValueChange = { childQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("صادر (${product.subUnitName})", fontSize = 9.sp) },
                                    placeholder = { Text("0.0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFEF4444), unfocusedBorderColor = Color(0xFFFECACA)),
                                    modifier = Modifier.weight(1f).height(54.dp)
                                )
                            }

                            val capacity = product.subUnitCountPerParent
                            val parentVal = parentQtyInput.toDoubleOrNull() ?: 0.0
                            val childVal = childQtyInput.toDoubleOrNull() ?: 0.0
                            val calculatedTotal = (parentVal * capacity) + childVal

                            if (calculatedTotal > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFEF2F2))
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "إجمالي حبات التصدير: $parentVal × $capacity + $childVal = ${calculatedTotal.toInt()} ${product.subUnitName}.",
                                        color = Color(0xFF991B1B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                } else if (product.unitType == "كيلو") {
                    // Weigh System Sales
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = qtyInput,
                                onValueChange = { qtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("الكمية الصادرات (كجم)", fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                placeholder = { Text("0.0", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            )
                        }

                        // Quick helpers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("ربع كجم (0.25)" to "0.25", "نصف كجم (0.5)" to "0.5", "واحد كجم (1.0)" to "1").forEach { (lbl, valStr) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable { qtyInput = valStr }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = lbl, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = weightGramInput,
                            onValueChange = {
                                val filtered = it.filter { c -> c.isDigit() }
                                weightGramInput = filtered
                                val gramVal = filtered.toDoubleOrNull() ?: 0.0
                                qtyInput = (gramVal / 1000.0).toString()
                            },
                            label = { Text("مبيعات بالغرام المباشر (مثال: 450 غرام)", fontSize = 8.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFEF4444), unfocusedBorderColor = Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        )
                    }
                } else {
                    // Standard Simple Count Sales
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("الكمية الصادرة / المباعة (حبة)", fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                            placeholder = { Text("0.0", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("ملاحظات وتفاصيل إضافية", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1B3B6F),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("قيمة المبيعات:", fontSize = 11.sp, color = Color.DarkGray)
                            Text("$calculatedRevenue $currencySymbol", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3B6F))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("صافي الربح الفعلي:", fontSize = 11.sp, color = Color(0xFF15803D))
                            Text("$calculatedProfit $currencySymbol", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { syncToMizan = !syncToMizan }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = syncToMizan,
                        onCheckedChange = { syncToMizan = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1B3B6F))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("ترحيل فوري إلى الحساب الرئيسي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3B6F))
                        Text("إضافة معاملة وارد بـ $calculatedRevenue للرصيد اليومي", fontSize = 9.sp, color = Color.Gray)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = resolvedQty
                    if (q <= 0.0) {
                        errorMsg = "يرجى كتابة رقم صحيح أو كسري صالح"
                        return@Button
                    }
                    if (q > product.quantity) {
                        errorMsg = "المخزون الحالي غير كافٍ! متبقي ${product.quantity} $unitSuffix فقط."
                        return@Button
                    }
                    onConfirm(q, syncToMizan, noteInput.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B6F))
            ) {
                Text("تأكيد البيع وحساب الربح", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

// Edit Product Dialog Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductDialog(
    product: ProductEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (ProductEntity) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var category by remember { mutableStateOf(product.category) }
    var purchasePriceInput by remember { mutableStateOf(product.purchasePrice.toString()) }
    var sellingPriceInput by remember { mutableStateOf(product.sellingPrice.toString()) }
    var currentQtyInput by remember { mutableStateOf(product.quantity.toString()) }
    var alertThresholdInput by remember { mutableStateOf(product.lowStockThreshold.toString()) }
    var unitType by remember { mutableStateOf(product.unitType) }
    var selectedEmoji by remember { mutableStateOf(product.imageUrl ?: "📦") }

    // Multi-unit fields
    var hasSubUnits by remember { mutableStateOf(product.hasSubUnits) }
    var parentUnitName by remember { mutableStateOf(product.parentUnitName) }
    var subUnitName by remember { mutableStateOf(product.subUnitName) }
    var subUnitCountPerParentInput by remember { mutableStateOf(product.subUnitCountPerParent.toString()) }

    // Preload carton values
    val initialParentQty = if (product.hasSubUnits && product.subUnitCountPerParent > 1.0) (product.quantity / product.subUnitCountPerParent).toInt().toString() else ""
    val initialChildQty = if (product.hasSubUnits && product.subUnitCountPerParent > 1.0) (product.quantity % product.subUnitCountPerParent).toInt().toString() else ""

    var parentQtyInput by remember { mutableStateOf(initialParentQty) }
    var childQtyInput by remember { mutableStateOf(initialChildQty) }
    var weightGramInput by remember { mutableStateOf("") }

    var errorMsg by remember { mutableStateOf("") }

    val popularEmojis = listOf("📦", "🥤", "🥯", "💻", "👕", "🔋", "🧼", "💊", "✏️", "✨", "🍎", "🥛", "🍗", "🔑", "👟", "📱")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "تعديل بيانات السلعة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1B3B6F)
                )
            }
        },
        containerColor = Color.White,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (errorMsg.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEE2E2))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = errorMsg,
                            color = Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // GROUP 1: General Product Information (Compact & Side-by-side)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "بيانات السلعة الأساسية",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("اسم المنتج", fontSize = 9.sp) },
                                textStyle = TextStyle(fontSize = 11.sp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1B3B6F),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.weight(1.2f).height(54.dp)
                            )

                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text("التصنيف", fontSize = 9.sp) },
                                textStyle = TextStyle(fontSize = 11.sp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1B3B6F),
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.weight(0.8f).height(54.dp)
                            )
                        }
                    }
                }

                // GROUP 2: Financials & Pricing (التسعير والربح والفوائد)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "التسعير المالي والربحية",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    OutlinedTextField(
                                        value = purchasePriceInput,
                                        onValueChange = { purchasePriceInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("سعر الشراء", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                        placeholder = { Text("0.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                        textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.End),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF16A34A),
                                            unfocusedBorderColor = Color(0xFFBBF7D0),
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(54.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    OutlinedTextField(
                                        value = sellingPriceInput,
                                        onValueChange = { sellingPriceInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("سعر البيع", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                        placeholder = { Text("0.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                        textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.End),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF16A34A),
                                            unfocusedBorderColor = Color(0xFFBBF7D0),
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(54.dp)
                                    )
                                }
                            }
                        }

                        // Live Profitability Calculations
                        val purchasePr = purchasePriceInput.toDoubleOrNull() ?: 0.0
                        val sellingPr = sellingPriceInput.toDoubleOrNull() ?: 0.0
                        val singleProfit = sellingPr - purchasePr
                        val profitPct = if (sellingPr > 0.0) ((singleProfit / sellingPr) * 100).toInt() else 0

                        val calcBgColor = when {
                            singleProfit > 0.0 -> Color(0xFFDCFCE7)
                            singleProfit < 0.0 -> Color(0xFFFEE2E2)
                            else -> Color(0xFFF1F5F9)
                        }
                        val calcTextColor = when {
                            singleProfit > 0.0 -> Color(0xFF15803D)
                            singleProfit < 0.0 -> Color(0xFFB91C1C)
                            else -> Color(0xFF475569)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(calcBgColor)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = when {
                                    singleProfit > 0.0 -> "صافي ربح الوحدة: +$singleProfit $currencySymbol (بنسبة $profitPct%)"
                                    singleProfit < 0.0 -> "انتبه: خسارة محققة للقطعة: $singleProfit $currencySymbol!"
                                    else -> "أدخل سعر الشراء وسعر البيع لحساب الأرباح تلقائياً"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = calcTextColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // GROUP 3: Inventory Stock & Alarms (رصيد المخزون والتنبيهات)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "رصيد المخزون ووحدات القياس",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )

                        // Custom Segmented Unit Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("حبة", "كيلو").forEach { type ->
                                val selected = unitType == type
                                val label = if (type == "حبة") "حبة / قطعة" else "كيلو جرام / وزن"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Color(0xFF0284C7) else Color.White)
                                        .border(
                                            1.dp,
                                            if (selected) Color(0xFF0284C7) else Color(0xFFE2E8F0),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { unitType = type }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else Color(0xFF475569),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (unitType == "حبة") {
                            // Smart multi-units switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE0F2FE))
                                    .clickable { hasSubUnits = !hasSubUnits }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hasSubUnits,
                                    onCheckedChange = { hasSubUnits = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0284C7)),
                                    modifier = Modifier.scale(0.8f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(
                                        text = "تفعيل التعبئة والوحدات المتعددة (كرتون / علبة) 🔗",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0369A1)
                                    )
                                    Text(
                                        text = "تعديل كمية التعبئة للكرتون وفرز المخازن تلقائياً",
                                        fontSize = 7.5.sp,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }

                            if (hasSubUnits) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "مسميّات كرتون التعبئة والتوزيع",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0369A1)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = parentUnitName,
                                                onValueChange = { parentUnitName = it },
                                                label = { Text("الوحدة الكبرى (مثال: كرتون)", fontSize = 8.sp) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                                modifier = Modifier.weight(1.0f).height(54.dp)
                                            )
                                            OutlinedTextField(
                                                value = subUnitName,
                                                onValueChange = { subUnitName = it },
                                                label = { Text("وحدة التجزئة (مثال: حبة)", fontSize = 8.sp) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                                modifier = Modifier.weight(1.0f).height(54.dp)
                                            )
                                        }

                                        // Presets
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("كرتون" to "حبة", "صندوق" to "قطعة", "شد" to "بكت", "درزن" to "حبة").forEach { (p, c) ->
                                                val isMatch = parentUnitName == p
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isMatch) Color(0xFFE0F2FE) else Color(0xFFF1F5F9))
                                                        .border(1.dp, if (isMatch) Color(0xFF0284C7) else Color.Transparent, RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            parentUnitName = p
                                                            subUnitName = c
                                                        }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(text = "$p/$c", fontSize = 8.sp, color = Color(0xFF0369A1))
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = subUnitCountPerParentInput,
                                            onValueChange = { subUnitCountPerParentInput = it.filter { c -> c.isDigit() || c == '.' } },
                                            label = { Text("معامل الاحتواء (كم $subUnitName داخل الـ $parentUnitName الواحد؟)", fontSize = 8.sp) },
                                            textStyle = TextStyle(fontSize = 10.sp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                            modifier = Modifier.fillMaxWidth().height(54.dp)
                                        )

                                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)

                                        Text(
                                            text = "تعديل كمية المخزون برمجياً كرتونياً:",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.DarkGray
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = parentQtyInput,
                                                onValueChange = { parentQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("عدد ($parentUnitName)", fontSize = 8.sp) },
                                                placeholder = { Text("0.0", fontSize = 8.sp) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                                modifier = Modifier.weight(1f).height(54.dp)
                                            )
                                            OutlinedTextField(
                                                value = childQtyInput,
                                                onValueChange = { childQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("عدد ($subUnitName) إضافي", fontSize = 8.sp) },
                                                placeholder = { Text("0.0", fontSize = 8.sp) },
                                                textStyle = TextStyle(fontSize = 10.sp),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                                modifier = Modifier.weight(1f).height(54.dp)
                                            )
                                        }

                                        // Formula calculation in real time
                                        val capacity = subUnitCountPerParentInput.toDoubleOrNull() ?: 12.0
                                        val parentVal = parentQtyInput.toDoubleOrNull() ?: 0.0
                                        val childVal = childQtyInput.toDoubleOrNull() ?: 0.0
                                        val calculatedTotal = (parentVal * capacity) + childVal

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFF0FDF4))
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = "حسبة كرتونية ذكية: $parentVal $parentUnitName × $capacity $subUnitName = ${(parentVal * capacity).toInt()} $subUnitName + $childVal $subUnitName = ${calculatedTotal.toInt()} $subUnitName إجمالاً.",
                                                color = Color(0xFF15803D),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.5.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Default simple حبة input (Side-by-Side: الكمية + حد التنبيه)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = currentQtyInput,
                                                onValueChange = { currentQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("الكمية المتوفرة ($unitType)", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("0.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFBAE6FD)),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(0.8f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = alertThresholdInput,
                                                onValueChange = { alertThresholdInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("حد التنبيه", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("5.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF0284C7),
                                                    unfocusedBorderColor = Color(0xFFBAE6FD),
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Weight System
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Side-by-Side: الكمية + حد التنبيه
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = currentQtyInput,
                                                onValueChange = { currentQtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("الكمية المتوفرة (كجم)", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("0.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFBAE6FD)),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(0.8f)) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            OutlinedTextField(
                                                value = alertThresholdInput,
                                                onValueChange = { alertThresholdInput = it.filter { c -> c.isDigit() || c == '.' } },
                                                label = { Text("حد التنبيه (كجم)", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                placeholder = { Text("5.0", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                                textStyle = TextStyle(fontSize = 11.sp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF0284C7),
                                                    unfocusedBorderColor = Color(0xFFBAE6FD),
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(54.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick weights loaders
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("ربع كجم (+0.25)" to 0.25, "نصف كجم (+0.50)" to 0.50, "1 كجم (+1.00)" to 1.00).forEach { (lbl, valDec) ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFE0F2FE))
                                                .clickable {
                                                    val curr = currentQtyInput.toDoubleOrNull() ?: 0.0
                                                    currentQtyInput = (curr + valDec).toString()
                                                }
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = lbl, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = weightGramInput,
                                        onValueChange = {
                                            val filtered = it.filter { c -> c.isDigit() }
                                            weightGramInput = filtered
                                            val gramVal = filtered.toDoubleOrNull() ?: 0.0
                                            currentQtyInput = (gramVal / 1000.0).toString()
                                        },
                                        label = { Text("محوّل ومساعد الغرامات المباشرة (مثال: 750 جرام)", fontSize = 8.sp) },
                                        textStyle = TextStyle(fontSize = 10.sp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0284C7), unfocusedBorderColor = Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth().height(54.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val purchasePr = purchasePriceInput.toDoubleOrNull() ?: 0.0
                    val sellingPr = sellingPriceInput.toDoubleOrNull() ?: 0.0
                    val threshold = alertThresholdInput.toDoubleOrNull() ?: 5.0

                    // Calculate total quantity
                    var finalQty = 0.0
                    if (unitType == "حبة") {
                        if (hasSubUnits) {
                            val capacity = subUnitCountPerParentInput.toDoubleOrNull() ?: 12.0
                            val parentVal = parentQtyInput.toDoubleOrNull() ?: 0.0
                            val childVal = childQtyInput.toDoubleOrNull() ?: 0.0
                            finalQty = (parentVal * capacity) + childVal
                        } else {
                            finalQty = currentQtyInput.toDoubleOrNull() ?: 0.0
                        }
                    } else {
                        finalQty = currentQtyInput.toDoubleOrNull() ?: 0.0
                    }

                    val countPerParent = subUnitCountPerParentInput.toDoubleOrNull() ?: 1.0

                    if (name.isBlank() || category.isBlank()) {
                        errorMsg = "فضلاً قم بملء اسم المنتج وتصنيفه أولاً"
                        return@Button
                    }

                    val updated = product.copy(
                        name = name.trim(),
                        category = category.trim(),
                        purchasePrice = purchasePr,
                        sellingPrice = sellingPr,
                        quantity = finalQty,
                        lowStockThreshold = threshold,
                        unitType = unitType,
                        imageUrl = selectedEmoji,
                        hasSubUnits = hasSubUnits,
                        parentUnitName = parentUnitName.trim(),
                        subUnitName = subUnitName.trim(),
                        subUnitCountPerParent = countPerParent
                    )
                    onConfirm(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B6F))
            ) {
                Text("حفظ التغييرات", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color.Gray)
            }
        }
    )
}

// Product Detail Sheet & Movement history timeline Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailSheet(
    product: ProductEntity,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestock: () -> Unit,
    onSale: () -> Unit
) {
    val makhzanTransactions by viewModel.makhzanTransactionsState.collectAsState()
    val productTxs = makhzanTransactions.filter { it.productId == product.id }
    val isCritical = product.quantity <= product.lowStockThreshold

    val totalPurchase = product.purchasePrice * product.quantity
    val totalExpectedSales = product.sellingPrice * product.quantity
    val expectedProfit = totalExpectedSales - totalPurchase

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ROW 1: Header (Product Name, Icon Emoji, Action Controls and Close Button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leading visual emoji container
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCritical) Color(0xFFFFECEF) else Color(0xFFE8FDF0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = getProductEmoji(product), fontSize = 24.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B3B6F)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "السجل والأصل للمجموعة: ${product.category}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Edit Action
                    IconButton(
                        onClick = { onDismiss(); onEdit() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "تعديل",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Delete Action
                    IconButton(
                        onClick = { onDismiss(); onDelete() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Close Dialog Accent
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // ROW 2: Themed Multi-Metric Panel Display Grid (سعر الشراء، والبيع، والكميات)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Metric Panel A: Prices & Profit margin
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("شراء الوحدة", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                Text(viewModel.formatDoubleCurrency(product.purchasePrice, "ر.ي"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                            }
                            Column {
                                Text("بيع الوحدة", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                Text(viewModel.formatDoubleCurrency(product.sellingPrice, "ر.ي"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B3B6F))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val profitUnitTxt = product.sellingPrice - product.purchasePrice
                                Text("هامش ربح الحبة", fontSize = 9.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                                Text(
                                    text = viewModel.formatDoubleCurrency(profitUnitTxt, "ر.ي"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (profitUnitTxt >= 0.0) Color(0xFF16A34A) else Color(0xFFEF4444)
                                )
                            }
                        }
                    }

                    // Metric Panel B: Current stock levels and alerts
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isCritical) Color(0xFFFFECEF) else Color(0xFFE8FDF0)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("المخزون الحالي المتوفر", fontSize = 9.sp, color = Color(0xFF475569), fontWeight = FontWeight.Bold)
                                Text(
                                    text = formatProductStock(product), 
                                    fontSize = 13.sp, 
                                    fontWeight = FontWeight.Black, 
                                    color = if (isCritical) Color(0xFFEF4444) else Color(0xFF16A34A)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val qtyLabel = if (product.unitType == "كيلو") "كجم" else "حبة"
                                Text("حد الأمان والانتباه للسلعة", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                Text("${product.lowStockThreshold} $qtyLabel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            }
                        }
                    }

                    // Metric Panel C: Asset valuation summarizing capital invested
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("رأس المال في المخزن", fontSize = 9.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
                                Text(viewModel.formatDoubleCurrency(totalPurchase, "ر.ي"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                            }
                            Column {
                                Text("مبيعات متكاملة متوقعة", fontSize = 9.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
                                Text(viewModel.formatDoubleCurrency(totalExpectedSales, "ر.ي"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B3B6F))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("الأرباح الكلية المستهدفة", fontSize = 9.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                                Text(
                                    text = viewModel.formatDoubleCurrency(expectedProfit, "ر.ي"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }
                    }
                }

                // Quick Stock Actions (إضافة كمية للمنتج أو تسريح كمية)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onRestock() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "إضافة كمية (وارد)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = { onSale() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B6F)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "تسريح كمية (صادر)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0), thickness = 0.5.dp)
                    Text(
                        text = "سجل حركة السلعة",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0), thickness = 0.5.dp)
                }

                val sortedTxs = remember(productTxs) { productTxs.sortedBy { it.timestamp } }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (sortedTxs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TimelineItemRow(
                                title = "المخزون المتوفر",
                                qty = "${product.quantity} ${product.unitType}",
                                price = viewModel.formatDoubleCurrency(product.purchasePrice, "ر.ي"),
                                date = formatExactTimestamp(System.currentTimeMillis()),
                                isFirst = true,
                                note = "",
                                onDelete = null
                            )
                        }
                    } else {
                        val firstTx = sortedTxs.first()
                        val subsequentTxs = sortedTxs.drop(1).sortedByDescending { it.timestamp }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(subsequentTxs) { tx ->
                                val isWared = tx.type == "وارد"
                                val prefixTxt = if (isWared) "توريد (وارد)" else "بيع (صادر)"
                                val formattedDate = formatExactTimestamp(tx.timestamp)
                                val qtySuffix = if (product.unitType == "كيلو") "كجم" else "حبة"

                                TimelineItemRow(
                                    title = prefixTxt,
                                    qty = "${tx.quantityChanged} $qtySuffix",
                                    price = viewModel.formatDoubleCurrency(tx.pricePerUnit, "ر.ي"),
                                    date = formattedDate,
                                    isFirst = false,
                                    note = tx.note,
                                    onDelete = { viewModel.deleteMakhzanTransaction(tx) }
                                )
                            }

                            item {
                                val formattedFirstDate = formatExactTimestamp(firstTx.timestamp)
                                val qtySuffix = if (product.unitType == "كيلو") "كجم" else "حبة"
                                TimelineItemRow(
                                    title = "المخزون المتوفر",
                                    qty = "${firstTx.quantityChanged} $qtySuffix",
                                    price = viewModel.formatDoubleCurrency(firstTx.pricePerUnit, "ر.ي"),
                                    date = formattedFirstDate,
                                    isFirst = true,
                                    note = firstTx.note,
                                    onDelete = null
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
fun TimelineItemRow(
    title: String,
    qty: String,
    price: String,
    date: String,
    isFirst: Boolean,
    note: String,
    onDelete: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8FAFC)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Small color indicator dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isFirst) Color(0xFF64748B) else if (title.contains("وارد")) Color(0xFF16A34A) else Color(0xFF1B3B6F))
                    )
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFirst) Color(0xFF475569) else Color(0xFF1E293B)
                    )
                }

                if (onDelete != null && !isFirst) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إلغاء الحركة",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الكمية: $qty",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "السعر: $price",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = date,
                    fontSize = 8.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "ملاحظة: $note",
                    fontSize = 9.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

fun formatExactTimestamp(timestampMs: Long): String {
    val sdf = SimpleDateFormat("dd-MM-yyyy | hh:mm:ss a", Locale.getDefault())
    return sdf.format(Date(timestampMs))
        .replace("AM", "ص").replace("PM", "م")
        .replace("am", "ص").replace("pm", "م")
}

// Custom High-fidelity Barcode Scanner Composable Dialog
@Composable
fun BarcodeScannerDialog(
    products: List<ProductEntity>,
    onScanMatched: (ProductEntity) -> Unit,
    onDismiss: () -> Unit
) {
    // infinite laser beam line animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserOffset"
    )

    var simulatedResult by remember { mutableStateOf<ProductEntity?>(null) }
    var scanQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF1B3B6F))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ماسح الباركود التفاعلي 📷", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF1B3B6F))
            }
        },
        containerColor = Color.White,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "وجه الكاميرا نحو باركود المنتج لسهولة الجرد وإضافة المبيعات والتحقق اللحظي.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                // High fidelity camera viewfinder preview block with animating glow laser
                Box(
                    modifier = Modifier
                        .size(width = 240.dp, height = 130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                        .border(2.dp, Color(0xFF1B3B6F), RoundedCornerShape(14.dp))
                ) {
                    // Frame Corner guides drawn through standard offset compose Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val thickness = 4.dp.toPx()
                        val length = 18.dp.toPx()
                        val c = Color(0xFF1B3B6F)

                        // Top-Left corner guide
                        drawLine(c, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(length, 0f), thickness)
                        drawLine(c, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, length), thickness)
                        
                        // Top-Right corner guide
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - length, 0f), thickness)
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, length), thickness)
                        
                        // Bottom-Left corner guide
                        drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(length, size.height), thickness)
                        drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - length), thickness)
                        
                        // Bottom-Right corner guide
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - length, size.height), thickness)
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - length), thickness)
                    }

                    // Simulated neon red laser light beam sweeping up/down
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (laserOffset * 126).dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFFEF4444),
                                        Color(0xFFEF4444),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Text scanner indicator center overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = if (simulatedResult == null) "العدسة جاهزة للمسح..." else "تم التقاط الرمز! 🎉",
                            color = if (simulatedResult == null) Color.White else Color(0xFF4ADE80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "اختر منتجاً لتأكيد المطابقة:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155),
                    modifier = Modifier.align(Alignment.Start)
                )

                if (products.isEmpty()) {
                    Text(
                        "لا توجد منتجات متوفرة بالمستودع للمحاكاة.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                } else {
                    OutlinedTextField(
                        value = scanQuery,
                        onValueChange = { scanQuery = it },
                        placeholder = { Text("بحث عن اسم المنتج...", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    val filteredScanList = products.filter {
                        it.name.contains(scanQuery, ignoreCase = true) || it.category.contains(scanQuery, ignoreCase = true)
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredScanList) { prod ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                    .clickable {
                                        simulatedResult = prod
                                        onScanMatched(prod)
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(prod.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3B6F))
                                    Text(prod.category, fontSize = 10.sp, color = Color.Gray)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("رمز: #${prod.id}", color = Color(0xFF475569), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }
        }
    )
}
