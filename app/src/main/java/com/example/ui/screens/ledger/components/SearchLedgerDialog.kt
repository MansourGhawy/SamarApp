package com.example.ui.screens.ledger.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.TransactionDb
import com.example.domain.DateUtils
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed

@Composable
fun SearchLedgerDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<TransactionDb>,
    formatCurrency: (Double) -> String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val searchFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.habayeb_close_search))
                    }
                    Text(
                        stringResource(id = R.string.ledger_search_title),
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldPrimary,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(id = R.string.ledger_search_subtitle), color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    textStyle = LocalTextStyle.current.copy(color = Color(0xFF1E293B), textAlign = TextAlign.Right),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedPlaceholderColor = Color(0xFF94A3B8),
                        unfocusedPlaceholderColor = Color(0xFF94A3B8)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (results.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank()) stringResource(id = R.string.ledger_search_empty_state) else stringResource(id = R.string.ledger_search_no_results),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        stringResource(id = R.string.ledger_search_results_count, results.size),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(results) { index, tx ->
                            key(tx.id) {
                                SearchResultItem(
                                    tx = tx,
                                    nextTx = if (index < results.size - 1) results[index + 1] else null,
                                    formatCurrency = formatCurrency
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
fun SearchResultItem(
    tx: TransactionDb,
    nextTx: TransactionDb?,
    formatCurrency: (Double) -> String
) {
    val dayName = DateUtils.getDayOfWeekArabic(tx.timestamp)
    val fullDate = DateUtils.formatDateFull(tx.timestamp)
    val timeStr = DateUtils.formatTime24Or12(tx.timestamp)

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = formatCurrency(tx.amount),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (tx.type == "INCOME") SoftGreen else SoftRed,
                        fontSize = 13.sp
                    )
                    Text(
                        text = timeStr,
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = tx.description.ifBlank { tx.category },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = EmeraldPrimary,
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = "$dayName - $fullDate",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        if (nextTx != null) {
            val interval = DateUtils.formatDurationBetween(tx.timestamp, nextTx.timestamp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = interval,
                    fontSize = 9.sp,
                    color = EmeraldPrimary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = EmeraldPrimary.copy(alpha = 0.4f)
                )
            }
        }
    }
}
