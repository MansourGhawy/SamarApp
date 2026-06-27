package com.example.ui.screens.ledger.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.FixedCommitment
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftRed
import kotlinx.coroutines.delay

@Composable
fun CommitmentEditDialog(
    editingCommitment: FixedCommitment?,
    onDismiss: () -> Unit,
    onSaveCommitment: (name: String, target: Double, progress: Double) -> Unit,
    onDeleteCommitment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nameFocus = remember { FocusRequester() }
    val targetFocus = remember { FocusRequester() }
    val progressFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val initialName = editingCommitment?.name ?: ""
    val initialTarget = editingCommitment?.targetAmount?.let { if (it > 0) it.toInt().toString() else "" } ?: ""
    val initialProgress = editingCommitment?.currentProgress?.let { if (it > 0) it.toInt().toString() else "" } ?: ""

    var obligationName by rememberSaveable(editingCommitment) { mutableStateOf(initialName) }
    var targetAmtStr by rememberSaveable(editingCommitment) { mutableStateOf(initialTarget) }
    var progressAmtStr by rememberSaveable(editingCommitment) { mutableStateOf(initialProgress) }

    // التحكم في التركيز التلقائي للوحة المفاتيح
    LaunchedEffect(Unit) {
        delay(300)
        try {
            if (editingCommitment == null) {
                nameFocus.requestFocus()
            } else {
                targetFocus.requestFocus()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingCommitment != null) {
                    stringResource(id = R.string.ledger_edit_commitment_title)
                } else {
                    stringResource(id = R.string.ledger_add_commitment_title)
                },
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End
            ) {
                // اسم الالتزام (مُعطّل التعديل إذا كنا نعدّل التزاماً قائماً بالفعل)
                OutlinedTextField(
                    value = obligationName,
                    onValueChange = { if (editingCommitment == null) obligationName = it },
                    enabled = (editingCommitment == null),
                    label = { Text(stringResource(id = R.string.ledger_commitment_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { targetFocus.requestFocus() }),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // المبلغ المستهدف لتغطية هذا الالتزام
                OutlinedTextField(
                    value = targetAmtStr,
                    onValueChange = { targetAmtStr = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { progressFocus.requestFocus() }),
                    label = { Text(stringResource(id = R.string.ledger_commitment_target_amount_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(targetFocus),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // التقدم الحالي أو المبلغ المدخر له بالفعل
                OutlinedTextField(
                    value = progressAmtStr,
                    onValueChange = { progressAmtStr = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    label = { Text(stringResource(id = R.string.ledger_commitment_current_progress_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(progressFocus),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tar = targetAmtStr.toDoubleOrNull() ?: 0.0
                    val prg = progressAmtStr.toDoubleOrNull() ?: 0.0
                    if (obligationName.isNotBlank() && tar > 0) {
                        onSaveCommitment(obligationName, tar, prg)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(stringResource(id = R.string.ledger_save_commitment_btn))
            }
        },
        dismissButton = {
            Row {
                if (editingCommitment != null) {
                    TextButton(onClick = { onDeleteCommitment(editingCommitment.name) }) {
                        Text(stringResource(id = R.string.ledger_commitment_delete), color = SoftRed)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.common_cancel), color = Color.Gray)
                }
            }
        }
    )
}