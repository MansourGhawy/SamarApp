package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.sec_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8FAFC)
                )
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            if (!checked) {
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.sec_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            textAlign = TextAlign.Right
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFEFF6FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

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
                        if(!currentSettings.isPasscodeEnabled) {
                            passcodeFocus.requestFocus()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!currentSettings.isPasscodeEnabled) {
                        // Registration Form
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 4.sp
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { recoveryPhraseFocus.requestFocus() }),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmeraldPrimary,
                                            unfocusedBorderColor = Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(64.dp)
                                            .focusRequester(confirmPasscodeFocus)
                                            .testTag("pin_code_confirm_input")
                                    )

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
                                        textStyle = LocalTextStyle.current.copy(
                                            textAlign = TextAlign.Center,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 4.sp
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { confirmPasscodeFocus.requestFocus() }),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmeraldPrimary,
                                            unfocusedBorderColor = Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(64.dp)
                                            .focusRequester(passcodeFocus)
                                            .testTag("pin_code_input")
                                    )
                                }

                                OutlinedTextField(
                                    value = recoveryPhrase,
                                    onValueChange = { recoveryPhrase = it },
                                    label = { Text(stringResource(id = R.string.sec_label_recovery)) },
                                    placeholder = { Text(stringResource(id = R.string.sec_placeholder_recovery)) },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        textAlign = TextAlign.Right,
                                        fontSize = 14.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { recoveryHintFocus.requestFocus() }),
                                    shape = RoundedCornerShape(12.dp),
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
                                        fontSize = 14.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldPrimary,
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(recoveryHintFocus)
                                        .testTag("recovery_hint_input")
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { checkAcknowledged = !checkAcknowledged }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.sec_checkbox_ack),
                                        fontSize = 13.sp,
                                        color = Color(0xFF475569),
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 12.dp)
                                    )
                                    Checkbox(
                                        checked = checkAcknowledged,
                                        onCheckedChange = { checkAcknowledged = it },
                                        colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                                    )
                                }

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
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    enabled = isValid,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
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
                        // ALREADY ENABLED STATE
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.sec_card_title_warning),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626),
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                Text(
                                    text = stringResource(id = R.string.sec_card_desc_warning),
                                    fontSize = 13.sp,
                                    color = Color(0xFF991B1B),
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (!currentSettings.recoveryHint.isNullOrBlank()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.sec_hint_pattern, currentSettings.recoveryHint.orEmpty()),
                                            fontSize = 13.sp,
                                            color = Color(0xFF475569),
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            if (!currentSettings.recoveryPhraseHash.isNullOrBlank()) {
                                                clipboardManager.setText(AnnotatedString(currentSettings.recoveryPhraseHash!!))
                                                Toast.makeText(context, context.getString(R.string.sec_toast_copied), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text(stringResource(id = R.string.sec_btn_copy), color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
