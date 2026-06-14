package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DeletedItemEntity
import com.example.ui.viewmodel.FinanceViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.deletedItemsFlow.collectAsState(initial = emptyList())
    var showEmptyConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سلة المحذوفات 🗑️", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "عودة", tint = Color(0xFF1E293B))
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showEmptyConfirm = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "إفراغ السلة", tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF1F5F9)
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "السلة فارغة",
                    fontSize = 18.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val typeLabel = when (item.originalTableName) {
                                    "transactions" -> "حركة مالية (الدار)"
                                    "habayeb_transactions" -> "معاملة دين (حبايب)"
                                    "makhzan_transactions" -> "حركة مخزنية (المخزن)"
                                    "fixed_commitments" -> "التزام / هدف مالي"
                                    "makhzan_products" -> "سلعة مخزنية"
                                    "habayeb_customers" -> "حساب عميل / ديون"
                                    "habayeb_bundle" -> "حزمة حساب عميل (حبايب)"
                                    "makhzan_bundle" -> "حزمة منتج كاملة (المخزن)"
                                    else -> item.originalTableName
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF64748B).copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }

                                val systemColor = when (item.sourceSystem) {
                                    "حبايب" -> Color(0xFF3B82F6)
                                    "مخزن" -> Color(0xFFEAB308)
                                    else -> Color(0xFF10B981) // دار
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(systemColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = item.sourceSystem,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = systemColor
                                    )
                                }
                                
                                val parsedDate = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(item.deletedAt))
                                Text(
                                    text = parsedDate,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            var desc = "بيانات غير متوفرة"
                            var amount = ""
                            try {
                                val jsonObj = JSONObject(item.jsonData)
                                desc = jsonObj.optString("description", "")
                                if (desc.isEmpty()) {
                                    desc = jsonObj.optString("productName", "")
                                }
                                if (desc.isEmpty()) {
                                    desc = jsonObj.optString("name", "")
                                }
                                if (desc.isEmpty()) {
                                    desc = "سجل محذوف"
                                }

                                if (jsonObj.has("amount")) {
                                    amount = jsonObj.getDouble("amount").toString() + " ر.ي"
                                } else if (item.originalTableName.endsWith("_bundle")) {
                                    val txCount = jsonObj.optInt("totalTransactions", 0)
                                    amount = "إجمالي عدد المعاملات المشمولة: $txCount"
                                } else if (jsonObj.has("quantityChanged")) {
                                    val q = jsonObj.getDouble("quantityChanged")
                                    val t = jsonObj.optString("type", "")
                                    amount = "كمية: $q ($t)"
                                } else if (jsonObj.has("targetAmount")) {
                                    val tgt = jsonObj.getDouble("targetAmount")
                                    val cur = jsonObj.optDouble("currentProgress", 0.0)
                                    amount = "المستهدف: $tgt / المتوفر الحالي: $cur"
                                } else if (jsonObj.has("sellingPrice")) {
                                    val sp = jsonObj.getDouble("sellingPrice")
                                    val qty = jsonObj.getDouble("quantity")
                                    amount = "السعر: $sp / الكمية المحذوفة: $qty"
                                } else if (jsonObj.has("phone")) {
                                    amount = "الهاتف: " + jsonObj.optString("phone", "لا يوجد")
                                }
                            } catch (e: Exception) { e.printStackTrace() }

                            Column {
                                Text(
                                    text = desc,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                )
                                if (amount.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = amount,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F766E)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var showDeleteConfirm by remember { mutableStateOf(false) }

                                if (showDeleteConfirm) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteConfirm = false },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    viewModel.permanentlyDeleteDeletedItem(item)
                                                    showDeleteConfirm = false
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("حذف نهائي", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteConfirm = false }) {
                                                Text("إلغاء", color = Color(0xFF64748B))
                                            }
                                        },
                                        title = { Text("تأكيد الحذف النهائي 🚫", fontWeight = FontWeight.Bold) },
                                        text = { Text("هل أنت متأكد من حذف هذا العنصر نهائياً؟ هذا الإجراء لا يمكن التراجع عنه مطلقاً.") },
                                        containerColor = Color.White,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                                ) {
                                    Text("حذف نهائي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.restoreDeletedItem(item) },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                                ) {
                                    Text("استعادة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
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
                    Text("إفراغ", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            },
            title = {
                Text("تأكيد إفراغ السلة")
            },
            text = {
                Text("هل أنت متأكد من رغبتك في حذف كافة العناصر نهائياً؟ هذا الإجراء لا يمكن التراجع عنه.")
            }
        )
    }
}
