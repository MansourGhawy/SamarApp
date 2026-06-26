package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.HashUtils
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.R
import com.example.ui.viewmodel.FinanceViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun AppLockScreen(
    viewModel: FinanceViewModel,
    onUnlockSuccess: () -> Unit,
    onUnlockBypassedAndDisabled: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    // Smooth, lifecycle-aware StateFlow collection
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    
    var enteredPasscode by remember { mutableStateOf("") }
    var isCheckingPasscode by remember { mutableStateOf(false) }
    var showRecoveryView by remember { mutableStateOf(false) }
    var recoveryPhraseInput by remember { mutableStateOf("") }
    var showHintText by remember { mutableStateOf(false) }
    val recoveryHint = settings.recoveryHint

    // Cache the keypad row items to prevent recreation on each keystroke recomposition
    val row1 = remember { listOf("1", "2", "3") }
    val row2 = remember { listOf("4", "5", "6") }
    val row3 = remember { listOf("7", "8", "9") }

    // Deluxe Deep-Olive Olive/Emerald Atmosphere theme (زيتي داكن راقي)
    val elegantOliveDark = Color(0xFF0D1410)

    // Optimized, non-blocking asynchronous PIN validator on a background thread (Dispatchers.Default)
    val onKeyPress = remember(enteredPasscode, isCheckingPasscode, settings.passcodeHash) {
        { key: String ->
            if (!isCheckingPasscode && enteredPasscode.length < 4) {
                val nextPasscode = enteredPasscode + key
                enteredPasscode = nextPasscode
                if (nextPasscode.length == 4) {
                    isCheckingPasscode = true
                    scope.launch {
                        val isMatch = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            val hashed = HashUtils.hashString(nextPasscode)
                            com.example.domain.DatabaseSecurityGuard.secureEqual(hashed, settings.passcodeHash.orEmpty())
                        }
                        if (isMatch) {
                            onUnlockSuccess()
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val fallbackMsg = context.getString(R.string.lock_incorrect_pin)
                            Toast.makeText(context, fallbackMsg, Toast.LENGTH_SHORT).show()
                            enteredPasscode = ""
                            isCheckingPasscode = false
                        }
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = elegantOliveDark
    ) {
        AnimatedContent(
            targetState = showRecoveryView,
            transitionSpec = {
                slideInHorizontally { width -> if (targetState) width else -width } + fadeIn() togetherWith
                slideOutHorizontally { width -> if (targetState) -width else width } + fadeOut()
            },
            label = "ScreenType"
        ) { isRecovery ->
            if (isRecovery) {
                // Recovery Phrase View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(CoralAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = stringResource(id = R.string.lock_recover_account),
                            tint = CoralAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(id = R.string.lock_recovery_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(id = R.string.lock_recovery_desc),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    OutlinedTextField(
                        value = recoveryPhraseInput,
                        onValueChange = { recoveryPhraseInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(id = R.string.lock_recovery_phrase_hint), color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoralAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = CoralAccent,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                    )

                    if (!recoveryHint.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .clickable { showHintText = !showHintText },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showHintText) stringResource(id = R.string.lock_hide_hint) else stringResource(id = R.string.lock_show_hint),
                                color = Color(0xFFF59E0B),
                                style = MaterialTheme.typography.bodyMedium
                             )
                        }
                    }

                    AnimatedVisibility(visible = showHintText && !recoveryHint.isNullOrBlank()) {
                        Text(
                            text = stringResource(id = R.string.lock_hint_prefix, recoveryHint ?: ""),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val isCorrect = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    val hashed = HashUtils.hashString(recoveryPhraseInput.trim())
                                    com.example.domain.DatabaseSecurityGuard.secureEqual(hashed, settings.recoveryPhraseHash)
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isCorrect) {
                                    val successMsg = context.getString(R.string.lock_recovery_matched)
                                    Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                                    onUnlockBypassedAndDisabled()
                                } else {
                                    val errorMsg = context.getString(R.string.lock_recovery_wrong)
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralAccent),
                        shape = RoundedCornerShape(16.dp),
                        enabled = recoveryPhraseInput.isNotBlank()
                    ) {
                        Text(
                            text = stringResource(id = R.string.lock_verify_and_unlock),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            recoveryPhraseInput = ""
                            showRecoveryView = false
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = stringResource(id = R.string.lock_back_to_keypad_desc),
                                tint = Color.LightGray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.lock_return_to_keypad),
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                // Passcode Custom Keypad View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Area
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(id = R.string.lock_app_locked_desc),
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(id = R.string.lock_ledger_locked),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(id = R.string.lock_enter_pin_prompt),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }

                    // 4 Round Indicators
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 4) {
                                val filled = enteredPasscode.length > i
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) EmeraldPrimary else Color.White.copy(alpha = 0.2f))
                                        .border(
                                            width = 1.2.dp,
                                            color = if (filled) EmeraldPrimary else Color.White.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }

                    // Keypad Area
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            KeypadRow(row = row1, onKeyClick = onKeyPress)
                            KeypadRow(row = row2, onKeyClick = onKeyPress)
                            KeypadRow(row = row3, onKeyClick = onKeyPress)

                            // Last row with Empty Spacer / "0" / Delete/Backspace (حذف) [LTR Direction]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Column 1: Blank Spacer
                                Box(modifier = Modifier.size(68.dp))

                                // Column 2: Number "0"
                                KeypadButton(text = "0", isFunctional = false) {
                                    onKeyPress("0")
                                }

                                // Column 3: Delete/Backspace
                                KeypadButton(text = stringResource(id = R.string.lock_delete_btn), isFunctional = true) {
                                    if (!isCheckingPasscode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (enteredPasscode.isNotEmpty()) {
                                            enteredPasscode = enteredPasscode.dropLast(1)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Recovery Link "نسيت الرمز؟"
                            Text(
                                text = stringResource(id = R.string.lock_forgot_pin),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralAccent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showRecoveryView = true
                                    }
                                    .padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadRow(row: List<String>, onKeyClick: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        row.forEach { digit ->
            KeypadButton(text = digit, isFunctional = false, onClick = { onKeyClick(digit) })
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    isFunctional: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val buttonBgColor = if (isFunctional) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f)
    val buttonTextColor = if (isFunctional) Color.LightGray else Color.White
    val buttonTextSize = if (isFunctional) 12.sp else 22.sp
    val buttonFontWeight = if (isFunctional) FontWeight.SemiBold else FontWeight.Bold

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(buttonBgColor)
            .clickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = buttonTextColor,
            fontSize = buttonTextSize,
            fontWeight = buttonFontWeight,
            textAlign = TextAlign.Center
        )
    }
}

private fun handleKeyInput(key: String, current: String, update: (String) -> Unit) {
    if (current.length < 4) {
        update(current + key)
    }
}


