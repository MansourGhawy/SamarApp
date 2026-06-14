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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettings
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.CoralAccent
import com.example.ui.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityBottomSheet(
    settings: AppSettings,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isEnabled by remember { mutableStateOf(settings.isPasscodeEnabled) }
    
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "الأمان وقفل التطبيق",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                }
                
                // Switch
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            // Turn off lock
                            val updated = settings.copy(
                                isPasscodeEnabled = false,
                                passcodeHash = null,
                                recoveryPhraseHash = null,
                                recoveryHint = null
                            )
                            viewModel.saveSettings(updated)
                            isEnabled = false
                            Toast.makeText(context, "تم إيقاف قفل التطبيق والأمن 🔓", Toast.LENGTH_SHORT).show()
                        } else {
                            isEnabled = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = EmeraldPrimary,
                        uncheckedBorderColor = Color(0xFF94A3B8)
                    )
                )
            }

            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Setup Details Row
            AnimatedVisibility(
                visible = isEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!settings.isPasscodeEnabled) {
                        // PIN Fields side-by-side
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
                                label = { Text("تأكيد الرمز") },
                                placeholder = { Text("٤ أرقام") },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            )

                            OutlinedTextField(
                                value = passcode,
                                onValueChange = {
                                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                        passcode = it
                                    }
                                },
                                label = { Text("رمز القفل") },
                                placeholder = { Text("مثال: ١٢٣٤") },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            )
                        }

                        // Recovery inputs
                        OutlinedTextField(
                            value = recoveryPhrase,
                            onValueChange = { recoveryPhrase = it },
                            label = { Text("مفتاح أمان الاسترداد") },
                            placeholder = { Text("مثال: فاطمة أو أحمد") },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Right,
                                fontSize = 13.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = recoveryHint,
                            onValueChange = { recoveryHint = it },
                            label = { Text("تلميح الذاكرة") },
                            placeholder = { Text("مثال: اسم الزوجة أو صديق الطفولة") },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Right,
                                fontSize = 13.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Checkbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checkAcknowledged = !checkAcknowledged }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "أؤكد حفظي الآمن لمفتاح الاسترداد والرمز السري خارج الهاتف وأتحمل المسؤولية كاملة",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Right,
                                modifier = Modifier.weight(1f).padding(end = 6.dp)
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
                                      checkAcknowledged

                        Button(
                            onClick = {
                                if (isValid) {
                                    val pHash = com.example.domain.HashUtils.hashString(passcode)
                                    val rHash = com.example.domain.HashUtils.hashString(recoveryPhrase.trim())
                                    val updated = settings.copy(
                                        isPasscodeEnabled = true,
                                        passcodeHash = pHash,
                                        recoveryPhraseHash = rHash,
                                        recoveryHint = recoveryHint.trim().takeIf { it.isNotBlank() }
                                    )
                                    viewModel.saveSettings(updated)
                                    Toast.makeText(context, "تم تفعيل قفل التطبيق والأمان بنجاح 🛡️", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            enabled = isValid,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text("تفعيل وحفظ رمز القفل 💾", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "تنبيه الخصوصية والنسخ الاحتياطي",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE11D48),
                                        textAlign = TextAlign.Right
                                    )
                                }
                                
                                Text(
                                    text = "التطبيق يحمي خصوصيتك محلياً بالكامل بترميز مشفر. عبارة الاسترداد الفائقة هي طريقتك الوحيدة للوصول للبيانات حال نسيان رمز القفل.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFBE123C),
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Right
                                )

                                if (!settings.recoveryHint.isNullOrBlank()) {
                                    Text(
                                        text = "تلميح الذاكرة الحالي: ${settings.recoveryHint}",
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
                                            clipboardManager.setText(AnnotatedString("عبارة أمان التطبيق للفقد والطوارئ"))
                                            Toast.makeText(context, "تم نسخ عبارة الأمان الأساسية للحافظة 📋", Toast.LENGTH_SHORT).show()
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
                                        text = "نسخ عبارة الأمان",
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
