package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entities.DeletedItemEntity
import com.example.data.local.entities.HabayebCustomer
import com.example.ui.viewmodel.FinanceViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject
import java.util.*

import com.example.ui.viewmodel.SyncSettingsViewModel

enum class FilterType {
    ALL, HABAYEB, LEDGER
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    viewModel: FinanceViewModel,
    syncViewModel: SyncSettingsViewModel,
    onBack: () -> Unit
) {
    // 100% Lifecycle-aware state flow tracking for better memory optimization
    val items by viewModel.deletedItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val customersList by viewModel.habayebCustomersState.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf(FilterType.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Search Mode States
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Clear Trash Prompt State
    var showEmptyConfirm by remember { mutableStateOf(false) }

    // Multi-Selection State Managers
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<String>() }

    fun toggleSelection(itemId: String) {
        if (selectedItemIds.contains(itemId)) {
            selectedItemIds.remove(itemId)
            if (selectedItemIds.isEmpty()) isSelectionMode = false
        } else {
            selectedItemIds.add(itemId)
        }
    }

    fun clearSelection() {
        selectedItemIds.clear()
        isSelectionMode = false
    }

    // Advanced Filter and Instant Search algorithm with Arabic Normalization compatibility (Asynchronously calculated on Dispatchers.Default to prevent main-thread blockage)
    var processedItems by remember { mutableStateOf(emptyList<DeletedItemEntity>()) }

    LaunchedEffect(items, searchQuery, selectedFilter) {
        val filtered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            var list = items

            // 1. Filter system type
            list = when (selectedFilter) {
                FilterType.ALL -> list
                FilterType.HABAYEB -> list.filter {
                    it.sourceSystem == "حبايب" || it.originalTableName.startsWith("habayeb_")
                }

                FilterType.LEDGER -> list.filter {
                    it.sourceSystem == "دار" ||
                            it.originalTableName == "transactions" ||
                            it.originalTableName == "dar_bundle" ||
                            it.originalTableName == "fixed_commitments"
                }
            }

            // 2. Perform search normalization if querying
            if (searchQuery.isNotBlank()) {
                val queryClean = searchQuery.trim().lowercase()
                list = list.filter { item ->
                    var match = item.sourceSystem.lowercase().contains(queryClean)
                    try {
                        val jsonObj = JSONObject(item.jsonData)
                        val name = jsonObj.optString("name", "").lowercase()
                        val desc = jsonObj.optString("description", "").lowercase()
                        val prodName = jsonObj.optString("productName", "").lowercase()
                        val notes = jsonObj.optString("notes", "").lowercase()
                        val category = jsonObj.optString("category", "").lowercase()

                        match = match || name.contains(queryClean) ||
                                desc.contains(queryClean) ||
                                prodName.contains(queryClean) ||
                                notes.contains(queryClean) ||
                                category.contains(queryClean)
                    } catch (e: Exception) {
                        // Fail-safe skip on malformated JSON
                    }
                    match
                }
            }
            list
        }
        processedItems = filtered
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive && !isSelectionMode,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it }).togetherWith(fadeOut() + slideOutHorizontally { it })
                },
                label = "ToolbarTransition"
            ) { searching ->
                if (searching) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        color = Color.White,
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = stringResource(id = R.string.trash_back),
                                    tint = Color(0xFF1E293B)
                                )
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(id = R.string.trash_search_placeholder),
                                        fontSize = 14.sp
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF0F766E)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    TopAppBar(
                        title = {
                            if (isSelectionMode) {
                                Text(
                                    text = stringResource(id = R.string.trash_selected_count, selectedItemIds.size),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            } else {
                                Text(
                                    text = stringResource(id = R.string.trash_title) + " 🗑️",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        },
                        navigationIcon = {
                            if (isSelectionMode) {
                                IconButton(onClick = { clearSelection() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(id = R.string.trash_cancel_selection),
                                        tint = Color(0xFF1E293B)
                                    )
                                }
                            } else {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = stringResource(id = R.string.trash_back),
                                        tint = Color(0xFF1E293B)
                                    )
                                }
                            }
                        },
                        actions = {
                            if (isSelectionMode) {
                                IconButton(onClick = {
                                    val selectedItems = items.filter { selectedItemIds.contains(it.id) }
                                    syncViewModel.restoreMultipleItems(selectedItems)
                                    clearSelection()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = stringResource(id = R.string.trash_restore_selected),
                                        tint = Color(0xFF0F766E)
                                    )
                                }
                                IconButton(onClick = {
                                    val selectedItems = items.filter { selectedItemIds.contains(it.id) }
                                    syncViewModel.permanentlyDeleteMultipleItems(selectedItems)
                                    clearSelection()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = stringResource(id = R.string.trash_delete_selected_permanently),
                                        tint = Color(0xFFF43F5E)
                                    )
                                }
                            } else {
                                if (items.isNotEmpty()) {
                                    // Visual AssistChip selector for deep and interactive filters
                                    Box {
                                        AssistChip(
                                            onClick = { showFilterMenu = true },
                                            label = {
                                                Text(
                                                    text = when (selectedFilter) {
                                                        FilterType.ALL -> stringResource(id = R.string.trash_filter_all)
                                                        FilterType.HABAYEB -> stringResource(id = R.string.trash_filter_habayeb)
                                                        FilterType.LEDGER -> stringResource(id = R.string.trash_filter_general)
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F766E),
                                                    fontSize = 11.sp
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0F766E),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = Color(0xFF0F766E).copy(alpha = 0.08f)
                                            ),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = Color(0xFF0F766E).copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.padding(end = 4.dp)
                                        )

                                        DropdownMenu(
                                            expanded = showFilterMenu,
                                            onDismissRequest = { showFilterMenu = false },
                                            modifier = Modifier.background(Color.White)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(id = R.string.trash_filter_all)) },
                                                onClick = {
                                                    selectedFilter = FilterType.ALL
                                                    showFilterMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(id = R.string.trash_filter_habayeb)) },
                                                onClick = {
                                                    selectedFilter = FilterType.HABAYEB
                                                    showFilterMenu = false
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text(stringResource(id = R.string.trash_filter_general)) },
                                                onClick = {
                                                    selectedFilter = FilterType.LEDGER
                                                    showFilterMenu = false
                                                }
                                            )
                                        }
                                    }

                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = stringResource(id = R.string.trash_search),
                                            tint = Color(0xFF475569)
                                        )
                                    }
                                    IconButton(onClick = { showEmptyConfirm = true }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteForever,
                                            contentDescription = stringResource(id = R.string.trash_empty_bin),
                                            tint = Color(0xFFF43F5E)
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(id = R.string.trash_empty_message),
                        fontSize = 18.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(processedItems, key = { it.id }) { item ->
                    val isSelected = selectedItemIds.contains(item.id)

                    TrashItemCard(
                        item = item,
                        customersList = customersList,
                        isSelected = isSelected,
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                toggleSelection(item.id)
                            }
                        },
                        onClick = {
                            if (isSelectionMode) {
                                toggleSelection(item.id)
                            }
                        },
                        onRestore = { syncViewModel.restoreDeletedItem(item) },
                        onPermanentDelete = { syncViewModel.permanentlyDeleteDeletedItem(item) }
                    )
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            confirmButton = {
                Button(
                    onClick = {
                        syncViewModel.emptyTrash()
                        showEmptyConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.trash_empty_confirm_btn),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text(
                        text = stringResource(id = R.string.trash_cancel),
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(id = R.string.trash_confirm_empty_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.trash_confirm_empty_desc),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashItemCard(
    item: DeletedItemEntity,
    customersList: List<HabayebCustomer>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Human-friendly table categorization labels
    val typeLabel = when (item.originalTableName) {
        "transactions" -> "حركة مالية (الدار)"
        "habayeb_transactions" -> "معاملة دين (حبايب)"
        "fixed_commitments" -> "التزام مالي"
        "habayeb_customers" -> "حساب عميل"
        "habayeb_bundle" -> "حساب عميل بجميع معاملاته 👥"
        "dar_bundle" -> "حزمة ميزان الدار"
        else -> item.originalTableName
    }

    // Deluxe brand tag colors with customized Glassmorphic card theme
    val systemColor = when (item.sourceSystem) {
        "حبايب" -> Color(0xFF1E3A8A)    // Premium Dark Blue
        else -> Color(0xFF0F766E)       // Emerald Primary
    }

    val indicatorColor = when {
        item.originalTableName.startsWith("habayeb_") -> Color(0xFF2563EB) // Blue
        else -> {
            try {
                val json = JSONObject(item.jsonData)
                if (json.optString("type", "") == "EXPENSE") Color(0xFFF43F5E) else Color(0xFF10B981)
            } catch (e: Exception) {
                Color(0xFF0F766E)
            }
        }
    }

    val cardBorder = if (isSelected) BorderStroke(2.dp, Color(0xFF0F766E)) else BorderStroke(0.8.dp, Color(0xFFE2E8F0))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 2.dp),
        border = cardBorder
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0xFF0F766E).copy(alpha = 0.06f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Glassmorphic interactive color tag strip on the side (RTL alignment safe)
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(indicatorColor)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header badges and timestamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF64748B).copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = typeLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(systemColor.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = item.sourceSystem,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = systemColor
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F766E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else {
                            val parsedDate = try {
                                java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(item.deletedAt))
                            } catch (e: Exception) {
                                ""
                            }
                            Text(
                                text = parsedDate,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Polymorphic extraction of JSON data safely
                    val jsonObj = remember(item.jsonData) {
                        try {
                            JSONObject(item.jsonData)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    var titleText = stringResource(id = R.string.trash_item_unknown)
                    var amountText = ""
                    var subText = ""

                    if (jsonObj != null) {
                        when (item.originalTableName) {
                            "transactions" -> {
                                titleText = jsonObj.optString("description", "").ifEmpty { jsonObj.optString("category", "معاملة الدار") }
                                val amountVal = jsonObj.optDouble("amount", 0.0)
                                val prefix = if (jsonObj.optString("type") == "EXPENSE") "-" else "+"
                                val formatted = try { String.format(Locale.getDefault(), "%,.2f", amountVal) } catch (e: Exception) { amountVal.toString() }
                                amountText = "$prefix$formatted ر.ي"
                                subText = "التصنيف: " + jsonObj.optString("category", "")
                            }

                            "habayeb_transactions" -> {
                                // Linked directly with parent account name
                                val customerId = jsonObj.optString("customerId", "")
                                val resolvedName = customersList.find { it.id == customerId }?.name ?: "عميل غير معروف"
                                titleText = stringResource(id = R.string.trash_linked_with, resolvedName)

                                val amt = jsonObj.optDouble("amount", 0.0)
                                val t = jsonObj.optString("type", "")
                                val typeAr = when (t) {
                                    "OWED_BY_THEM" -> "دين عليه"
                                    "PAYMENT_BY_THEM" -> "سداد منه"
                                    "OWED_TO_THEM" -> "دين له"
                                    "PAYMENT_TO_THEM" -> "سداد له"
                                    else -> t
                                }
                                val formatted = try { String.format(Locale.getDefault(), "%,.2f", amt) } catch (e: Exception) { amt.toString() }
                                amountText = "$formatted  ر.ي ($typeAr)"
                                subText = jsonObj.optString("description", "")
                            }

                            "fixed_commitments" -> {
                                titleText = jsonObj.optString("name", "هدف مالي")
                                val targetVal = jsonObj.optDouble("targetAmount", 0.0)
                                val formattedTarget = try { String.format(Locale.getDefault(), "%,.2f", targetVal) } catch (e: Exception) { targetVal.toString() }
                                amountText = stringResource(id = R.string.trash_target_amount, formattedTarget)
                                val progressVal = jsonObj.optDouble("currentProgress", 0.0)
                                val formattedProgress = try { String.format(Locale.getDefault(), "%.1f", progressVal) } catch (e: Exception) { progressVal.toString() }
                                subText = "نسبة الإنجاز الحالي: %$formattedProgress"
                            }



                            "habayeb_customers" -> {
                                titleText = jsonObj.optString("name", "عميل")
                                val phoneStr = jsonObj.optString("phone", "").ifEmpty { "ليس لديه هاتف" }
                                amountText = stringResource(id = R.string.trash_phone, phoneStr)
                                subText = jsonObj.optString("notes", "")
                            }

                            "habayeb_bundle" -> {
                                val cust = jsonObj.optJSONObject("customer")
                                if (cust != null) {
                                    titleText = cust.optString("name", "عميل حبايب")
                                    val phoneStr = cust.optString("phone", "").ifEmpty { "ليس لديه هاتف" }
                                    amountText = stringResource(id = R.string.trash_phone, phoneStr)
                                }
                                val txCount = jsonObj.optInt("totalTransactions", 0)
                                subText = stringResource(id = R.string.trash_bundle_tx_count, txCount)
                            }

                            "dar_bundle" -> {
                                val txsArray = jsonObj.optJSONArray("transactions")
                                val count = txsArray?.length() ?: 0
                                titleText = "حزمة ميزان الدار"
                                amountText = stringResource(id = R.string.trash_bundle_tx_count, count)
                            }

                            else -> {
                                titleText = jsonObj.optString("name", jsonObj.optString("description", "سجل مجهول"))
                            }
                        }
                    } else {
                        titleText = "تعذر قراءة التفاصيل"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = titleText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (amountText.isNotEmpty()) {
                            Text(
                                text = amountText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = systemColor
                            )
                        }
                        if (subText.isNotEmpty()) {
                            Text(
                                text = subText,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Bottom CTA Action buttons
                    if (!isSelected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF43F5E)),
                                border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = stringResource(id = R.string.trash_delete_permanently),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = onRestore,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                            ) {
                                Text(
                                    text = stringResource(id = R.string.trash_restore_action),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                Button(
                    onClick = {
                        onPermanentDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.trash_delete_permanently),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        text = stringResource(id = R.string.trash_cancel),
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(id = R.string.trash_delete_warning_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.trash_delete_warning_desc),
                    fontSize = 14.sp
                )
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
