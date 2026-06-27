package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.AppSettings
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.FinanceViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    settings: AppSettings,
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Safely collect reactive app settings with lifecycle-awareness
    val currentSettings by viewModel.settingsState.collectAsStateWithLifecycle()
    val isAlreadyPasscodeEnabled = currentSettings.isPasscodeEnabled

    // State inputs
    var passcode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    var recoveryPhrase by remember { mutableStateOf("") }
    var recoveryHint by remember { mutableStateOf("") }
    var checkAcknowledged by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Toggle password visibility states
    var passcodeVisible by remember { mutableStateOf(false) }
    var confirmPasscodeVisible by remember { mutableStateOf(false) }

    // Focus Requesters
    val passcodeFocus = remember { FocusRequester() }
    val confirmPasscodeFocus = remember { FocusRequester() }
    val recoveryPhraseFocus = remember { FocusRequester() }
    val recoveryHintFocus = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(id = R.string.sec_title), 
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                ),
                modifier = Modifier.border(width = 0.5.dp, color = Color(0xFFE2E8F0))
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .imePadding() // CRITICAL: Keyboard dynamically pushes fields and buttons up perfectly!
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // Header decorative security banner (Creative modern look)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = Color(0xFFE2E8F0), shape = RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = if (isAlreadyPasscodeEnabled) Color(0xFFECFDF5) else Color(0xFFEFF6FF), 
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAlreadyPasscodeEnabled) Icons.Default.VerifiedUser else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isAlreadyPasscodeEnabled) Color(0xFF059669) else EmeraldPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text(
                            text = if (isAlreadyPasscodeEnabled) "الأمان نشط بالكامل" else "تأمين الدار والبيانات",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAlreadyPasscodeEnabled) "تطبيقك محمي بتشفير محلي قوي" else "أنشئ رمز أمان لمنع التطفل والوصول غير المصرح به",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }

            if (!isAlreadyPasscodeEnabled) {
                // SETUP SECURITY FORM (Directly presented to user - No redundant switch required!)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "إعداد رمز القفل الجديد",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.align(Alignment.End)
                        )

                        // PASSCODE INPUT (Stacked vertically for spacious RTL natural look)
                        OutlinedTextField(
                            value = passcode,
                            onValueChange = {
                                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                    passcode = it
                                    if (it.length == 4) {
                                        confirmPasscodeFocus.requestFocus()
                                    }
                                }
                            },
                            label = { Text(stringResource(id = R.string.sec_label_code)) },
                            placeholder = { Text(stringResource(id = R.string.sec_placeholder_code)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = EmeraldPrimary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passcodeVisible = !passcodeVisible }) {
                                    Icon(
                                        imageVector = if (passcodeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            },
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 6.sp
                            ),
                            visualTransformation = if (passcodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { confirmPasscodeFocus.requestFocus() }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .focusRequester(passcodeFocus)
                                .testTag("pin_code_input")
                        )

                        // CONFIRM PASSCODE INPUT
                        OutlinedTextField(
                            value = confirmPasscode,
                            onValueChange = {
                                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                    confirmPasscode = it
                                    if (it.length == 4) {
                                        recoveryPhraseFocus.requestFocus()
                                    }
                                }
                            },
                            label = { Text(stringResource(id = R.string.sec_label_confirm)) },
                            placeholder = { Text(stringResource(id = R.string.sec_placeholder_confirm)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = EmeraldPrimary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { confirmPasscodeVisible = !confirmPasscodeVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasscodeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            },
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 6.sp
                            ),
                            visualTransformation = if (confirmPasscodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { recoveryPhraseFocus.requestFocus() }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .focusRequester(confirmPasscodeFocus)
                                .testTag("pin_code_confirm_input")
                        )

                        Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        Text(
                            text = "استرداد الحساب عند الطوارئ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.align(Alignment.End)
                        )

                        // RECOVERY PHRASE
                        OutlinedTextField(
                            value = recoveryPhrase,
                            onValueChange = { recoveryPhrase = it },
                            label = { Text(stringResource(id = R.string.sec_label_recovery)) },
                            placeholder = { Text(stringResource(id = R.string.sec_placeholder_recovery)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = EmeraldPrimary
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Right,
                                fontSize = 14.sp
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { recoveryHintFocus.requestFocus() }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(recoveryPhraseFocus)
                                .testTag("recovery_phrase_input")
                        )

                        // RECOVERY HINT
                        OutlinedTextField(
                            value = recoveryHint,
                            onValueChange = { recoveryHint = it },
                            label = { Text(stringResource(id = R.string.sec_label_hint)) },
                            placeholder = { Text(stringResource(id = R.string.sec_placeholder_hint)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = EmeraldPrimary
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Right,
                                fontSize = 14.sp
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(recoveryHintFocus)
                                .testTag("recovery_hint_input")
                        )

                        // ACK CHECKBOX (Beautiful custom highlight row)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFEF3C7).copy(alpha = 0.3f))
                                .clickable { checkAcknowledged = !checkAcknowledged }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.sec_checkbox_ack),
                                fontSize = 12.sp,
                                color = Color(0xFF78350F),
                                textAlign = TextAlign.Right,
                                lineHeight = 18.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            )
                            Checkbox(
                                checked = checkAcknowledged,
                                onCheckedChange = { checkAcknowledged = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = EmeraldPrimary,
                                    uncheckedColor = Color(0xFFD97706)
                                )
                            )
                        }

                        val isValid = passcode.length == 4 && 
                                      confirmPasscode == passcode && 
                                      recoveryPhrase.isNotBlank() && 
                                      checkAcknowledged &&
                                      !isSaving

                        // SAVE & ACTIVATE BUTTON (Always scrollable and visible in card context, never overlaps)
                        Button(
                            onClick = {
                                if (isValid) {
                                    isSaving = true
                                    coroutineScope.launch(Dispatchers.Default) {
                                        val pHash = com.example.domain.HashUtils.hashString(passcode)
                                        val rHash = com.example.domain.HashUtils.hashString(recoveryPhrase.trim())
                                        val updated = currentSettings.copy(
                                            isPasscodeEnabled = true,
                                            passcodeHash = pHash,
                                            recoveryPhraseHash = rHash,
                                            recoveryHint = recoveryHint.trim().takeIf { it.isNotBlank() }
                                        )
                                        viewModel.saveSettings(updated)
                                        
                                        withContext(Dispatchers.Main) {
                                            isSaving = false
                                            Toast.makeText(context, context.getString(R.string.sec_toast_enabled_success), Toast.LENGTH_SHORT).show()
                                            onBack()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                disabledContainerColor = Color(0xFFCBD5E1)
                            ),
                            enabled = isValid,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("security_save_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = stringResource(id = R.string.sec_btn_activate),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // SECURITY ALREADY ACTIVE VIEW (Gorgeous polished control center)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Big glowing emerald shield
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color(0xFFD1FAE5), CircleShape)
                                .border(width = 2.dp, color = Color(0xFF34D399), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Text(
                            text = "تطبيقك محمي بنجاح 🔒",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF065F46)
                        )

                        Text(
                            text = stringResource(id = R.string.sec_card_desc_warning),
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!currentSettings.recoveryHint.isNullOrBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(id = R.string.sec_hint_pattern, currentSettings.recoveryHint.orEmpty()),
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        }

                        // Copy recovery button
                        Button(
                            onClick = {
                                if (!currentSettings.recoveryPhraseHash.isNullOrBlank()) {
                                    clipboardManager.setText(AnnotatedString(currentSettings.recoveryPhraseHash!!))
                                    Toast.makeText(context, context.getString(R.string.sec_toast_copied), Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy, 
                                    contentDescription = null, 
                                    tint = EmeraldPrimary, 
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.sec_btn_copy), 
                                    color = EmeraldPrimary, 
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // DEACTIVATE SECURITY BUTTON (Outline styling - highly modern & protective)
                        OutlinedButton(
                            onClick = {
                                val updated = currentSettings.copy(
                                    isPasscodeEnabled = false,
                                    passcodeHash = null,
                                    recoveryPhraseHash = null,
                                    recoveryHint = null
                                )
                                viewModel.saveSettings(updated)
                                Toast.makeText(context, context.getString(R.string.sec_toast_disabled), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFECACA)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen, 
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تعطيل قفل التطبيق والأمان 🔓",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
