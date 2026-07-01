package com.example.ui.screens.reports.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.HabayebTransaction
import com.example.data.serialization.PdfReportGenerator
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerDetailsDialog(
    customer: HabayebCustomer,
    viewModel: FinanceViewModel,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPdfExporting by remember { mutableStateOf(false) }
    val transactions by viewModel.habayebTransactionsState.collectAsStateWithLifecycle()

    val customerTxs = remember(transactions, customer) {
        transactions.filter { it.customerId == customer.id }.sortedBy { it.timestamp }
    }

    val currencyGroups = remember(customerTxs) {
        customerTxs.groupBy { com.example.ui.screens.habayeb.utils.CurrencyConfig.parseTransactionCurrency(it.description, currencySymbol).first }
    }

    val netDebtsMap = remember(currencyGroups) {
        currencyGroups.mapValues { (curr, txs) ->
            val owedByThem = txs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
            val paymentByThem = txs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
            val owedToThem = txs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
            val paymentToThem = txs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
            (owedByThem - paymentByThem) - (owedToThem - paymentToThem)
        }
    }

    val activeDebts = remember(netDebtsMap) {
        netDebtsMap.filter { kotlin.math.abs(it.value) >= 0.01 }
    }

    val balanceText = remember(activeDebts) {
        if (activeDebts.isEmpty()) {
            "0.00 $currencySymbol"
        } else {
            activeDebts.map { (curr, debtVal) ->
                val formatted = try {
                    val symbols = java.text.DecimalFormatSymbols(Locale.ENGLISH)
                    val formatter = java.text.DecimalFormat("#,##0", symbols)
                    formatter.format(kotlin.math.abs(debtVal))
                } catch (e: Exception) {
                    "${kotlin.math.abs(debtVal)}"
                }
                "$formatted $curr"
            }.joinToString(" و ")
        }
    }

    val statusString = remember(activeDebts) {
        if (activeDebts.isEmpty()) {
            "متعادل"
        } else {
            activeDebts.map { (curr, debtVal) ->
                val label = if (debtVal > 0) "عليه" else "له"
                "$label ($curr)"
            }.joinToString(" و ")
        }
    }

    val netDebt = remember(netDebtsMap) {
        netDebtsMap[currencySymbol] ?: 0.0
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Area
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8F9FA))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = customer.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                            if (customer.phone.isNotBlank()) {
                                Text(
                                    text = customer.phone,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE9ECEF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color(0xFF495057),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        // Stat Card
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.reports_net_debt_label),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    
                                    val nonZeroDebts = netDebtsMap.filter { kotlin.math.abs(it.value) >= 0.01 }
                                    if (nonZeroDebts.isEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "0.00 $currencySymbol",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.LightGray.copy(alpha = 0.2f))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = context.getString(R.string.reports_balanced_status),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    } else {
                                        nonZeroDebts.forEach { (curr, debtVal) ->
                                            val cStatusColor = if (debtVal > 0) SoftRed else SoftGreen
                                            val cStatusText = if (debtVal > 0) {
                                                context.getString(R.string.reports_owed_by_them_status)
                                            } else {
                                                context.getString(R.string.reports_owed_to_them_status)
                                            }
                                            val formattedDebt = try {
                                                val symbols = java.text.DecimalFormatSymbols(Locale.ENGLISH)
                                                val formatter = java.text.DecimalFormat("#,##0", symbols)
                                                formatter.format(kotlin.math.abs(debtVal)) + " " + curr
                                            } catch (e: Exception) {
                                                "${kotlin.math.abs(debtVal)} $curr"
                                            }
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = formattedDebt,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = cStatusColor
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(cStatusColor.copy(alpha = 0.12f))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = cStatusText,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = cStatusColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Communications and Actions Row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Call Button
                                if (customer.phone.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "اتصال", tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("اتصال", color = Color(0xFF475569), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // WhatsApp Button
                                    Button(
                                        onClick = {
                                            try {
                                                val cleanNum = customer.phone.replace(Regex("[^\\d+]"), "")
                                                val msg = context.getString(
                                                    R.string.reports_whatsapp_message_pattern,
                                                    customer.name,
                                                    balanceText,
                                                    statusString
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum&text=${Uri.encode(msg)}")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCFCE7)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1.2f).height(40.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("واتساب", color = Color(0xFF15803D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Ledger History Header
                        item {
                            Text(
                                text = "حركة الحساب والتعاملات الأخيرة:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (customerTxs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("لا توجد تعاملات مسجلة لهذا الحساب 🌸", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            items(customerTxs) { tx ->
                                val dateStr = try {
                                    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                                    sdf.format(Date(tx.timestamp * 1000L))
                                } catch (e: Exception) {
                                    ""
                                }

                                val txTypeStr = when (tx.type) {
                                    "OWED_BY_THEM" -> "دين عليه (مطلوب منه)"
                                    "PAYMENT_BY_THEM" -> "سداد منه (دفعة واردة)"
                                    "OWED_TO_THEM" -> "دين له (له عندنا)"
                                    "PAYMENT_TO_THEM" -> "سداد له (دفعة منصرفة)"
                                    else -> ""
                                }

                                val isPositiveSign = tx.type == "PAYMENT_BY_THEM" || tx.type == "OWED_TO_THEM"
                                val isGreenColor = tx.type == "PAYMENT_BY_THEM" || tx.type == "PAYMENT_TO_THEM"
                                val indicatorColor = if (isGreenColor) SoftGreen else SoftRed
                                val txPrefix = if (isPositiveSign) "+" else "-"

                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                    border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Professional indicator bar
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(4.dp)
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            indicatorColor,
                                                            indicatorColor.copy(alpha = 0.6f)
                                                        )
                                                    )
                                                )
                                        )

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = txTypeStr,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF334155)
                                                )
                                                if (tx.description.isNotBlank()) {
                                                    Text(
                                                        text = tx.description,
                                                        fontSize = 10.sp,
                                                        color = Color.Gray,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = dateStr,
                                                    fontSize = 9.sp,
                                                    color = Color.LightGray
                                                )
                                            }

                                            Text(
                                                text = try {
                                                    val symbols = java.text.DecimalFormatSymbols(Locale.ENGLISH)
                                                    val formatter = java.text.DecimalFormat("#,##0", symbols)
                                                    txPrefix + formatter.format(tx.amount) + " " + currencySymbol
                                                } catch (e: Exception) {
                                                    "$txPrefix${tx.amount} $currencySymbol"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = indicatorColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    // Dialog Actions Footer
                    Surface(
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp,
                        color = Color(0xFFF8F9FA),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Share PDF Button
                            Button(
                                onClick = {
                                    isPdfExporting = true
                                    try {
                                        PdfReportGenerator.generateAndHandleCustomerPdfReportAsync(
                                            scope = coroutineScope,
                                            context = context,
                                            customer = customer,
                                            netDebt = netDebt,
                                            transactions = customerTxs,
                                            action = "SHARE",
                                            onFinished = { isPdfExporting = false }
                                        )
                                    } catch (e: Exception) {
                                        isPdfExporting = false
                                        Toast.makeText(context, "فشل تصدير التقرير", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isPdfExporting,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                if (isPdfExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("جاري التصدير...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                } else {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير كشف حساب", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تصدير كشف حساب", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            // Share Text Button
                            Button(
                                onClick = {
                                    val builder = StringBuilder()
                                    builder.append("🤝 *كشف حساب العميل / المورد:* ${customer.name}\n")
                                    if (customer.phone.isNotBlank()) {
                                        builder.append("📞 *رقم الهاتف:* ${customer.phone}\n")
                                    }
                                    builder.append("⚖️ *الرصيد المالي الحالي:* $balanceText ($statusString)\n\n")
                                    if (customerTxs.isNotEmpty()) {
                                        builder.append("📋 *تفاصيل التعاملات الأخيرة:*\n")
                                        customerTxs.take(15).forEach { tx ->
                                            val dStr = try {
                                                val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                                                sdf.format(Date(tx.timestamp * 1000L))
                                            } catch (e: Exception) { "" }
                                            val tStr = when(tx.type) {
                                                "OWED_BY_THEM" -> "دين عليه"
                                                "PAYMENT_BY_THEM" -> "سداد منه"
                                                "OWED_TO_THEM" -> "له عندنا"
                                                "PAYMENT_TO_THEM" -> "سداد له"
                                                else -> ""
                                            }
                                            val notePart = if (tx.description.isNotBlank()) " [${tx.description}]" else ""
                                            builder.append("- *$dStr* - $tStr: ${tx.amount} $currencySymbol$notePart\n")
                                        }
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, builder.toString())
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة كشف الحساب"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(0.8f).height(44.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة نصية", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
