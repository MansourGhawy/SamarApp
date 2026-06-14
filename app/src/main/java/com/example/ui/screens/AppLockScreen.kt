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
import androidx.compose.material.icons.filled.Pin
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettings
import com.example.domain.HashUtils
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.EmeraldPrimary

@Composable
fun AppLockScreen(
    settings: AppSettings,
    onUnlockSuccess: () -> Unit,
    onUnlockBypassedAndDisabled: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var enteredPasscode by remember { mutableStateOf("") }
    var showRecoveryView by remember { mutableStateOf(false) }
    var recoveryPhraseInput by remember { mutableStateOf("") }
    var showHintText by remember { mutableStateOf(false) }
    val recoveryHint = settings.recoveryHint

    // Deluxe Deep-Olive Olive/Emerald Atmosphere theme (زيتي داكن راقي)
    val elegantOliveDark = Color(0xFF0D1410)

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
                            contentDescription = "استرداد رمز المرور",
                            tint = CoralAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "استرداد التطبيق المغلق 🔓",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "أدخل عبارة الاسترداد لإعادة تعيين الرمز",
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
                        label = { Text("عبارة الاسترداد المحفوظة", color = Color.White.copy(alpha = 0.6f)) },
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
                                text = if (showHintText) "إخفاء تلميح الذاكرة" else "عرض تلميح الذاكرة 💡",
                                color = Color(0xFFF59E0B),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    AnimatedVisibility(visible = showHintText && !recoveryHint.isNullOrBlank()) {
                        Text(
                            text = "💡 تلميح الذاكرة: $recoveryHint",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val hashed = HashUtils.hashString(recoveryPhraseInput.trim())
                            if (hashed == settings.recoveryPhraseHash) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "تم مطابقة عبارة الاسترداد! تم تعطيل القفل بسلام ✔️", Toast.LENGTH_LONG).show()
                                onUnlockBypassedAndDisabled()
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "عبارة الاسترداد خاطئة أو غير متطابقة! ❌", Toast.LENGTH_SHORT).show()
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
                            text = "تحقق وإلغاء القفل الدائم 🔓",
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
                                contentDescription = "عودة للوحة الأرقام",
                                tint = Color.LightGray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "العودة للوحة الرمز السري",
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
                                contentDescription = "التطبيق مغلق",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "السجل مغلق ⚖️",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "الرجاء إدخال الرمز السري المكون من 4 أرقام",
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
                            val row1 = listOf("1", "2", "3")
                            val row2 = listOf("4", "5", "6")
                            val row3 = listOf("7", "8", "9")

                            KeypadRow(row = row1) { key ->
                                handleKeyInput(key, enteredPasscode) { enteredPasscode = it }
                                if (enteredPasscode.length == 4) {
                                    checkPasscode(enteredPasscode, settings, onUnlockSuccess, haptic, context) {
                                        enteredPasscode = ""
                                    }
                                }
                            }

                            KeypadRow(row = row2) { key ->
                                handleKeyInput(key, enteredPasscode) { enteredPasscode = it }
                                if (enteredPasscode.length == 4) {
                                    checkPasscode(enteredPasscode, settings, onUnlockSuccess, haptic, context) {
                                        enteredPasscode = ""
                                    }
                                }
                            }

                            KeypadRow(row = row3) { key ->
                                handleKeyInput(key, enteredPasscode) { enteredPasscode = it }
                                if (enteredPasscode.length == 4) {
                                    checkPasscode(enteredPasscode, settings, onUnlockSuccess, haptic, context) {
                                        enteredPasscode = ""
                                    }
                                }
                            }

                            // Last row with Empty Spacer / "0" / Delete/Backspace (حذف) [LTR Direction]
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Column 1: Blank Spacer
                                Box(modifier = Modifier.size(68.dp))

                                // Column 2: Number "0"
                                KeypadButton(text = "0", isFunctional = false) {
                                    handleKeyInput("0", enteredPasscode) { enteredPasscode = it }
                                    if (enteredPasscode.length == 4) {
                                        checkPasscode(enteredPasscode, settings, onUnlockSuccess, haptic, context) {
                                            enteredPasscode = ""
                                        }
                                    }
                                }

                                // Column 3: Delete/Backspace
                                KeypadButton(text = "حذف", isFunctional = true) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (enteredPasscode.isNotEmpty()) {
                                        enteredPasscode = enteredPasscode.dropLast(1)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Recovery Link "نسيت الرمز؟"
                            Text(
                                text = "نسيت الرمز؟ 🔑",
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

private fun checkPasscode(
    passcode: String,
    settings: AppSettings,
    onSuccess: () -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: android.content.Context,
    onFail: () -> Unit
) {
    val hashed = HashUtils.hashString(passcode)
    if (hashed == settings.passcodeHash) {
        onSuccess()
    } else {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.makeText(context, "الرمز المدخل غير صحيح! ❌", Toast.LENGTH_SHORT).show()
        onFail()
    }
}
