package com.example.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.EmeraldPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasscodeSetupBottomSheet(
    onDismiss: () -> Unit,
    onSavePasscode: (passcode: String, recoveryPhrase: String, recoveryHint: String) -> Unit
) {
    var tempPasscode by remember { mutableStateOf("") }
    var tempConfirmPasscode by remember { mutableStateOf("") }
    var tempRecoveryPhrase by remember { mutableStateOf("") }
    var tempRecoveryHint by remember { mutableStateOf("") }
    var tempCheckAcknowledged by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.settings_setup_passcode_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_setup_passcode_subtitle),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Passcode Outlined Text Field
                    OutlinedTextField(
                        value = tempPasscode,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                tempPasscode = it
                            }
                        },
                        label = { Text(stringResource(R.string.settings_passcode_field)) },
                        placeholder = { Text(stringResource(R.string.settings_passcode_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Confirm Passcode Outlined Text Field
                    OutlinedTextField(
                        value = tempConfirmPasscode,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                tempConfirmPasscode = it
                            }
                        },
                        label = { Text(stringResource(R.string.settings_confirm_passcode_field)) },
                        placeholder = { Text(stringResource(R.string.settings_confirm_passcode_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Warnings Box (صندوق تحذيري جذاب بلون متباين دافئ)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_security_warning),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 16.sp
                        )
                    }

                    // Recovery Phrase Outlined Text Field
                    OutlinedTextField(
                        value = tempRecoveryPhrase,
                        onValueChange = { tempRecoveryPhrase = it },
                        label = { Text(stringResource(R.string.settings_recovery_phrase_field)) },
                        placeholder = { Text(stringResource(R.string.settings_recovery_phrase_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    Text(
                        text = stringResource(R.string.settings_save_phrase_tip),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Recovery Hint Outlined Text Field
                    OutlinedTextField(
                        value = tempRecoveryHint,
                        onValueChange = { tempRecoveryHint = it },
                        label = { Text(stringResource(R.string.settings_recovery_hint_field)) },
                        placeholder = { Text(stringResource(R.string.settings_recovery_hint_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Acknowledge Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tempCheckAcknowledged = !tempCheckAcknowledged }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_acknowledge_checkbox),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Checkbox(
                            checked = tempCheckAcknowledged,
                            onCheckedChange = { tempCheckAcknowledged = it },
                            colors = CheckboxDefaults.colors(checkedColor = CoralAccent)
                        )
                    }
                }
            }

            item {
                val isValid = tempPasscode.length == 4 && 
                              tempConfirmPasscode == tempPasscode && 
                              tempRecoveryPhrase.isNotBlank() && 
                              tempCheckAcknowledged

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(stringResource(R.string.common_cancel), fontWeight = FontWeight.Bold)
                    }

                    // Save & Enable Passcode
                    Button(
                        onClick = {
                            if (isValid) {
                                onSavePasscode(tempPasscode, tempRecoveryPhrase, tempRecoveryHint)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = isValid
                    ) {
                        Text(stringResource(R.string.settings_save_passcode_btn), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
