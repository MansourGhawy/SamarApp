package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.ui.theme.CoralAccent
import com.example.ui.viewmodel.FinanceViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityBottomSheet(
    settings: AppSettings,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Safely collect reactive app settings with lifecycle-awareness
    val currentSettings by viewModel.settingsState.collectAsStateWithLifecycle()
    
    var isEnabled by remember(currentSettings.isPasscodeEnabled) { mutableStateOf(currentSettings.isPasscodeEnabled) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Inputs
    var passcode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    var recoveryPhrase by remember { mutableStateOf("") }
    var recoveryHint by remember { mutableStateOf("") }
    var checkAcknowledged by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Symmetrical RTL Header layout: Switch on far left, Icon and title on far right
                // Switch on the left
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            // Turn off lock
                            val updated = currentSettings.copy(
                                isPasscodeEnabled = false,
                                passcodeHash = null,
                                recoveryPhraseHash = null,
                                recoveryHint = null
                            )
                            viewModel.saveSettings(updated)
                            isEnabled = false
                            Toast.makeText(context, context.getString(R.string.sec_toast_disabled), Toast.LENGTH_SHORT).show()
                        } else {
                            isEnabled = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = EmeraldPrimary,
                        uncheckedBorderColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("security_lock_switch")
                )

                // Title and Icon on the right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.sec_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155),
                        textAlign = TextAlign.Right
                    )
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Setup Details Row
            AnimatedVisibility(
                visible = isEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val passcodeFocus = remember { FocusRequester() }
                val confirmPasscodeFocus = remember { FocusRequester() }
                val recoveryPhraseFocus = remember { FocusRequester() }
                val recoveryHintFocus = remember { FocusRequester() }
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

                LaunchedEffect(Unit) {
                    try {
                        passcodeFocus.requestFocus()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!currentSettings.isPasscodeEnabled) {
                        // PIN Fields side-by-side (RTL Order: Confirm then code)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = confirmPasscode,
                                onValueChange = {
                                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                        confirmPasscode = it
                                    }
                                },
                                label = { Text(stringResource(id = R.string.sec_label_confirm)) },
                                placeholder = { Text(stringResource(id = R.string.sec_placeholder_confirm)) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { recoveryPhraseFocus.requestFocus() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .focusRequester(confirmPasscodeFocus)
                                    .testTag("pin_code_confirm_input")
                            )

                            OutlinedTextField(
                                value = passcode,
                                onValueChange = {
                                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                        passcode = it
                                    }
                                },
                                label = { Text(stringResource(id = R.string.sec_label_code)) },
                                placeholder = { Text(stringResource(id = R.string.sec_placeholder_code)) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { confirmPasscodeFocus.requestFocus() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .focusRequester(passcodeFocus)
                                    .testTag("pin_code_input")
                            )
                        }

                        // Recovery inputs
                        OutlinedTextField(
                            value = recoveryPhrase,
                            onValueChange = { recoveryPhrase = it },
                            label = { Text(stringResource(id = R.string.sec_label_recovery)) },
                            placeholder = { Text(stringResource(id = R.string.sec_placeholder_recovery)) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Right,
                                fontSize = 13.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { recoveryHintFocus.requestFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(recoveryPhraseFocus)
                                .testTag("recovery_phrase_input")
                        )

                        OutlinedTextField(
                            value = recoveryHint,
                            onValueChange = { recoveryHint = it },
                            label = { Text(stringResource(id = R.string.sec_label_hint)) },
                            placeholder = { Text(stringResource(id = R.string.sec_placeholder_hint)) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Right,
                                fontSize = 13.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(recoveryHintFocus)
                                .testTag("recovery_hint_input")
                        )

                        // Checkbox (RTL layout)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checkAcknowledged = !checkAcknowledged }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.sec_checkbox_ack),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Right,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                            )
                            Checkbox(
                                checked = checkAcknowledged,
                                onCheckedChange = { checkAcknowledged = it },
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Submit Button
                        val isValid = passcode.length == 4 && 
                                      confirmPasscode == passcode && 
                                      recoveryPhrase.isNotBlank() && 
                                      checkAcknowledged &&
                                      !isSaving

                        Button(
                            onClick = {
                                if (isValid) {
                                    isSaving = true
                                    coroutineScope.launch(Dispatchers.Default) {
                                        // Compute securely on background thread without blocking the UI
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
                                            onDismiss()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            enabled = isValid,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("security_save_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = stringResource(id = R.string.sec_btn_activate),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        // ALREADY ENABLED STATE
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF1F2)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Title with lock icon
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.sec_card_title_warning),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE11D48),
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Text(
                                    text = stringResource(id = R.string.sec_card_desc_warning),
                                    fontSize = 11.sp,
                                    color = Color(0xFFBE123C),
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (!currentSettings.recoveryHint.isNullOrBlank()) {
                                    Text(
                                        text = stringResource(id = R.string.sec_hint_pattern, currentSettings.recoveryHint.orEmpty()),
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569),
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(context.getString(R.string.sec_phrase_copylabel)))
                                            Toast.makeText(context, context.getString(R.string.sec_toast_copied), Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = Color(0xFFE11D48),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.sec_btn_copy),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE11D48)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
