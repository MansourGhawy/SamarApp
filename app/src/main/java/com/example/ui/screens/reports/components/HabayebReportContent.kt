package com.example.ui.screens.reports.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entities.HabayebCustomer
import com.example.ui.theme.*

fun LazyListScope.habayebReportContent(
    habayebSearchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    owedByThemTotal: Double,
    owedToThemTotal: Double,
    filteredCustomerProfiles: List<Pair<HabayebCustomer, Double>>,
    onCustomerSelected: (HabayebCustomer) -> Unit,
    formatDouble: (Double) -> String
) {
    item { Spacer(modifier = Modifier.height(8.dp)) }

    // Hubayeb Summary cards
    item {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reports_owed_by_them_label), fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(formatDouble(owedByThemTotal), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoftGreen)
                }

                Box(modifier = Modifier.width(1.dp).height(35.dp).background(Color(0xFFE9ECEF)))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reports_owed_to_them_label), fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(formatDouble(owedToThemTotal), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoftRed)
                }

                Box(modifier = Modifier.width(1.dp).height(35.dp).background(Color(0xFFE9ECEF)))

                val netDebt = owedByThemTotal - owedToThemTotal
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reports_net_debt_label), fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        formatDouble(netDebt),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (netDebt >= 0) SoftGreen else SoftRed
                    )
                }
            }
        }
    }

    // Search field
    item {
        OutlinedTextField(
            value = habayebSearchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text(stringResource(R.string.reports_habayeb_search_placeholder), fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = Color(0xFFDEE2E6),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )
    }

    // Customer debt directory header
    item {
        Text(
            stringResource(R.string.reports_habayeb_directory_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
    }

    if (filteredCustomerProfiles.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.reports_habayeb_search_empty), fontSize = 11.sp, color = Color.Gray)
            }
        }
    } else {
        items(filteredCustomerProfiles) { (customer, balance) ->
            val context = LocalContext.current
            val statusString = if (balance > 0) {
                context.getString(R.string.reports_owed_by_them_status)
            } else if (balance < 0) {
                context.getString(R.string.reports_owed_to_them_status)
            } else {
                context.getString(R.string.reports_balanced_status)
            }
            val color = if (balance > 0) SoftGreen else if (balance < 0) SoftRed else Color.Gray

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F1EF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCustomerSelected(customer) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Action dial/whatsapp buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Call button
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0))
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "اتصال", tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                        }

                        // WhatsApp button
                        IconButton(
                            onClick = {
                                try {
                                    val cleanNum = customer.phone.replace(Regex("[^\\d+]"), "")
                                    val msg = context.getString(
                                        R.string.reports_whatsapp_message_pattern,
                                        customer.name,
                                        formatDouble(kotlin.math.abs(balance)),
                                        statusString
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum&text=${Uri.encode(msg)}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7))
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "واتساب", tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Balance & labels
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatDouble(kotlin.math.abs(balance)), fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
                            Text(statusString, fontSize = 9.sp, color = Color.Gray)
                        }

                        // Avatar badge
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = customer.name.take(1).uppercase(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }
    }

    item { Spacer(modifier = Modifier.height(24.dp)) }
}
