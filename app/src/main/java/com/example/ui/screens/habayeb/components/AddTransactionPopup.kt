package com.example.ui.screens.habayeb.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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

    val initialCurrencyAndDesc = remember(editingTransaction) {
        if (editingTransaction != null) {
            com.example.ui.screens.habayeb.utils.CurrencyConfig.parseTransactionCurrency(
                editingTransaction.description,
                currencySymbol
            )
        } else {
            Pair(currencySymbol, "")
        }
    }

    var selectedTransactionCurrency by rememberSaveable {
        mutableStateOf(editingTransaction?.currency_code?.let { if (it == "DEFAULT") currencySymbol else it } ?: initialCurrencyAndDesc.first)
    }

    var isForeignSelected by rememberSaveable { mutableStateOf(editingTransaction?.is_foreign ?: false) }
    var foreignExchangeRate by rememberSaveable { mutableStateOf(editingTransaction?.exchange_rate ?: 1.0) }
    var isForeignRateCalculated by rememberSaveable { mutableStateOf(editingTransaction?.is_rate_calculated ?: false) }
    var selectedForeignSymbol by remember { mutableStateOf(editingTransaction?.currency_code ?: "") }

    var amountStr by rememberSaveable {
        mutableStateOf(
            editingTransaction?.let {
                if (it.is_foreign) {
                    if (it.foreign_amount % 1.0 == 0.0) it.foreign_amount.toInt().toString() else it.foreign_amount.toString()
                } else {
                    if (it.amount % 1.0 == 0.0) it.amount.toInt().toString() else it.amount.toString()
                }
            } ?: ""
        )
    }
    var descStr by rememberSaveable { mutableStateOf(if (editingTransaction != null) initialCurrencyAndDesc.second else "") }
    var selectedType by rememberSaveable { mutableStateOf(editingTransaction?.type ?: initialSelectedType) }
    var showFastActionPopup by remember { mutableStateOf(false) }
    
    val amountFocusRequester = remember { FocusRequester() }
    val descFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(showFastActionPopup) {
        if (!showFastActionPopup) {
            kotlinx.coroutines.delay(200)
            try {
                amountFocusRequester.requestFocus()
                softwareKeyboardController?.show()
            } catch(e: Exception) {}
        }
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

    val executeSave = { finalActionType: String ->
        softwareKeyboardController?.hide()
        if (!isSaving) {
            isSaving = true

            val cleanAmountStr = com.example.ui.screens.habayeb.utils.CurrencyConfig.normalizeDigits(amountStr).trim()
            val amount = cleanAmountStr.toDoubleOrNull() ?: 0.0
            if (amount <= 0.0) {
                Toast.makeText(context, context.getString(R.string.habayeb_toast_valid_amount), Toast.LENGTH_SHORT).show()
                isSaving = false
            } else {
                if (editingTransaction != null) {
                    viewModel.deleteHabayebTransaction(editingTransaction.id)
                }

                val finalEquivalentAmount = if (isForeignSelected) {
                    if (isForeignRateCalculated) {
                        amount * foreignExchangeRate
                    } else {
                        0.0
                    }
                } else {
                    0.0
                }

                val mainIncomeAmount = if (isForeignSelected) finalEquivalentAmount else amount

                val presetMainTxId = if (finalActionType == "PAYMENT_BY_THEM" && syncAsMainIncome && mainIncomeAmount > 0.0) {
                    "tx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
                } else null

                if (presetMainTxId != null) {
                    viewModel.addTransaction(
                        type = "INCOME",
                        category = context.getString(R.string.habayeb_category_other),
                        amount = mainIncomeAmount,
                        description = "${context.getString(R.string.habayeb_pdf_tx_payment_by)}: ${customer.name}${if (descStr.isNotBlank()) " - " + descStr.trim() else ""}",
                        timestamp = dateMillis / 1000,
                        presetId = presetMainTxId
                    )
                }

                viewModel.addHabayebTransaction(
                    customerId = customer.id,
                    type = finalActionType,
                    amount = if (isForeignSelected) finalEquivalentAmount else amount,
                    desc = com.example.ui.screens.habayeb.utils.CurrencyConfig.formatDescriptionWithCurrency(descStr.trim(), selectedTransactionCurrency),
                    timestamp = dateMillis / 1000,
                    linkedMainTxId = presetMainTxId,
                    isForeign = isForeignSelected,
                    currencyCode = selectedTransactionCurrency,
                    foreignAmount = amount,
                    exchangeRate = foreignExchangeRate,
                    isRateCalculated = isForeignRateCalculated,
                    equivalentAmount = finalEquivalentAmount
                )
                Toast.makeText(context, context.getString(R.string.habayeb_toast_tx_save_success), Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (showFastActionPopup) {
                showFastActionPopup = false
            } else {
                onDismiss()
            }
        }
    ) {
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
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .animateContentSize(animationSpec = tween(300))
            ) {
                AnimatedContent(
                    targetState = showFastActionPopup,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300))).togetherWith(
                                slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(animationSpec = tween(300))
                            ).using(androidx.compose.animation.SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> androidx.compose.animation.core.snap() }))
                        } else {
                            (slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(animationSpec = tween(300))).togetherWith(
                                slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(300))
                            ).using(androidx.compose.animation.SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> androidx.compose.animation.core.snap() }))
                        }
                    },
                    label = "FastActionPopupTransition"
                ) { isFastAction ->
                    if (isFastAction) {
                        val settings = viewModel.settingsState.collectAsStateWithLifecycle().value
                        val settingsRate = when (selectedForeignSymbol) {
                            "ر.س" -> settings.exchangeRateSar
                            "$" -> settings.exchangeRateUsd
                            "ر.ي" -> settings.exchangeRateYer
                            else -> 1.0
                        }

                        // Determine initial calculation mode based on transactional state
                        var calculationMode by remember {
                            mutableStateOf(
                                if (!isForeignRateCalculated) "NONE"
                                else if (foreignExchangeRate == settingsRate && settingsRate > 1.0) "AUTO"
                                else "MANUAL"
                            )
                        }

                        var rateInputStr by remember { 
                            mutableStateOf(
                                if (isForeignRateCalculated) {
                                    if (calculationMode == "AUTO") settingsRate.toString() else foreignExchangeRate.toString()
                                } else ""
                            ) 
                        }

                        LaunchedEffect(calculationMode, settingsRate) {
                            if (calculationMode == "AUTO") {
                                rateInputStr = if (settingsRate > 0.0) settingsRate.toString() else ""
                            } else if (calculationMode == "NONE") {
                                rateInputStr = ""
                            }
                        }

                        val rateFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(calculationMode) {
                            if (isForeignSelected && calculationMode == "MANUAL") {
                                kotlinx.coroutines.delay(100)
                                try { rateFocusRequester.requestFocus() } catch (e: Exception) {}
                            }
                        }
                        
                        Column(
                            modifier = Modifier
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                IconButton(
                                    onClick = { 
                                        showFastActionPopup = false 
                                        // Focus back on the first screen input field so keyboard is retained
                                        try { amountFocusRequester.requestFocus() } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.align(Alignment.CenterStart).size(24.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = activeThemeColor)
                                }
                                Text(
                                    text = "تأكيد المعاملة",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeThemeColor,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            
                            if (isForeignSelected) {
                                // 3-Way Mode selector
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (calculationMode == "NONE") activeThemeColor else Color.Transparent)
                                            .clickable { 
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                calculationMode = "NONE" 
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("لا يُحتسب", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (calculationMode == "NONE") Color.White else Color.Gray)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (calculationMode == "MANUAL") activeThemeColor else Color.Transparent)
                                            .clickable { 
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                calculationMode = "MANUAL" 
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("صرف يدوي", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (calculationMode == "MANUAL") Color.White else Color.Gray)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (calculationMode == "AUTO") activeThemeColor else Color.Transparent)
                                            .clickable { 
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                calculationMode = "AUTO" 
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("تلقائي (الإعدادات)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (calculationMode == "AUTO") Color.White else Color.Gray)
                                    }
                                }

                                if (calculationMode != "NONE") {
                                    OutlinedTextField(
                                        value = rateInputStr,
                                        onValueChange = { 
                                            if (calculationMode == "MANUAL") {
                                                rateInputStr = it 
                                            }
                                        },
                                        placeholder = { Text("سعر الصرف", fontSize = 10.sp) },
                                        readOnly = calculationMode == "AUTO",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = activeThemeColor,
                                            cursorColor = activeThemeColor,
                                            unfocusedBorderColor = Color.LightGray,
                                            focusedContainerColor = if (calculationMode == "AUTO") Color(0xFFF8FAFC) else Color.White,
                                            unfocusedContainerColor = if (calculationMode == "AUTO") Color(0xFFF8FAFC) else Color.White
                                        ),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .focusRequester(rateFocusRequester),
                                        supportingText = {
                                            if (calculationMode == "AUTO") {
                                                Text("سعر الصرف العام مستورد تلقائياً من الإعدادات", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (isForeignSelected) {
                                            when (calculationMode) {
                                                "NONE" -> {
                                                    foreignExchangeRate = 1.0
                                                    isForeignRateCalculated = false
                                                }
                                                "AUTO" -> {
                                                    foreignExchangeRate = settingsRate
                                                    isForeignRateCalculated = true
                                                }
                                                "MANUAL" -> {
                                                    val cleanRateStr = com.example.ui.screens.habayeb.utils.CurrencyConfig.normalizeDigits(rateInputStr).trim()
                                                    val parsedRate = cleanRateStr.toDoubleOrNull() ?: 0.0
                                                    if (parsedRate <= 0.0) {
                                                        Toast.makeText(context, "يرجى إدخال سعر صرف صحيح", Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    foreignExchangeRate = parsedRate
                                                    isForeignRateCalculated = true
                                                }
                                            }
                                        }
                                        executeSave(if (isLendOperationSelected) "OWED_BY_THEM" else "OWED_TO_THEM")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("دين جديد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        if (isForeignSelected) {
                                            when (calculationMode) {
                                                "NONE" -> {
                                                    foreignExchangeRate = 1.0
                                                    isForeignRateCalculated = false
                                                }
                                                "AUTO" -> {
                                                    foreignExchangeRate = settingsRate
                                                    isForeignRateCalculated = true
                                                }
                                                "MANUAL" -> {
                                                    val cleanRateStr = com.example.ui.screens.habayeb.utils.CurrencyConfig.normalizeDigits(rateInputStr).trim()
                                                    val parsedRate = cleanRateStr.toDoubleOrNull() ?: 0.0
                                                    if (parsedRate <= 0.0) {
                                                        Toast.makeText(context, "يرجى إدخال سعر صرف صحيح", Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    foreignExchangeRate = parsedRate
                                                    isForeignRateCalculated = true
                                                }
                                            }
                                        }
                                        executeSave(if (isLendOperationSelected) "PAYMENT_BY_THEM" else "PAYMENT_TO_THEM")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (isLendOperationSelected) "استلام دفعة" else "سداد دفعة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header (title and back click)
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (editingTransaction != null) "تعديل المعاملة" else "إضافة معاملة جديدة",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeThemeColor
                        )
                        Text(
                            text = "الحساب: ${customer.name.take(15)}${if (customer.name.length > 15) ".." else ""}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(24.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.habayeb_go_back), tint = activeThemeColor, modifier = Modifier.size(16.dp))
                    }
                }

                val dynamicThemeColor = if (isLendOperationSelected) Color(0xFFEF4444) else Color(0xFF10B981)
                val dynamicSubColor = if (isLendOperationSelected) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)

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
                            text = selectedTransactionCurrency,
                            color = dynamicThemeColor,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            fontSize = 14.sp
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                val formattedSelectedDate = remember(dateMillis) {
                    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
                    sdf.format(Date(dateMillis))
                }

                // Detail comments area with leading Calendar symbol
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
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formattedSelectedDate,
                                fontSize = 9.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = { datePickerDialog.show() }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = stringResource(id = R.string.habayeb_tx_date),
                                    tint = dynamicThemeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
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

                Spacer(modifier = Modifier.height(4.dp))
                
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(4.dp))

                // Currency Radio buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val famousCurrencies = listOf(
                        Pair("ر.ي", "ريال يمني"),
                        Pair("$", "دولار"),
                        Pair("ر.س", "ريال سعودي")
                    )
                    famousCurrencies.forEachIndexed { index, (sym, label) ->
                        val isSelected = selectedTransactionCurrency == sym
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val isForeign = sym != currencySymbol
                                    if (isForeign) {
                                        selectedForeignSymbol = sym
                                        isForeignSelected = true
                                        selectedTransactionCurrency = sym
                                    } else {
                                        isForeignSelected = false
                                        foreignExchangeRate = 1.0
                                        isForeignRateCalculated = false
                                        selectedTransactionCurrency = sym
                                    }
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFE91E63) else Color.DarkGray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Custom Radio Button Circle
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (isSelected) Color(0xFFE91E63) else Color.Gray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE91E63))
                                    )
                                }
                            }
                        }
                        if (index < famousCurrencies.size - 1) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Primary Operation Class Selector (Lend Mode vs. Borrow Mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(activeSubColor.copy(alpha = 0.5f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLendOperationSelected) Color(0xFFEF4444) else Color.Transparent)
                            .clickable {
                                isLendOperationSelected = true
                                selectedType = "OWED_BY_THEM"
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "عليه دين لي",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLendOperationSelected) Color.White else Color(0xFFEF4444)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isLendOperationSelected) Color(0xFF10B981) else Color.Transparent)
                            .clickable {
                                isLendOperationSelected = false
                                selectedType = "OWED_TO_THEM"
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "له دين عندي",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isLendOperationSelected) Color.White else Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Sync Option checkbox for Main Mizan Al-Dar Income
                AnimatedVisibility(visible = isLendOperationSelected) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { syncAsMainIncome = !syncAsMainIncome }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = syncAsMainIncome,
                                onCheckedChange = { syncAsMainIncome = it },
                                colors = CheckboxDefaults.colors(checkedColor = dynamicThemeColor),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.habayeb_sync_main_income),
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    enabled = !isSaving,
                    onClick = {
                        val cleanAmountStr = com.example.ui.screens.habayeb.utils.CurrencyConfig.normalizeDigits(amountStr).trim()
                        val amount = cleanAmountStr.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            Toast.makeText(context, context.getString(R.string.habayeb_toast_valid_amount), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!isForeignSelected) {
                            focusManager.clearFocus()
                            softwareKeyboardController?.hide()
                        }
                        showFastActionPopup = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicThemeColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "تأكيد",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
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
