package com.example.ui.screens.habayeb.components

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.HabayebTransaction
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun AddTransactionPopup(
    customer: HabayebCustomer,
    viewModel: FinanceViewModel,
    initialSelectedType: String = "OWED_BY_THEM",
    editingTransaction: HabayebTransaction? = null,
    onDismiss: () -> Unit,
    activeThemeColor: Color,
    activeSubColor: Color
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currencySymbol = viewModel.settingsState.collectAsStateWithLifecycle().value.currencySymbol

    var amountStr by rememberSaveable { mutableStateOf(editingTransaction?.amount?.toInt()?.toString() ?: "") }
    var descStr by rememberSaveable { mutableStateOf(editingTransaction?.description ?: "") }
    var selectedType by rememberSaveable { mutableStateOf(editingTransaction?.type ?: initialSelectedType) }
    
    val amountFocusRequester = remember { FocusRequester() }
    val descFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        try {
            amountFocusRequester.requestFocus()
            softwareKeyboardController?.show()
        } catch(e: Exception) {}
    }

    val isLendMode = selectedType == "OWED_BY_THEM" || selectedType == "PAYMENT_BY_THEM"
    var isLendOperationSelected by rememberSaveable { mutableStateOf(isLendMode) }
    var dateMillis by rememberSaveable { mutableStateOf(editingTransaction?.timestamp?.let { it * 1000 } ?: System.currentTimeMillis()) }
    var showCalculator by rememberSaveable { mutableStateOf(false) }
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
            val debtInteractionSource = remember { MutableInteractionSource() }
            val isDebtPressed by debtInteractionSource.collectIsPressedAsState()
            val debtScale by animateFloatAsState(
                targetValue = if (isDebtPressed) 0.95f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = 1500f
                ),
                label = "DebtBtnScale"
            )

            val payInteractionSource = remember { MutableInteractionSource() }
            val isPayPressed by payInteractionSource.collectIsPressedAsState()
            val payScale by animateFloatAsState(
                targetValue = if (isPayPressed) 0.95f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = 1500f
                ),
                label = "PayBtnScale"
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
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
                        text = if (editingTransaction != null) stringResource(id = R.string.habayeb_edit_tx_title).replace(" 📝","") else stringResource(id = R.string.habayeb_add_tx_button).replace(" ➕",""),
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.habayeb_go_back), tint = activeThemeColor, modifier = Modifier.size(16.dp))
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                text = customer.name.firstOrNull()?.toString() ?: "ا",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = stringResource(id = R.string.habayeb_account_name) + ": ${customer.name}",
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
                            text = stringResource(id = R.string.habayeb_register_owed_by),
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
                            text = stringResource(id = R.string.habayeb_register_owed_to),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isLendOperationSelected) Color.White else Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Secondary Sub-Selector (Interactive Icon Cards)
                val isNegativeAct = selectedType == "OWED_BY_THEM" || selectedType == "OWED_TO_THEM"
                val isPositiveAct = selectedType == "PAYMENT_BY_THEM" || selectedType == "PAYMENT_TO_THEM"

                val dynamicThemeColor = if (isLendOperationSelected) Color(0xFFEF4444) else Color(0xFF10B981)
                val dynamicSubColor = if (isLendOperationSelected) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Debt Card (دين جديد)
                    val debtBg = if (isNegativeAct) Color(0xFF1B3B6F) else Color(0xFFF1F5F9)
                    val debtContentColor = if (isNegativeAct) Color.White else Color(0xFF475569)
                    val debtBorder = if (isNegativeAct) null else BorderStroke(1.dp, Color(0xFFE2E8F0))
                    val debtShadow = if (isNegativeAct) 6.dp else 0.dp
                    
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = debtBg),
                        border = debtBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = debtShadow),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = debtScale
                                scaleY = debtScale
                            }
                            .clickable(
                                interactionSource = debtInteractionSource,
                                indication = null,
                                onClick = {
                                    selectedType = if (isLendOperationSelected) "OWED_BY_THEM" else "OWED_TO_THEM"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📝", fontSize = 14.sp)
                                if (isNegativeAct) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            Crossfade(targetState = isLendOperationSelected, animationSpec = tween(150), label = "DebtSubLabel") { lendMode ->
                                val textLabel = if (lendMode) stringResource(id = R.string.habayeb_register_owed_by) else stringResource(id = R.string.habayeb_register_owed_to)
                                Text(
                                    text = textLabel,
                                    color = debtContentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // 2. Payment Card (سداد دفعة)
                    val payBg = if (isPositiveAct) Color(0xFF1B3B6F) else Color(0xFFF1F5F9)
                    val payContentColor = if (isPositiveAct) Color.White else Color(0xFF475569)
                    val payBorder = if (isPositiveAct) null else BorderStroke(1.dp, Color(0xFFE2E8F0))
                    val payShadow = if (isPositiveAct) 6.dp else 0.dp

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = payBg),
                        border = payBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = payShadow),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = payScale
                                scaleY = payScale
                            }
                            .clickable(
                                interactionSource = payInteractionSource,
                                indication = null,
                                onClick = {
                                    selectedType = if (isLendOperationSelected) "PAYMENT_BY_THEM" else "PAYMENT_TO_THEM"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "💸", fontSize = 14.sp)
                                if (isPositiveAct) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            Crossfade(targetState = isLendOperationSelected, animationSpec = tween(150), label = "PaySubLabel") { lendMode ->
                                val textLabel = if (lendMode) stringResource(id = R.string.habayeb_pdf_tx_payment_by) else stringResource(id = R.string.habayeb_pdf_tx_payment_to)
                                Text(
                                    text = textLabel,
                                    color = payContentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

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
                            text = stringResource(id = R.string.habayeb_amount) + " *",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { descFocusRequester.requestFocus() }),
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
                                contentDescription = stringResource(id = R.string.habayeb_calculator),
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

                // Detail comments area with leading Hamburger symbol
                OutlinedTextField(
                    value = descStr,
                    onValueChange = { descStr = it },
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.habayeb_tx_desc_optional),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
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
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 56.dp)
                        .focusRequester(descFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Date displays container
                val formattedSelectedDate = remember(dateMillis) {
                    val sdf = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.ENGLISH)
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
                            stringResource(id = R.string.habayeb_edit_date),
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
                            contentDescription = stringResource(id = R.string.habayeb_tx_date),
                            tint = dynamicThemeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

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
                                text = stringResource(id = R.string.habayeb_sync_main_income),
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
                        softwareKeyboardController?.hide()
                        if (isSaving) return@Button
                        isSaving = true

                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            Toast.makeText(context, context.getString(R.string.habayeb_toast_valid_amount), Toast.LENGTH_SHORT).show()
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
                                category = context.getString(R.string.habayeb_category_other),
                                amount = amount,
                                description = "${context.getString(R.string.habayeb_pdf_tx_payment_by)}: ${customer.name}${if (descStr.isNotBlank()) " - " + descStr.trim() else ""}",
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
                        Toast.makeText(context, context.getString(R.string.habayeb_toast_tx_save_success), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicThemeColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.habayeb_confirm_save).replace(" 💾",""),
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
            },
            activeThemeColor = activeThemeColor,
            activeSubColor = activeSubColor
        )
    }
}
