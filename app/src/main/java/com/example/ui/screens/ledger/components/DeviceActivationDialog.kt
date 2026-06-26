package com.example.ui.screens.ledger.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftRed
import com.example.ui.viewmodel.FinanceViewModel

@Composable
fun DeviceActivationDialog(
    deviceId: String,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    var activationCodeInput by remember { mutableStateOf("") }
    var isCodeError by remember { mutableStateOf(false) }

    val activationFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        activationFocusRequester.requestFocus()
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White),
            shadowElevation = 8.dp,
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header lock icon with modern glow
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF2F2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(id = R.string.licensing_dialog_title),
                        tint = SoftRed,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = R.string.licensing_dialog_title),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(id = R.string.licensing_dialog_desc),
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Device ID Display
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText(context.getString(R.string.licensing_device_id_label), deviceId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.toast_license_copied), Toast.LENGTH_SHORT).show()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(id = R.string.licensing_copy_desc),
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(id = R.string.licensing_device_id_label),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = deviceId,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Color(0xFF334155),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Soft Green WhatsApp Button
                Button(
                    onClick = {
                        val msg = context.getString(R.string.licensing_whatsapp_msg_template, deviceId)
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://api.whatsapp.com/send?phone=967774004399&text=" + android.net.Uri.encode(msg))
                        )
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.ledger_whatsapp_not_found), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.licensing_contact_dev),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Input for Activation Code
                OutlinedTextField(
                    value = activationCodeInput,
                    onValueChange = { 
                        activationCodeInput = it
                        isCodeError = false
                    },
                    label = { Text(stringResource(id = R.string.licensing_enter_code_label), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(activationFocusRequester),
                    isError = isCodeError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Color.LightGray,
                        errorBorderColor = SoftRed
                    ),
                    placeholder = { Text(stringResource(id = R.string.licensing_code_placeholder), color = Color.LightGray) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                if (isCodeError) {
                    Text(
                        text = stringResource(id = R.string.licensing_invalid_code_error),
                        color = SoftRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Submit & Cancel Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(id = R.string.licensing_browse_offline), color = Color.Gray, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val cleanInput = activationCodeInput.trim().uppercase()
                            val success = viewModel.activateLicense(cleanInput)
                            if (success) {
                                val isPermanentCode = cleanInput.startsWith("ACT-P-")
                                val toastMsg = if (isPermanentCode) {
                                    context.getString(R.string.licensing_permanent_success)
                                } else {
                                    context.getString(R.string.licensing_temporary_success)
                                }
                                Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                                onDismiss()
                                isCodeError = false
                            } else {
                                isCodeError = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text(stringResource(id = R.string.licensing_confirm_btn), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
