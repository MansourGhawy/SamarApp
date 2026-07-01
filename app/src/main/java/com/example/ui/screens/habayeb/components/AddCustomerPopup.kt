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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.entities.HabayebCustomer
import com.example.domain.StringUtils.getContactDetails
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.screens.CalculatorDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@Composable
fun AddCustomerPopup(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onCustomerAdded: () -> Unit = {},
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

    val secondStepNotesFocusRequester = remember { FocusRequester() }
    val secondStepPhoneFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showConfirmPopup) {
        if (showConfirmPopup) {
            kotlinx.coroutines.delay(350)
            try {
                secondStepNotesFocusRequester.requestFocus()
                softwareKeyboardController?.show()
            } catch (e: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = { if (showConfirmPopup) showConfirmPopup = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .fillMaxWidth(0.9f)
                    .padding(8.dp)
            ) {
                androidx.compose.animation.AnimatedContent(targetState = showConfirmPopup, label = "StepTransition") { isStepTwo ->
                    if (!isStepTwo) {
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
                                label = { Text("اسم الحساب", fontSize = 11.sp) },
                                placeholder = { Text(stringResource(id = R.string.habayeb_edit_name_desc), fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
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

                            // 2. Amount field with calculator trailingIcon and Date picker leadingIcon
                            OutlinedTextField(
                                value = initialAmountStr,
                                onValueChange = { initialAmountStr = it },
                                label = { Text("رصيد أولي (اختياري)", fontSize = 11.sp) },
                                placeholder = { Text("0.0", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = {
                                    if (nameStr.isNotBlank()) {
                                        showConfirmPopup = true
                                    } else {
                                        focusManager.clearFocus()
                                    }
                                }),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(initialAmountFocusRequester),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeThemeColor,
                                    focusedLabelColor = activeThemeColor,
                                    cursorColor = activeThemeColor,
                                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                                ),
                                leadingIcon = {
                                    IconButton(onClick = { showCalculator = true }, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Calculate,
                                            contentDescription = stringResource(id = R.string.habayeb_calculator),
                                            tint = activeThemeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = dateStr,
                                            fontSize = 9.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                val year = selectedCalendar.get(Calendar.YEAR)
                                                val month = selectedCalendar.get(Calendar.MONTH)
                                                val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)
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
                                                ).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = stringResource(id = R.string.habayeb_tx_date),
                                                tint = activeThemeColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                }
                            )

                            // 3. Status switcher buttons: مستحقات لي vs التزامات علي
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
                                        .background(if (initialType == "OWED_BY_THEM") Color(0xFFEF4444) else Color.Transparent)
                                        .clickable {
                                            initialType = "OWED_BY_THEM"
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "عليه دين لي",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (initialType == "OWED_BY_THEM") Color.White else Color(0xFFEF4444)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (initialType == "OWED_TO_THEM") Color(0xFF10B981) else Color.Transparent)
                                        .clickable {
                                            initialType = "OWED_TO_THEM"
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "له دين عندي",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (initialType == "OWED_TO_THEM") Color.White else Color(0xFF10B981)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Footer Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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

                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .navigationBarsPadding()
                                .imePadding()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.habayeb_last_step),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = activeThemeColor
                            )

                            // 1. Details/Notes field (Required - First now!)
                            Column {
                                OutlinedTextField(
                                    value = notesStr,
                                    onValueChange = { notesStr = it },
                                    label = { Text(stringResource(id = R.string.habayeb_details_required), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    placeholder = { Text(stringResource(id = R.string.habayeb_starting_balance), fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    isError = notesStr.isBlank(),
                                    modifier = Modifier.fillMaxWidth().focusRequester(secondStepNotesFocusRequester),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(
                                        onNext = { secondStepPhoneFocusRequester.requestFocus() }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = activeThemeColor,
                                        focusedLabelColor = activeThemeColor,
                                        cursorColor = activeThemeColor,
                                        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
                                    )
                                )
                                if (notesStr.isBlank()) {
                                    Text(
                                        text = stringResource(id = R.string.habayeb_required_field),
                                        color = Color.Red,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                    )
                                }
                            }

                            // 2. Phone field (Optional - Second now!)
                            OutlinedTextField(
                                value = phoneStr,
                                onValueChange = { phoneStr = it },
                                label = { Text(stringResource(id = R.string.habayeb_phone_optional), fontSize = 11.sp) },
                                placeholder = { Text(stringResource(id = R.string.habayeb_contact_picker), fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
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
                                            onCustomerAdded()
                                            onDismiss()
                                        } else {
                                            focusManager.clearFocus()
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(secondStepPhoneFocusRequester),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeThemeColor,
                                    focusedLabelColor = activeThemeColor,
                                    cursorColor = activeThemeColor,
                                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f)
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
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
                                        onCustomerAdded()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(id = R.string.habayeb_save_final))
                                }
                                
                                OutlinedButton(
                                    onClick = { showConfirmPopup = false },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(id = R.string.habayeb_go_back), color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCalculator) {
        CalculatorDialog(
            onDismiss = { showCalculator = false },
            onValueConfirmed = { value ->
                initialAmountStr = value.toString()
                showCalculator = false
            },
            activeThemeColor = activeThemeColor,
            activeSubColor = activeSubColor
        )
    }
}
