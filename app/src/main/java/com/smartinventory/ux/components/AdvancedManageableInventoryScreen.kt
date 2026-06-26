package com.smartinventory.ux.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Calendar

// نماذج البيانات الأساسية المستقرة
data class CustomCategory(val id: String, var name: String)
data class CustomProduct(val id: String, val name: String, val categoryId: String, val quantity: Double)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AdvancedManageableInventoryScreen(
    initialCategories: List<CustomCategory>,
    initialProducts: List<CustomProduct>,
    onProductDeleted: (String) -> Unit, // واجهة أمنة لإشعار قاعدة البيانات بالحذف
    onProductEdited: (CustomProduct) -> Unit, // واجهة لتعديل المنتج
    onProductMoved: (productId: String, targetCategoryId: String) -> Unit, // واجهة لنقل المنتج بين الأصناف
    onCategoryReordered: (List<CustomCategory>) -> Unit // واجهة لحفظ ترتيب الأصناف الجديد
) {
    // إدارة الحالات داخلياً لسرعة الاستجابة اللحظية للواجهة قبل الحفظ النهائي
    val categories = remember { mutableStateListOf<CustomCategory>().apply { addAll(initialCategories) } }
    val products = remember { mutableStateListOf<CustomProduct>().apply { addAll(initialProducts) } }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // حالات فتح النوافذ المنبثقة للعمليات التفاعلية
    var productToEdit by remember { mutableStateOf<CustomProduct?>(null) }
    var productToMove by remember { mutableStateOf<CustomProduct?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                
                // أولاً: جزء الأصناف مع إمكانية التحريك (Drag to Reorder) وإعادة الترتيب
                item {
                    Text(
                        text = "الأصناف (اضغط مطولاً للتحريك والترتيب)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // شريط الأصناف التفاعلي القابل للتحريك وإعادة الترتيب
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
                            var isDragging by remember { mutableStateOf(false) }
                            
                            Box(
                                modifier = Modifier
                                    .animateItemPlacement()
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { isDragging = true },
                                            onDragEnd = { 
                                                isDragging = false 
                                                onCategoryReordered(categories.toList())
                                            },
                                            onDragCancel = { isDragging = false },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                // منطق تبديل العناصر السريع عند السحب يميناً أو يساراً
                                                if (dragAmount.x > 50 && index < categories.lastIndex) {
                                                    val temp = categories[index]
                                                    categories[index] = categories[index + 1]
                                                    categories[index + 1] = temp
                                                } else if (dragAmount.x < -50 && index > 0) {
                                                    val temp = categories[index]
                                                    categories[index] = categories[index - 1]
                                                    categories[index - 1] = temp
                                                }
                                            }
                                        )
                                    }
                            ) {
                                CategoryManageableChip(
                                    category = category,
                                    isDragging = isDragging,
                                    onDeleteClick = {
                                        categories.remove(category)
                                        // هنا يتم ترحيل التغيير لقاعدة البيانات بشكل مستقل
                                    },
                                    onRenameClick = { newName ->
                                        category.name = newName
                                        // تحديث الاسم فوراً في الواجهة
                                    }
                                )
                            }
                        }
                    }
                }

                // ثانياً: قائمة المنتجات مع ميزة الحذف والتعديل بالسحب (Swipe Actions)
                item {
                    Text(
                        text = "المنتجات (اسحب لليمين للتعديل والنقل، لليسار للحذف)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                itemsIndexed(products, key = { _, product -> product.id }) { _, product ->
                    
                    // استخدام واجهة SwipeToDismissBox الرسمية من Material 3
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            when (dismissValue) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    // تم السحب لليمين: فتح قائمة التعديل السريع
                                    productToEdit = product
                                    false // لمنع اختفاء العنصر بصرياً من القائمة بشكل دائم
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    // تم السحب لليسار: تنفيذ الحذف المؤقت مع إمكانية التراجع
                                    val backupProduct = product
                                    products.remove(product)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "تم حذف ${product.name}",
                                            actionLabel = "تراجع",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            products.add(backupProduct) // إعادة المنتج في حال التراجع
                                        } else {
                                            onProductDeleted(product.id) // حذف نهائي من قاعدة البيانات
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color by animateColorAsState(
                                when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF0EA5E9) // أزرق للتعديل
                                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFEF4444) // أحمر للحذف
                                    else -> Color.Transparent
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل ونقل", tint = Color.White)
                                } else {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف نهائي", tint = Color.White)
                                }
                            }
                        },
                        content = {
                            // بطاقة المنتج الأساسية المتوافقة مع نظام النسبة والتناسب
                            ProportionalProductCard(
                                product = CustomProduct(product.id, product.name, "التصنيف", product.quantity),
                                onEditRequest = { productToEdit = product },
                                onMoveRequest = { productToMove = product }
                            )
                        },
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
    }

    // نافذة تعديل المنتج سريعة ومبسطة (تعديل مباشر)
    productToEdit?.let { product ->
        EditProductDialog(
            product = product,
            onDismiss = { productToEdit = null },
            onConfirm = { updatedProduct ->
                val index = products.indexOfFirst { it.id == product.id }
                if (index != -1) products[index] = updatedProduct
                onProductEdited(updatedProduct)
                productToEdit = null
            }
        )
    }

    // نافذة تحريك المنتج السريع لنقل صنف المنتج
    productToMove?.let { product ->
        MoveProductDialog(
            product = product,
            categories = categories,
            onDismiss = { productToMove = null },
            onConfirm = { targetCategoryId ->
                onProductMoved(product.id, targetCategoryId)
                productToMove = null
            }
        )
    }
}

// مكون فرعي لبطاقات الأصناف يدعم التعديل والحذف المباشرين
@Composable
fun CategoryManageableChip(
    category: CustomCategory,
    isDragging: Boolean,
    onDeleteClick: () -> Unit,
    onRenameClick: (String) -> Unit
) {
    val elevation by animateDpAsState(if (isDragging) 8.dp else 1.dp)
    var isEditing by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(category.name) }

    Surface(
        tonalElevation = elevation,
        shape = RoundedCornerShape(20.dp),
        color = if (isDragging) Color(0xFFE2E8F0) else Color.White,
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isEditing) {
                TextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.width(80.dp)
                )
                IconButton(onClick = { 
                    onRenameClick(tempName)
                    isEditing = false 
                }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                }
            } else {
                Text(
                    text = category.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { isEditing = true }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "حذف صنف",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDeleteClick() }
                )
            }
        }
    }
}

// مكون فرعي لبطاقة منتج تدعم أزرار التحكم الجانبية السريعة
@Composable
fun ProportionalProductCard(
    product: CustomProduct,
    onEditRequest: () -> Unit,
    onMoveRequest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Inventory, contentDescription = null, tint = Color(0xFF64748B))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("الكمية: ${product.quantity}", fontSize = 12.sp, color = Color(0xFF64748B))
            }
            
            // أزرار التحكم السريع المدمجة نسبة وتناسب
            IconButton(onClick = onEditRequest) {
                Icon(Icons.Default.Edit, contentDescription = "تعديل بيانات", tint = Color(0xFF0EA5E9), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onMoveRequest) {
                Icon(Icons.Default.DriveFileMove, contentDescription = "تحريك ونقل", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun EditProductDialog(
    product: CustomProduct,
    onDismiss: () -> Unit,
    onConfirm: (CustomProduct) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var quantityStr by remember { mutableStateOf(product.quantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل منتج", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المنتج") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("الكمية") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityStr.toDoubleOrNull() ?: 0.0
                    onConfirm(product.copy(name = name, quantity = qty))
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("تأكيد")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun MoveProductDialog(
    product: CustomProduct,
    categories: List<CustomCategory>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نقل المنتج إلى صنف آخر", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                Text("اختر الصنف المستهدف لنقل المنتج \"${product.name}\":", modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(categories) { category ->
                        Card(
                            onClick = { onConfirm(category.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Category, contentDescription = null, tint = Color(0xFF64748B))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(category.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("إلغاء")
            }
        }
    )
}
