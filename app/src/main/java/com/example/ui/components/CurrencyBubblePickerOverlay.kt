package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entities.AppSettings
import com.example.ui.screens.habayeb.utils.CurrencyConfig

@Composable
fun CurrencySettingsDialog(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    var localDefaultCurrency by remember { mutableStateOf(settings.currencySymbol) }
    
    // Hold local rates
    var localRateSar by remember { mutableStateOf(settings.exchangeRateSar) }
    var localRateUsd by remember { mutableStateOf(settings.exchangeRateUsd) }
    var localRateYer by remember { mutableStateOf(settings.exchangeRateYer) }

    val currenciesToDisplay = listOf("ر.ي", "ر.س", "$")

    // Select which target currency to configure
    var selectedTargetCurrency by remember(localDefaultCurrency) {
        mutableStateOf(
            if (localDefaultCurrency == "ر.ي") "$" else "ر.ي"
        )
    }

    // Determine current rate being configured
    val currentRateValue = when {
        localDefaultCurrency == "ر.ي" && selectedTargetCurrency == "$" -> localRateUsd
        localDefaultCurrency == "ر.ي" && selectedTargetCurrency == "ر.س" -> localRateSar
        localDefaultCurrency == "ر.س" && selectedTargetCurrency == "$" -> localRateUsd
        localDefaultCurrency == "ر.س" && selectedTargetCurrency == "ر.ي" -> localRateYer
        localDefaultCurrency == "$" && selectedTargetCurrency == "ر.س" -> localRateSar
        localDefaultCurrency == "$" && selectedTargetCurrency == "ر.ي" -> localRateYer
        else -> 1.0
    }

    var rateInputStr by remember(localDefaultCurrency, selectedTargetCurrency) {
        mutableStateOf(if (currentRateValue > 0.0 && currentRateValue != 1.0) currentRateValue.toString() else "")
    }

    val rateFocusRequester = remember { FocusRequester() }

    // Auto-focus on exchange rate field upon entering
    LaunchedEffect(localDefaultCurrency, selectedTargetCurrency) {
        kotlinx.coroutines.delay(200)
        try {
            rateFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .width(280.dp) // Perfect mid-width to host side-by-side contents gracefully
                .padding(4.dp)
                .imePadding()
                .animateContentSize(animationSpec = tween(200)),
            // Highly creative, modern asymmetrical rounded leaf/petal shape
            shape = RoundedCornerShape(
                topStart = 28.dp,
                bottomEnd = 28.dp,
                topEnd = 6.dp,
                bottomStart = 6.dp
            ),
            border = BorderStroke(1.2.dp, Color(0xFFE2E8F0)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Centered Mini Title & Close Trigger
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "إعدادات الصرف الفوري",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                // Side-by-Side configuration Row (No flag graphics, 100% text-based pure aesthetic)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left Column: Default main App Currency
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "العملة الافتراضية",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            currenciesToDisplay.forEach { symbol ->
                                val isSelected = localDefaultCurrency == symbol
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp, topEnd = 2.dp, bottomStart = 2.dp))
                                        .background(if (isSelected) Color(0xFF4B36A2).copy(alpha = 0.12f) else Color.Transparent)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            localDefaultCurrency = symbol
                                            if (selectedTargetCurrency == symbol) {
                                                selectedTargetCurrency = if (symbol == "ر.ي") "$" else "ر.ي"
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = symbol,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF4B36A2) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    // Right Column: Target exchange rates
                    Column(
                        modifier = Modifier.weight(1.3f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "سعر صرف الهدف",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )

                        val availableTargets = currenciesToDisplay.filter { it != localDefaultCurrency }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(1.5.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            availableTargets.forEach { symbol ->
                                val isSelected = selectedTargetCurrency == symbol
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(topStart = 5.dp, bottomEnd = 5.dp, topEnd = 1.5.dp, bottomStart = 1.5.dp))
                                        .background(if (isSelected) Color(0xFF4B36A2) else Color.Transparent)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedTargetCurrency = symbol
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = symbol,
                                        fontSize = 8.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Ultra-compact custom zero-padding equation input field
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp, topEnd = 2.dp, bottomStart = 2.dp))
                                .background(Color.White)
                                .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp, topEnd = 2.dp, bottomStart = 2.dp))
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "1 $selectedTargetCurrency =",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = rateInputStr,
                                    onValueChange = { newVal ->
                                        val cleaned = CurrencyConfig.normalizeDigits(newVal)
                                        rateInputStr = cleaned
                                        val parsed = cleaned.toDoubleOrNull() ?: 1.0
                                        when {
                                            localDefaultCurrency == "ر.ي" && selectedTargetCurrency == "$" -> localRateUsd = parsed
                                            localDefaultCurrency == "ر.ي" && selectedTargetCurrency == "ر.س" -> localRateSar = parsed
                                            localDefaultCurrency == "ر.س" && selectedTargetCurrency == "$" -> localRateUsd = parsed
                                            localDefaultCurrency == "ر.س" && selectedTargetCurrency == "ر.ي" -> localRateYer = parsed
                                            localDefaultCurrency == "$" && selectedTargetCurrency == "ر.س" -> localRateSar = parsed
                                            localDefaultCurrency == "$" && selectedTargetCurrency == "ر.ي" -> localRateYer = parsed
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        textAlign = TextAlign.Center,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(rateFocusRequester),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (rateInputStr.isEmpty()) {
                                                Text(
                                                    text = "سعر",
                                                    fontSize = 8.sp,
                                                    color = Color(0xFF94A3B8),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }

                            Text(
                                text = localDefaultCurrency,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Centered action buttons with creative matched edges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val finalRateSar = if (localRateSar <= 0.0) 1.0 else localRateSar
                            val finalRateUsd = if (localRateUsd <= 0.0) 1.0 else localRateUsd
                            val finalRateYer = if (localRateYer <= 0.0) 1.0 else localRateYer

                            val updatedSettings = settings.copy(
                                currencySymbol = localDefaultCurrency,
                                exchangeRateSar = finalRateSar,
                                exchangeRateUsd = finalRateUsd,
                                exchangeRateYer = finalRateYer
                            )
                            onSaveSettings(updatedSettings)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B36A2)),
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(24.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "حفظ وإعتماد",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF1F5F9),
                            contentColor = Color(0xFF475569)
                        ),
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "إلغاء",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
