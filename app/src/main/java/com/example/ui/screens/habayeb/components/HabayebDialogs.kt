package com.example.ui.screens.habayeb.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.HabayebCustomer
import com.example.ui.viewmodel.FinanceViewModel

@Composable
fun DeleteConfirmDialog(
    customerToDelete: HabayebCustomer?,
    selectedCustomerIds: List<String>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onSuccessBulkDelete: () -> Unit
) {
    val context = LocalContext.current
    val isSingleDelete = customerToDelete != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSingleDelete) stringResource(id = R.string.habayeb_delete_account_title) else stringResource(id = R.string.habayeb_bulk_delete_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                text = if (isSingleDelete) {
                    stringResource(id = R.string.habayeb_delete_account_confirm, customerToDelete?.name ?: "")
                } else {
                    stringResource(id = R.string.habayeb_bulk_delete_confirm, selectedCustomerIds.size)
                },
                fontSize = 13.sp,
                color = Color.Gray
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSingleDelete) {
                        customerToDelete?.let {
                            viewModel.deleteHabayebCustomer(it.id)
                            Toast.makeText(context, context.getString(R.string.habayeb_toast_delete_success), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        viewModel.deleteMultipleHabayebCustomers(selectedCustomerIds)
                        Toast.makeText(context, context.getString(R.string.habayeb_toast_delete_success), Toast.LENGTH_SHORT).show()
                        onSuccessBulkDelete()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(stringResource(id = R.string.habayeb_delete_yes), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
            }
        }
    )
}

@Composable
fun EditCustomerDialog(
    customer: HabayebCustomer,
    viewModel: FinanceViewModel,
    activeThemeColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editedNameStr by remember(customer.name) { mutableStateOf(customer.name) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(id = R.string.habayeb_edit_name_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = editedNameStr,
                    onValueChange = { editedNameStr = it },
                    label = { Text(stringResource(id = R.string.habayeb_account_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeThemeColor,
                        focusedLabelColor = activeThemeColor,
                        cursorColor = activeThemeColor
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editedNameStr.isNotBlank()) {
                        viewModel.updateHabayebCustomerName(customer.id, editedNameStr.trim())
                        Toast.makeText(context, context.getString(R.string.habayeb_toast_update_success), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor)
            ) {
                Text(stringResource(id = R.string.habayeb_save_edit), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.habayeb_cancel), color = Color.Gray)
            }
        }
    )
}
