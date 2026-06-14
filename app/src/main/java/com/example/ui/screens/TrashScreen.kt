package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DeletedItemEntity
import com.example.ui.viewmodel.FinanceViewModel
import org.json.JSONObject
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.deletedItemsFlow.collectAsState(initial = emptyList())
    
    // Search States
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Empty trash confirmation
    var showEmptyConfirm by remember { mutableStateOf(false) }
    
    // Multi-Selection State
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

    // Advanced Filtering Logic
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else {
            items.filter { item ->
                val searchLower = searchQuery.lowercase().trim()
                var matches = item.sourceSystem.lowercase().contains(searchLower)
                
                try {
                    val jsonObj = JSONObject(item.jsonData)
                    val name = jsonObj.optString("name", "").lowercase()
                    val desc = jsonObj.optString("description", "").lowercase()
                    val prodName = jsonObj.optString("productName", "").lowercase()
                    val amount = jsonObj.optString("amount", "").lowercase()
                    val cat = jsonObj.optString("category", "").lowercase()
                    
                    matches = matches || name.contains(searchLower) || 
                              desc.contains(searchLower) || 
                              prodName.contains(searchLower) || 
                              amount.contains(searchLower) || 
                              cat.contains(searchLower)
                } catch (e: Exception) {}
                matches
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive && !isSelectionMode,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it }).togetherWith(fadeOut() + slideOutHorizontally { it })
                },
                label = "TopBarSearch"
            ) { searching ->
                if (searching) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        color = Color.White,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { 
                                isSearchActive = false 
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF1E293B))
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث عن اسم، مبلغ، أو نوع معاملة...", fontSize = 14.sp) },
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF0F766E)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                Text("تم تحديد ${selectedItemIds.size}", fontWeight = FontWeight.Bold)
                            } else {
                                Text("سلة المحذوفات 🗑️", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                        },
                        navigationIcon = {
                            if (isSelectionMode) {
                                IconButton(onClick = { clearSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد")
                                }
                            } else {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "عودة", tint = Color(0xFF1E293B))
                                }
                            }
                        },
                        actions = {
                            if (isSelectionMode) {
                                IconButton(onClick = {
                                    val selectedItems = items.filter { selectedItemIds.contains(it.id) }
                                    viewModel.restoreMultipleItems(selectedItems)
                                    clearSelection()
                                }) {
                                    Icon(Icons.Default.Restore, contentDescription = "استعادة المحددة", tint = Color(0xFF0F766E))
                                }
                                IconButton(onClick = {
                                    val selectedItems = items.filter { selectedItemIds.contains(it.id) }
                                    viewModel.permanentlyDeleteMultipleItems(selectedItems)
                                    clearSelection()
                                }) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "حذف المحددة نهائياً", tint = Color.Red)
                                }
                            } else {
                                if (items.isNotEmpty()) {
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(Icons.Default.Search, contentDescription = "بحث", tint = Color(0xFF475569))
                                    }
                                    IconButton(onClick = { showEmptyConfirm = true }) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = "إفراغ السلة", tint = Color.Red)
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "السلة نظيفة تماماً ✅",
                        fontSize = 18.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val isSelected = selectedItemIds.contains(item.id)
                    
                    TrashItemCard(
                        item = item,
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
                        onRestore = { viewModel.restoreDeletedItem(item) },
                        onPermanentDelete = { viewModel.permanentlyDeleteDeletedItem(item) }
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
                        viewModel.emptyTrash()
                        showEmptyConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("إفراغ الآن", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            title = { Text("تأكيد إفراغ السلة نهائياً 🧹") },
            text = { Text("هل أنت متأكد من رغبتك في حذف كافة العناصر في السلة نهائياً وبشكل قطعي؟ لا يمكن التراجع.") },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashItemCard(
    item: DeletedItemEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val typeLabel = when (item.originalTableName) {
        "transactions" -> "حركة مالية (الدار)"
        "habayeb_transactions" -> "معاملة دين (حبايب)"
        "makhzan_transactions" -> "حركة مخزنية (المخزن)"
        "fixed_commitments" -> "التزام / هدف مالي"
        "makhzan_products" -> "سلعة مخزنية"
        "habayeb_customers" -> "حساب عميل / ديون"
        "habayeb_bundle" -> "حزمة حساب عميل (حبايب)"
        "makhzan_bundle" -> "حزمة منتج كاملة (المخزن)"
        "dar_bundle" -> "حزمة شاملة (الدار)"
        else -> item.originalTableName
    }

    val systemColor = when (item.sourceSystem) {
        "حبايب" -> Color(0xFF3B82F6)
        "مخزن" -> Color(0xFFEAB308)
        else -> Color(0xFF10B981) // دار
    }

    val cardBorder = if (isSelected) BorderStroke(2.dp, Color(0xFF0F766E)) else null
    val cardAlpha = if (isSelected) 0.05f else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 2.dp),
        border = cardBorder
    ) {
        Box {
            if (isSelected) {
                Box(modifier = Modifier.matchParentSize().background(Color(0xFF0F766E).copy(alpha = 0.05f)))
            }
            
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF64748B).copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = typeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(systemColor.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = item.sourceSystem,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = systemColor
                            )
                        }
                    }
                    
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F766E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        val parsedDate = try {
                            java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ENGLISH).format(Date(item.deletedAt))
                        } catch (e: Exception) { "" }
                        Text(
                            text = parsedDate,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                var desc = "بيانات غير متوفرة"
                var amount = ""
                var subText = ""
                try {
                    val jsonObj = JSONObject(item.jsonData)
                    desc = jsonObj.optString("name", jsonObj.optString("description", jsonObj.optString("productName", "سجل محذوف")))
                    
                    if (item.originalTableName.endsWith("_bundle")) {
                        val txCount = jsonObj.optInt("totalTransactions", 0)
                        val totalNet = jsonObj.optDouble("totalNet", 0.0)
                        amount = "حزمة مشمولة: $txCount معاملات"
                        subText = "صافي القيمة: ${String.format("%.2f", totalNet)} ر.ي"
                    } else if (jsonObj.has("amount")) {
                        amount = jsonObj.getDouble("amount").toString() + " ر.ي"
                        val cat = jsonObj.optString("category", "")
                        if (cat.isNotEmpty()) subText = "التصنيف: $cat"
                    } else if (jsonObj.has("quantityChanged")) {
                        val q = jsonObj.getDouble("quantityChanged")
                        val t = jsonObj.optString("type", "")
                        amount = "كمية: $q ($t)"
                    } else if (jsonObj.has("targetAmount")) {
                        amount = "المستهدف: " + jsonObj.getDouble("targetAmount")
                    } else if (jsonObj.has("sellingPrice")) {
                        amount = "السعر: " + jsonObj.getDouble("sellingPrice")
                    } else if (jsonObj.has("phone")) {
                        amount = "الهاتف: " + jsonObj.optString("phone", "لا يوجد")
                    }
                } catch (e: Exception) {}

                Column {
                    Text(
                        text = desc,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (amount.isNotEmpty()) {
                        Text(
                            text = amount,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0F766E)
                        )
                    }
                    if (subText.isNotEmpty()) {
                        Text(
                            text = subText,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                if (!isSelected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                        ) {
                            Text("حذف نهائي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onRestore,
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            Text("استعادة ✅", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حذف نهائي", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء", color = Color(0xFF64748B))
                }
            },
            title = { Text("تنبيه الحذف النهائي 🚫") },
            text = { Text("هل أنت متأكد من حذف هذا السجل بشكل نهائي؟ لن تتمكن من استعادته مرة أخرى أبداً.") },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
