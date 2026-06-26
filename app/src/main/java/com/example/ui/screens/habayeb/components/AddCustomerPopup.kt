package com.example.ui.screens.habayeb.components

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.HabayebCustomer
import com.example.domain.StringUtils.getContactDetails
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@Composable
fun AddCustomerPopup(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    activeThemeColor: Color,
    activeSubColor: Color
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var nameStr by rememberSaveable { mutableStateOf("") }
    var phoneStr by rememberSaveable { mutableStateOf("") }
    var notesStr by rememberSaveable { mutableStateOf("") }
    
    // First atomic transaction fields
    var initialAmountStr by rememberSaveable { mutableStateOf("") }
    var initialType by rememberSaveable { mutableStateOf("OWED_BY_THEM") } // OWED_BY_THEM (عليه لي) or OWED_TO_THEM (له عندي)

    var showCalculator by rememberSaveable { mutableStateOf(false) }
    var isSavingCustomer by rememberSaveable { mutableStateOf(false) }
    var showConfirmPopup by remember { mutableStateOf(false) }

    // Date Picker state
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val dateStr = remember(selectedCalendar) {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        sdf.format(selectedCalendar.time)
    }

    val year = selectedCalendar.get(Calendar.YEAR)
    val month = selectedCalendar.get(Calendar.MONTH)
    val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, selectedDayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedCalendar = newCal
            },
            year,
            month,
            day
        )
    }

    // Auto-focus & keyboard navigation setup
    val focusRequester = remember { FocusRequester() }
    val initialAmountFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        try {
            focusRequester.requestFocus()
            softwareKeyboardController?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Storage integration launcher
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                notesStr = if (notesStr.isBlank()) {
                    context.getString(R.string.habayeb_attachment_included)
                } else {
                    notesStr.trim() + " " + context.getString(R.string.habayeb_attachment_included)
                }
                Toast.makeText(context, context.getString(R.string.habayeb_toast_attachment_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.habayeb_toast_attachment_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            storageLauncher.launch("image/*")
        } else {
            Toast.makeText(context, context.getString(R.string.habayeb_toast_storage_permission), Toast.LENGTH_SHORT).show()
        }
    }

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri ->
        contactUri?.let { uri ->
            val details = getContactDetails(context, uri)
            if (details != null) {
                nameStr = details.first
                phoneStr = details.second
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, context.getString(R.string.habayeb_toast_contacts_permission), Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp) // Snug vertical spacing
                ) {
                    Text(
                        text = stringResource(id = R.string.habayeb_add_account),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = activeThemeColor
                    )

                    // 1. Name field
                    OutlinedTextField(
                        value = nameStr,
                        onValueChange = { nameStr = it },
                        label = { Text(stringResource(id = R.string.habayeb_account_name), fontSize = 13.sp) },
                        placeholder = { Text(stringResource(id = R.string.habayeb_edit_name_desc), fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { initialAmountFocusRequester.requestFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                        )
                    )

                    // 2. Amount field with calculator trailingIcon
                    OutlinedTextField(
                        value = initialAmountStr,
                        onValueChange = { initialAmountStr = it },
                        label = { Text(stringResource(id = R.string.habayeb_amount), fontSize = 13.sp) },
                        placeholder = { Text("0.0", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(initialAmountFocusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showCalculator = true }) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = stringResource(id = R.string.habayeb_calculator),
                                    tint = activeThemeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )

                    // 3. Status switcher buttons: مستحقات لي vs التزامات علي
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (initialType == "OWED_BY_THEM") Color(0xFFEF4444) else Color.Transparent)
                                .clickable {
                                    initialType = "OWED_BY_THEM"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.habayeb_register_owed_by),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (initialType == "OWED_BY_THEM") Color.White else Color(0xFF475569)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (initialType == "OWED_TO_THEM") Color(0xFF10B981) else Color.Transparent)
                                .clickable {
                                    initialType = "OWED_TO_THEM"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.habayeb_register_owed_to),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (initialType == "OWED_TO_THEM") Color.White else Color(0xFF475569)
                            )
                        }
                    }

                    // 4. Interactive Date Picker Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(activeSubColor.copy(alpha = 0.5f))
                            .clickable { datePickerDialog.show() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = stringResource(id = R.string.habayeb_tx_date),
                                tint = activeThemeColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.habayeb_tx_date),
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = dateStr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeThemeColor
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Footer Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                        }

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 1500f),
                            label = "SaveBtnScale"
                        )
                        val saveBtnColor = if (initialType == "OWED_BY_THEM") Color(0xFFEF4444) else Color(0xFF10B981)

                        Button(
                            enabled = !isSavingCustomer,
                            onClick = {
                                if (isSavingCustomer) return@Button
                                isSavingCustomer = true

                                if (nameStr.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.habayeb_toast_enter_name), Toast.LENGTH_SHORT).show()
                                    isSavingCustomer = false
                                    return@Button
                                }
                                val initialAmount = initialAmountStr.toDoubleOrNull() ?: 0.0
                                if (initialAmount < 0.0) {
                                    Toast.makeText(context, context.getString(R.string.habayeb_toast_initial_amount_negative), Toast.LENGTH_SHORT).show()
                                    isSavingCustomer = false
                                    return@Button
                                }

                                showConfirmPopup = true
                                isSavingCustomer = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = saveBtnColor),
                            shape = RoundedCornerShape(12.dp),
                            interactionSource = interactionSource,
                            modifier = Modifier.weight(1.2f).graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            Text(stringResource(id = R.string.habayeb_confirm_save))
                        }
                    }
                }
            }
        }
    }

    if (showConfirmPopup) {
        val secondStepNotesFocusRequester = remember { FocusRequester() }
        Dialog(onDismissRequest = { showConfirmPopup = false }) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding()
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.habayeb_last_step),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = activeThemeColor
                        )

                        OutlinedTextField(
                            value = phoneStr,
                            onValueChange = { phoneStr = it },
                            label = { Text(stringResource(id = R.string.habayeb_phone_optional), fontSize = 13.sp) },
                            placeholder = { Text(stringResource(id = R.string.habayeb_contact_picker), fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { secondStepNotesFocusRequester.requestFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = activeThemeColor,
                                focusedLabelColor = activeThemeColor,
                                cursorColor = activeThemeColor
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        contactPickerLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Contacts,
                                        contentDescription = stringResource(id = R.string.habayeb_contact_picker),
                                        tint = activeThemeColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        )

                        Column {
                            OutlinedTextField(
                                value = notesStr,
                                onValueChange = { notesStr = it },
                                label = { Text(stringResource(id = R.string.habayeb_details_required), fontSize = 13.sp) },
                                placeholder = { Text(stringResource(id = R.string.habayeb_starting_balance), fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                isError = notesStr.isBlank(),
                                modifier = Modifier.fillMaxWidth().focusRequester(secondStepNotesFocusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { 
                                        if (notesStr.isNotBlank()) {
                                            focusManager.clearFocus()
                                            isSavingCustomer = true
                                            val initialAmount = initialAmountStr.toDoubleOrNull() ?: 0.0
                                            val transactionTimestamp = selectedCalendar.timeInMillis / 1000

                                            val newCustomer = HabayebCustomer(
                                                id = "cust_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                                                name = nameStr.trim(),
                                                phone = phoneStr.trim(),
                                                notes = notesStr.trim(),
                                                createdAt = transactionTimestamp
                                            )
                                            viewModel.saveHabayebCustomer(newCustomer, initialAmount, initialType, transactionTimestamp, notesStr.trim())
                                            Toast.makeText(context, context.getString(R.string.habayeb_toast_save_success), Toast.LENGTH_SHORT).show()
                                            showConfirmPopup = false
                                            onDismiss()
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeThemeColor,
                                    focusedLabelColor = activeThemeColor,
                                    cursorColor = activeThemeColor
                                )
                            )
                            if (notesStr.isBlank()) {
                                Text(
                                    text = stringResource(id = R.string.habayeb_required_field),
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfirmPopup = false },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(id = R.string.habayeb_go_back), color = Color.Gray)
                            }

                            Button(
                                enabled = notesStr.isNotBlank() && !isSavingCustomer,
                                onClick = {
                                    isSavingCustomer = true
                                    val initialAmount = initialAmountStr.toDoubleOrNull() ?: 0.0
                                    val transactionTimestamp = selectedCalendar.timeInMillis / 1000

                                    val newCustomer = HabayebCustomer(
                                        id = "cust_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                                        name = nameStr.trim(),
                                        phone = phoneStr.trim(),
                                        notes = notesStr.trim(),
                                        createdAt = transactionTimestamp
                                    )
                                    viewModel.saveHabayebCustomer(newCustomer, initialAmount, initialType, transactionTimestamp, notesStr.trim())
                                    Toast.makeText(context, context.getString(R.string.habayeb_toast_save_success), Toast.LENGTH_SHORT).show()
                                    showConfirmPopup = false
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(id = R.string.habayeb_save_final))
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
                initialAmountStr = value.toString()
                showCalculator = false
            },
            activeThemeColor = activeThemeColor,
            activeSubColor = activeSubColor
        )
    }
}
