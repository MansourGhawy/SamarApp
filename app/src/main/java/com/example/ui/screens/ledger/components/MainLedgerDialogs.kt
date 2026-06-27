package com.example.ui.screens.ledger.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entities.FixedCommitment
import com.example.ui.viewmodel.MonthLedger
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SoftRed
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeleteDaysConfirmDialog(
    showDeleteDaysDialog: Boolean,
    onDismiss: () -> Unit,
    monthlyLedger: List<MonthLedger>,
    selectedDayKeys: MutableList<String>,
    viewModel: FinanceViewModel,
    scope: CoroutineScope,
    context: Context,
    onSuccess: () -> Unit
) {
    if (showDeleteDaysDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(id = R.string.ledger_bulk_delete_days_title),
                    fontWeight = FontWeight.Bold,
                    color = SoftRed,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.ledger_bulk_delete_days_msg),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismiss()
                        scope.launch {
                            val txsToDelete = mutableListOf<String>()
                            monthlyLedger.forEach { ml ->
                                ml.days.forEach { day ->
                                    val dayKey = "${ml.monthKey}_${day.dayNumber}"
                                    if (selectedDayKeys.contains(dayKey)) {
                                        day.transactions.forEach { tx ->
                                            txsToDelete.add(tx.id)
                                        }
                                    }
                                }
                            }
                            viewModel.deleteTransactionsBulk(txsToDelete, context.getString(R.string.ledger_bulk_delete_days_desc))
                            onSuccess()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed)
                ) {
                    Text(stringResource(id = R.string.ledger_bulk_delete_days_confirm_btn), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.common_cancel), color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
fun ReorderCommitmentDialog(
    reorderCommitmentTarget: FixedCommitment?,
    commitmentsSize: Int,
    onDismiss: () -> Unit,
    onApplyReorder: (FixedCommitment, Int) -> Unit,
    context: Context
) {
    if (reorderCommitmentTarget != null) {
        var targetPositionStr by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(id = R.string.ledger_reorder_target_title, reorderCommitmentTarget.name),
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(stringResource(id = R.string.ledger_reorder_position_label, commitmentsSize), fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    LaunchedEffect(focusRequester) {
                        delay(500)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    OutlinedTextField(
                        value = targetPositionStr,
                        onValueChange = { 
                            targetPositionStr = it
                            errorMsg = ""
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        isError = errorMsg.isNotEmpty()
                    )
                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = SoftRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pos = targetPositionStr.toIntOrNull()
                        if (pos == null || pos < 1 || pos > commitmentsSize) {
                            errorMsg = context.getString(R.string.ledger_reorder_position_error)
                        } else {
                            onApplyReorder(reorderCommitmentTarget, pos)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(stringResource(id = R.string.ledger_reorder_apply), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.common_cancel), color = Color.Gray)
                }
            }
        )
    }
}
